package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.ForegroundDetector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Background-only responder for private user chats. It stores new unread incoming messages
 * locally, clearly discloses that replies are AI-generated, and sends a concise summary when
 * the app returns to foreground. Deletion is self-only (not "delete for everyone").
 */
public final class TelegaAutoReplyAgent {

    private static final String PREFS = "telega_auto_reply";
    private static final String CONVERSATION_PREFIX = "conversation_";
    private static final int MAX_RECORDS = 80;
    private static final long OFFLINE_REPLY_COOLDOWN_MS = 10L * 60L * 1000L;
    private static final Object LOCK = new Object();

    private static final Map<Integer, NotificationCenter.NotificationCenterDelegate> OBSERVERS =
            new ConcurrentHashMap<>();
    private static final Set<String> REPLYING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> SUMMARIZING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile boolean listenerRegistered;

    private static final ForegroundDetector.Listener FOREGROUND_LISTENER = new ForegroundDetector.Listener() {
        @Override
        public void onBecameForeground() {
            for (Integer account : OBSERVERS.keySet()) {
                summarizePending(account);
            }
        }

        @Override
        public void onBecameBackground() {
            for (Integer account : OBSERVERS.keySet()) {
                flushPendingReplies(account);
            }
        }
    };

    private TelegaAutoReplyAgent() {
    }

    /** Registers incoming-message and send-ack listeners for one logged-in account. */
    public static void ensureInitialized(final int account) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!UserConfig.getInstance(account).isClientActivated()) return;
            if (OBSERVERS.containsKey(account)) return;

            NotificationCenter.NotificationCenterDelegate observer =
                    (id, account1, args) -> {
                        if (id == NotificationCenter.didReceiveNewMessages) {
                            handleNewMessages(account, args);
                        } else if (id == NotificationCenter.messageReceivedByServer2) {
                            handleSentMessageAck(account, args);
                        }
                    };
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.addObserver(observer, NotificationCenter.didReceiveNewMessages);
            center.addObserver(observer, NotificationCenter.messageReceivedByServer2);
            OBSERVERS.put(account, observer);

            ForegroundDetector detector = ForegroundDetector.getInstance();
            if (detector != null && !listenerRegistered) {
                detector.addListener(FOREGROUND_LISTENER);
                listenerRegistered = true;
            }
            if (detector != null && detector.isForeground()) {
                summarizePending(account);
            }
        });
    }

    private static SharedPreferences prefs() {
        if (ApplicationLoader.applicationContext == null) return null;
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isBackground() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        if (detector == null) return true;
        return detector.isBackground() || !detector.isForeground();
    }

    private static boolean isForegroundNow() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        return detector != null && detector.isForeground();
    }

    private static boolean canAutoReplyNow() {
        if (!isForegroundNow()) {
            return true;
        }
        return isBackground();
    }

    private static boolean isConfigured() {
        return TelegaSettings.isAiConnected()
                && TelegaSettings.isAiAutoReplyEnabled()
                && !TextUtils.isEmpty(TelegaSettings.getAiAutoReplyPrompt().trim());
    }

    private static String conversationKey(int account, long dialogId) {
        return CONVERSATION_PREFIX + account + "_" + dialogId;
    }

    private static String introducedKey(int account, long dialogId) {
        return "introduced_" + account + "_" + dialogId;
    }

    private static void handleNewMessages(int account, Object[] args) {
        if (args == null || args.length < 2 || !(args[0] instanceof Number)
                || !(args[1] instanceof List)) return;

        long dialogId = ((Number) args[0]).longValue();
        if (dialogId <= 0) return;

        for (Object value : (List<?>) args[1]) {
            if (!(value instanceof MessageObject)) continue;
            MessageObject message = (MessageObject) value;
            if (message.messageOwner == null || message.getDialogId() != dialogId) continue;

            if (message.messageOwner.out) {
                if (isAutoReplyMessage(message)) {
                    appendRecord(account, dialogId, message.getId(), message.messageOwner.date,
                            message.messageOwner.message, true, true);
                }
                continue;
            }

            String body = message.messageOwner.message;
            if (TextUtils.isEmpty(body)) continue;

            if (TelegaSettings.isOfflineAutoReplyEnabled()) {
                appendRecord(account, dialogId, message.getId(), message.messageOwner.date, body, false, false);
                if (canAutoReplyNow()) {
                    startOfflineReplyWorker(account, dialogId);
                }
                continue;
            }
            if (!isConfigured()) continue;
            appendRecord(account, dialogId, message.getId(), message.messageOwner.date, body, false, false);
            if (canAutoReplyNow()) {
                startReplyWorker(account, dialogId);
            }
        }
    }

    private static void handleSentMessageAck(int account, Object[] args) {
        if (args == null || args.length < 4 || !(args[1] instanceof Number)
                || !(args[2] instanceof TLRPC.Message) || !(args[3] instanceof Number)) return;
        TLRPC.Message message = (TLRPC.Message) args[2];
        long dialogId = ((Number) args[3]).longValue();
        if (dialogId <= 0 || message.message == null) return;
        int serverId = ((Number) args[1]).intValue();
        boolean tagged = message.params != null
                && ("1".equals(message.params.get("telega_ai_auto_reply"))
                || "1".equals(message.params.get("telega_auto_reply")));
        if (!tagged && !isAutoReplyText(message.message)) return;
        appendRecord(account, dialogId, serverId, message.date, message.message, true, true);
    }

    private static boolean isAutoReplyMessage(MessageObject message) {
        if (message == null || message.messageOwner == null) return false;
        if (message.messageOwner.params != null
                && ("1".equals(message.messageOwner.params.get("telega_ai_auto_reply"))
                || "1".equals(message.messageOwner.params.get("telega_auto_reply")))) {
            return true;
        }
        return isAutoReplyText(message.messageOwner.message);
    }

    private static boolean isAutoReplyText(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String t = text.trim();
        return t.startsWith("\uD83E\uDD16") || t.contains("Telega AI") || t.contains("автоответ");
    }

    private static void appendRecord(int account, long dialogId, int messageId, int date,
                                     String text, boolean out, boolean autoReply) {
        if (TextUtils.isEmpty(text)) return;
        SharedPreferences p = prefs();
        if (p == null) return;
        synchronized (LOCK) {
            try {
                String key = conversationKey(account, dialogId);
                JSONArray arr = new JSONArray(p.getString(key, "[]"));
                JSONObject item = new JSONObject();
                item.put("id", messageId);
                item.put("date", date);
                item.put("text", text);
                item.put("out", out);
                item.put("auto", autoReply);
                arr.put(item);
                while (arr.length() > MAX_RECORDS) {
                    JSONArray next = new JSONArray();
                    for (int i = 1; i < arr.length(); i++) next.put(arr.get(i));
                    arr = next;
                }
                p.edit().putString(key, arr.toString()).apply();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private static void flushPendingReplies(int account) {
        if (!canAutoReplyNow()) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        String prefix = CONVERSATION_PREFIX + account + "_";
        for (String key : p.getAll().keySet()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            try {
                long dialogId = Long.parseLong(key.substring(prefix.length()));
                if (dialogId <= 0) {
                    continue;
                }
                if (TelegaSettings.isOfflineAutoReplyEnabled()) {
                    startOfflineReplyWorker(account, dialogId);
                } else if (isConfigured()) {
                    startReplyWorker(account, dialogId);
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static void startReplyWorker(final int account, final long dialogId) {
        final String key = conversationKey(account, dialogId);
        if (!REPLYING.add(key)) return;
        new Thread(() -> {
            try {
                if (!canAutoReplyNow() || !isConfigured()) return;
                processAiReplies(account, dialogId);
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                REPLYING.remove(key);
            }
        }, "TelegaAutoReply").start();
    }

    private static void startOfflineReplyWorker(final int account, final long dialogId) {
        final String key = "offline_" + conversationKey(account, dialogId);
        if (!REPLYING.add(key)) return;
        new Thread(() -> {
            try {
                if (!canAutoReplyNow() || !TelegaSettings.isOfflineAutoReplyEnabled()) return;
                String message = TelegaSettings.getOfflineAutoReplyMessage();
                if (TextUtils.isEmpty(message)) return;
                SharedPreferences p = prefs();
                String cooldownKey = "offline_cooldown_" + account + "_" + dialogId;
                long now = System.currentTimeMillis();
                if (p != null) {
                    long last = p.getLong(cooldownKey, 0L);
                    if (now - last < OFFLINE_REPLY_COOLDOWN_MS) return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        SendMessagesHelper.SendMessageParams params =
                                SendMessagesHelper.SendMessageParams.of(message, dialogId);
                        if (params.params == null) params.params = new HashMap<>();
                        params.params.put("telega_auto_reply", "1");
                        SendMessagesHelper.getInstance(account).sendMessage(params);
                        if (p != null) p.edit().putLong(cooldownKey, now).apply();
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                });
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                REPLYING.remove(key);
            }
        }, "TelegaOfflineReply").start();
    }

    private static void processAiReplies(int account, long dialogId) {
        // Minimal safe path: one AI reply per worker invocation if unanswered remain.
        if (!hasUnanswered(account, dialogId)) return;
        String systemPrompt = TelegaSettings.getAiAutoReplyPrompt();
        String context = formatTranscript(loadRecords(account, dialogId));
        String answer;
        try {
            answer = TelegaAIAgent.completeWithSystemPrompt(
                    TelegaSettings.getAiBaseUrl(), TelegaSettings.getAiApiKey(),
                    TelegaSettings.getAiModel(), systemPrompt, context);
        } catch (Throwable apiError) {
            FileLog.e(apiError);
            return;
        }
        if (TextUtils.isEmpty(answer)) {
            FileLog.e("TelegaAutoReply empty AI response");
            return;
        }
        final String text = "\uD83E\uDD16 Telega AI:\n" + answer.trim();
        final CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.SendMessageParams params =
                        SendMessagesHelper.SendMessageParams.of(text, dialogId);
                if (params.params == null) params.params = new HashMap<>();
                params.params.put("telega_ai_auto_reply", "1");
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static JSONArray loadRecords(int account, long dialogId) {
        SharedPreferences p = prefs();
        if (p == null) return new JSONArray();
        try {
            return new JSONArray(p.getString(conversationKey(account, dialogId), "[]"));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private static boolean hasUnanswered(int account, long dialogId) {
        JSONArray arr = loadRecords(account, dialogId);
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            if (item.optBoolean("out", false)) return false;
            return true;
        }
        return false;
    }

    private static void summarizePending(int account) {
        SharedPreferences p = prefs();
        if (p == null) return;
        String prefix = CONVERSATION_PREFIX + account + "_";
        for (String key : p.getAll().keySet()) {
            if (!key.startsWith(prefix)) continue;
            try {
                long dialogId = Long.parseLong(key.substring(prefix.length()));
                if (dialogId > 0) summarizeConversation(account, dialogId);
            } catch (Throwable ignore) {
            }
        }
    }

    private static void summarizeConversation(final int account, final long dialogId) {
        final String key = conversationKey(account, dialogId);
        if (!SUMMARIZING.add(key)) return;
        new Thread(() -> {
            boolean uiCallbackPosted = false;
            try {
                JSONArray records = loadRecords(account, dialogId);
                if (records.length() == 0) return;
                String transcript = formatTranscript(records);
                if (TextUtils.isEmpty(transcript.trim())) return;
                String summary = transcript;
                if (isConfigured()) {
                    try {
                        String ai = TelegaAIAgent.completeWithSystemPrompt(
                                TelegaSettings.getAiBaseUrl(), TelegaSettings.getAiApiKey(),
                                TelegaSettings.getAiModel(),
                                "Кратко суммируй непрочитанные сообщения для вернувшегося пользователя на русском.",
                                transcript);
                        if (!TextUtils.isEmpty(ai)) summary = ai.trim();
                    } catch (Throwable ignore) {
                    }
                }
                final String finalSummary = summary;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        ArrayList<Integer> ids = new ArrayList<>();
                        JSONArray current = loadRecords(account, dialogId);
                        for (int i = 0; i < current.length(); i++) {
                            JSONObject item = current.optJSONObject(i);
                            if (item == null) continue;
                            int id = item.optInt("id", 0);
                            if (id != 0 && !ids.contains(id)) ids.add(id);
                        }
                        if (!ids.isEmpty()) {
                            MessagesController.getInstance(account).deleteMessages(ids, null, null, dialogId, 0, false, 0);
                        }
                        String cleanSummary = finalSummary.length() > 3500
                                ? finalSummary.substring(0, 3499) + "\u2026" : finalSummary;
                        SendMessagesHelper.SendMessageParams params =
                                SendMessagesHelper.SendMessageParams.of(
                                        "Кратко, пока меня не было:\n" + cleanSummary, dialogId);
                        SendMessagesHelper.getInstance(account).sendMessage(params);
                        SharedPreferences saved = prefs();
                        if (saved != null) {
                            saved.edit().remove(key).remove(introducedKey(account, dialogId)).apply();
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    } finally {
                        SUMMARIZING.remove(key);
                    }
                });
                uiCallbackPosted = true;
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                if (!uiCallbackPosted) SUMMARIZING.remove(key);
            }
        }, "TelegaAutoSummary").start();
    }

    private static String formatTranscript(JSONArray records) {
        StringBuilder text = new StringBuilder();
        int from = Math.max(0, records.length() - 50);
        for (int i = from; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) continue;
            text.append(item.optBoolean("out", false) ? "ИИ ответил: " : "Собеседник: ")
                    .append(item.optString("text", "")).append("\n");
        }
        return text.toString();
    }
}
