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
            // Incoming updates trigger the reply worker.
        }
    };

    private TelegaAutoReplyAgent() {
    }

    /** Registers incoming-message and send-ack listeners for one logged-in account. */
    public static void ensureInitialized(final int account) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!OBSERVERS.containsKey(account)) {
                NotificationCenter.NotificationCenterDelegate observer =
                        (id, currentAccount, args) -> {
                            if (currentAccount != account) return;
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
            }

            ForegroundDetector detector = ForegroundDetector.getInstance();
            if (detector != null && !listenerRegistered) {
                detector.addListener(FOREGROUND_LISTENER);
                listenerRegistered = true;
            }
            // Recover pending work if Android restarted the app while away.
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
        return isBackground() || !isForegroundNow();
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
        // Positive dialog IDs are private user chats. Never auto-reply in groups/channels.
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

            // The callback is already a new-message event. Telegram may clear the unread flag
            // during multi-device sync or catch-up; don't silently skip an incoming message.
            if (!canAutoReplyNow()) continue;
            String body = message.messageOwner.message;
            if (TextUtils.isEmpty(body)) continue;

            if (TelegaSettings.isOfflineAutoReplyEnabled()) {
                startOfflineReplyWorker(account, dialogId);
                continue;
            }
            if (!isConfigured()) continue;
            appendRecord(account, dialogId, message.getId(), message.messageOwner.date, body, false, false);
            startReplyWorker(account, dialogId);
        }
    }

    private static void handleSentMessageAck(int account, Object[] args) {
        // Telegram posts: old/local id, server id, sent TLRPC.Message, dialog id, ...
        if (args == null || args.length < 4 || !(args[1] instanceof Number)
                || !(args[2] instanceof TLRPC.Message) || !(args[3] instanceof Number)) return;
        TLRPC.Message message = (TLRPC.Message) args[2];
        long dialogId = ((Number) args[3]).longValue();
        if (dialogId <= 0 || message.message == null) return;
        int serverId = ((Number) args[1]).intValue();
        boolean tagged = message.params != null
                && ("1".equals(message.params.get("telega_ai_auto_reply"))
                || "1".equals(message.params.get("telega_auto_reply")));

        // Match placeholders only against the exact text previously generated by this agent.
        JSONArray saved = readConversation(account, dialogId);
        boolean matchingPlaceholder = false;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject record = saved.optJSONObject(i);
            if (record != null && record.optBoolean("out", false)
                    && record.optInt("id", 0) == 0
                    && message.message.equals(record.optString("text", ""))) {
                matchingPlaceholder = true;
                break;
            }
        }
        if (tagged || matchingPlaceholder) {
            appendRecord(account, dialogId, serverId, message.date, message.message, true, true);
        }
    }

    private static boolean isAutoReplyMessage(MessageObject message) {
        try {
            if (message.messageOwner.params != null
                    && ("1".equals(message.messageOwner.params.get("telega_ai_auto_reply"))
                    || "1".equals(message.messageOwner.params.get("telega_auto_reply")))) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        String text = message.messageOwner.message;
        return text != null && (text.startsWith("Привет, я ИИ-агент")
                || text.startsWith("Кратко, пока меня не было:"));
    }

    private static JSONArray readConversation(int account, long dialogId) {
        SharedPreferences p = prefs();
        if (p == null) return new JSONArray();
        try {
            return new JSONArray(p.getString(conversationKey(account, dialogId), "[]"));
        } catch (Throwable ignore) {
            return new JSONArray();
        }
    }

    private static void writeConversation(int account, long dialogId, JSONArray records) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putString(conversationKey(account, dialogId), records.toString()).apply();
    }

    private static void appendRecord(int account, long dialogId, int id, int date,
                                     String body, boolean out, boolean replied) {
        if (TextUtils.isEmpty(body)) return;
        synchronized (LOCK) {
            JSONArray old = readConversation(account, dialogId);
            JSONArray next = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) continue;
                int oldId = item.optInt("id", 0);
                boolean oldOut = item.optBoolean("out", false);
                String oldText = item.optString("text", "");

                if (out && id != 0 && oldOut && oldId == 0 && body.equals(oldText)) {
                    try {
                        item.put("id", id);
                        item.put("date", date);
                    } catch (Exception ignore) {
                    }
                    replaced = true;
                } else if (id != 0 && oldId == id && oldOut == out) {
                    replaced = true;
                } else if (out && id == 0 && oldOut && body.equals(oldText)) {
                    replaced = true;
                }
                next.put(item);
            }

            if (!replaced) {
                JSONObject item = new JSONObject();
                try {
                    item.put("id", id);
                    item.put("date", date);
                    item.put("text", body);
                    item.put("out", out);
                    item.put("replied", replied);
                } catch (Exception ignore) {
                }
                next.put(item);
            }

            JSONArray bounded = new JSONArray();
            int from = Math.max(0, next.length() - MAX_RECORDS);
            for (int i = from; i < next.length(); i++) bounded.put(next.optJSONObject(i));
            writeConversation(account, dialogId, bounded);
        }
    }

    private static ArrayList<Integer> unansweredIds(JSONArray records) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null || item.optBoolean("out", false) || item.optBoolean("replied", false)) continue;
            int id = item.optInt("id", 0);
            if (id != 0) result.add(id);
        }
        return result;
    }

    private static boolean hasUnanswered(int account, long dialogId) {
        return !unansweredIds(readConversation(account, dialogId)).isEmpty();
    }

    private static void markReplied(int account, long dialogId, Set<Integer> messageIds) {
        synchronized (LOCK) {
            JSONArray old = readConversation(account, dialogId);
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null || item.optBoolean("out", false)) continue;
                if (messageIds.contains(item.optInt("id", 0))) {
                    try {
                        item.put("replied", true);
                    } catch (Exception ignore) {
                    }
                }
            }
            writeConversation(account, dialogId, old);
        }
    }

    private static String offlineReplyCooldownKey(int account, long dialogId) {
        return "fixed_reply_last_sent_" + account + "_" + dialogId;
    }

    private static void startOfflineReplyWorker(final int account, final long dialogId) {
        if (!canAutoReplyNow() || !TelegaSettings.isOfflineAutoReplyEnabled()) return;
        final String workerKey = conversationKey(account, dialogId) + "_fixed";
        if (!REPLYING.add(workerKey)) return;

        new Thread(() -> {
            try {
                SharedPreferences saved = prefs();
                if (saved == null) return;
                String cooldownKey = offlineReplyCooldownKey(account, dialogId);
                long lastSent = saved.getLong(cooldownKey, 0L);
                if (lastSent > 0 && System.currentTimeMillis() - lastSent < OFFLINE_REPLY_COOLDOWN_MS) return;

                final String reply = TelegaSettings.getOfflineAutoReplyMessage().trim();
                if (reply.isEmpty()) return;

                CountDownLatch latch = new CountDownLatch(1);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        if (!canAutoReplyNow() || !TelegaSettings.isOfflineAutoReplyEnabled()) return;
                        SharedPreferences latest = prefs();
                        if (latest == null) return;
                        long sentAt = System.currentTimeMillis();
                        long previous = latest.getLong(cooldownKey, 0L);
                        if (previous > 0 && sentAt - previous < OFFLINE_REPLY_COOLDOWN_MS) return;

                        String currentReply = TelegaSettings.getOfflineAutoReplyMessage().trim();
                        if (currentReply.isEmpty()) return;
                        SendMessagesHelper.SendMessageParams params =
                                SendMessagesHelper.SendMessageParams.of(currentReply, dialogId);
                        if (params.params == null) params.params = new HashMap<>();
                        params.params.put("telega_auto_reply", "1");
                        SendMessagesHelper.getInstance(account).sendMessage(params);
                        latest.edit().putLong(cooldownKey, sentAt).apply();
                    } catch (Throwable error) {
                        FileLog.e(error);
                    } finally {
                        latch.countDown();
                    }
                });
                latch.await(12, TimeUnit.SECONDS);
            } catch (Throwable error) {
                FileLog.e(error);
            } finally {
                REPLYING.remove(workerKey);
            }
        }, "TelegaFixedAutoReply").start();
    }

    private static void startReplyWorker(final int account, final long dialogId) {
        if (!canAutoReplyNow() || !isConfigured()) return;
        final String key = conversationKey(account, dialogId);
        if (!REPLYING.add(key)) return;

        new Thread(() -> {
            final boolean[] failed = {false};
            try {
                int rounds = 0;
                while (rounds++ < 8 && canAutoReplyNow() && isConfigured()) {
                    JSONArray records = readConversation(account, dialogId);
                    ArrayList<Integer> ids = unansweredIds(records);
                    if (ids.isEmpty()) break;

                    String systemPrompt = TelegaSettings.getAiAutoReplyPrompt().trim()
                            + "\n\nТы автоматический ИИ-автоответчик, а не человек. Отвечай по заданному владельцем промту, "
                            + "кратко и уважительно. Не утверждай, что являешься владельцем аккаунта. Не проси пароли, коды "
                            + "входа, платёжные данные или секреты. Если вопрос неясен, уточни его. Не выдумывай факты "
                            + "и не обещай действий, которые не можешь совершить.";
                    String context = buildReplyContext(account, dialogId, records, ids);
                    String answer = requestReplyWithRetry(dialogId, systemPrompt, context);
                    if (TextUtils.isEmpty(answer)) {
                        failed[0] = true;
                        break;
                    }
                    if (!canAutoReplyNow() || !isConfigured()) break;

                    final String response = answer.trim();
                    final Set<Integer> handled = new HashSet<>(ids);
                    CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] sent = {false};
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (canAutoReplyNow() && isConfigured()) {
                                SharedPreferences p = prefs();
                                boolean needsIntro = p == null || !p.getBoolean(introducedKey(account, dialogId), false);
                                String outgoing = (needsIntro ? disclosure(account) + "\n\n" : "") + response;
                                if (outgoing.length() > 3900) outgoing = outgoing.substring(0, 3899) + "…";
                                SendMessagesHelper.SendMessageParams params =
                                        SendMessagesHelper.SendMessageParams.of(outgoing, dialogId);
                                if (params.params == null) params.params = new HashMap<>();
                                params.params.put("telega_ai_auto_reply", "1");
                                SendMessagesHelper.getInstance(account).sendMessage(params);
                                appendRecord(account, dialogId, 0,
                                        (int) (System.currentTimeMillis() / 1000L), outgoing, true, true);
                                markReplied(account, dialogId, handled);
                                SharedPreferences saved = prefs();
                                if (saved != null) saved.edit().putBoolean(introducedKey(account, dialogId), true).apply();
                                sent[0] = true;
                            }
                        } catch (Throwable t) {
                            FileLog.e(t);
                        } finally {
                            latch.countDown();
                        }
                    });
                    if (!latch.await(12, TimeUnit.SECONDS) || !sent[0]) {
                        if (canAutoReplyNow() && isConfigured()) failed[0] = true;
                        break;
                    }
                }
            } catch (Throwable t) {
                failed[0] = true;
                FileLog.e(t);
            } finally {
                REPLYING.remove(key);
                // Retry only after successful processing, never in a tight loop after API failure.
                if (!failed[0] && canAutoReplyNow() && isConfigured() && hasUnanswered(account, dialogId)) {
                    startReplyWorker(account, dialogId);
                }
            }
        }, "TelegaAutoReply").start();
    }

    /**
     * Retry transient provider/network failures, but keep the retry count bounded so
     * a broken endpoint cannot create a background request loop.
     */
    private static String requestReplyWithRetry(long dialogId, String systemPrompt, String context) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!canAutoReplyNow() || !isConfigured()) return null;
            try {
                String answer = TelegaAIAgent.completeWithSystemPrompt(
                        TelegaSettings.getAiBaseUrl(), TelegaSettings.getAiApiKey(),
                        TelegaSettings.getAiModel(), systemPrompt, context);
                if (!TextUtils.isEmpty(answer)) return answer.trim();
                FileLog.e(new IllegalStateException(
                        "AI auto-reply returned an empty response for dialog " + dialogId));
            } catch (Throwable error) {
                FileLog.e(error);
            }
            if (attempt < 2 && canAutoReplyNow() && isConfigured()) {
                try {
                    Thread.sleep((attempt + 1) * 1500L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private static String buildReplyContext(int account, long dialogId, JSONArray records,
                                            ArrayList<Integer> unanswered) {
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        TLRPC.User contact = MessagesController.getInstance(account).getUser(dialogId);
        String selfName = userName(self);
        String contactName = userName(contact);
        String contactHandle = contact != null && !TextUtils.isEmpty(contact.username)
                ? "@" + contact.username : "нет username";
        StringBuilder builder = new StringBuilder();
        builder.append("Данные аккаунта: ").append(selfName);
        if (self != null && !TextUtils.isEmpty(self.username)) builder.append(" (@").append(self.username).append(")");
        builder.append("\nСобеседник: ").append(contactName).append(" (").append(contactHandle)
                .append("), ID диалога ").append(dialogId).append(".");
        builder.append("\nТекущее локальное время: ").append(formatTime((int) (System.currentTimeMillis() / 1000L)));
        builder.append("\n\nКонтекст переписки (в хронологическом порядке):\n");

        int from = Math.max(0, records.length() - 24);
        for (int i = from; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) continue;
            String prefix = item.optBoolean("out", false) ? "Предыдущий ответ ИИ" : "Собеседник";
            builder.append("[").append(formatTime(item.optInt("date", 0))).append("] ")
                    .append(prefix).append(": ").append(item.optString("text", "")).append("\n");
        }
        builder.append("\nНовые сообщения, на которые требуется ответ (ID ")
                .append(unanswered.toString()).append("):\n");
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null || item.optBoolean("out", false) || item.optBoolean("replied", false)
                    || !unanswered.contains(item.optInt("id", 0))) continue;
            builder.append("[").append(formatTime(item.optInt("date", 0))).append("] ")
                    .append(item.optString("text", "")).append("\n");
        }
        builder.append("\nСформируй один подходящий ответ собеседнику. Верни только текст ответа, без префикса и кавычек.");
        return builder.toString();
    }

    private static String disclosure(int account) {
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        String handle = self != null && !TextUtils.isEmpty(self.username) ? "@" + self.username : userName(self);
        return "Привет, я ИИ-агент " + handle
                + ". Пользователь сейчас не в сети — можешь пообщаться со мной, а я передам ему информацию.";
    }

    private static String userName(TLRPC.User user) {
        if (user == null) return "пользователь";
        String result = ((user.first_name == null) ? "" : user.first_name)
                + ((user.last_name == null || user.last_name.isEmpty()) ? "" : " " + user.last_name);
        return result.trim().isEmpty() ? "пользователь" : result.trim();
    }

    private static String formatTime(int seconds) {
        if (seconds <= 0) seconds = (int) (System.currentTimeMillis() / 1000L);
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(seconds * 1000L));
    }

    private static void summarizePending(int account) {
        SharedPreferences p = prefs();
        if (p == null || !TelegaSettings.isAiConnected()) return;
        Map<String, ?> all = p.getAll();
        for (String key : all.keySet()) {
            if (!key.startsWith(CONVERSATION_PREFIX + account + "_")) continue;
            int split = key.lastIndexOf('_');
            if (split < 0 || split + 1 >= key.length()) continue;
            long dialogId;
            try {
                dialogId = Long.parseLong(key.substring(split + 1));
            } catch (Exception ignore) {
                continue;
            }
            if (dialogId > 0) startSummaryWorker(account, dialogId);
        }
    }

    private static void startSummaryWorker(final int account, final long dialogId) {
        if (!isForegroundNow() || !TelegaSettings.isAiConnected()) return;
        final String key = conversationKey(account, dialogId);
        JSONArray records = readConversation(account, dialogId);
        boolean hasIncoming = false;
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item != null && !item.optBoolean("out", false)) {
                hasIncoming = true;
                break;
            }
        }
        if (!hasIncoming || !SUMMARIZING.add(key)) return;

        new Thread(() -> {
            boolean uiCallbackPosted = false;
            try {
                JSONArray snapshot = readConversation(account, dialogId);
                String systemPrompt = "Ты помощник владельца аккаунта. Составь краткую, точную сводку переписки, "
                        + "которая произошла, пока владелец был не в сети. Выдели кто и что хотел, вопросы, договорённости "
                        + "и то, что требует ответа. Не выдумывай деталей. 2-5 коротких предложений на русском.";
                String request = "Собеседник: "
                        + userName(MessagesController.getInstance(account).getUser(dialogId))
                        + "\nПереписка, пока владелец отсутствовал:\n" + formatTranscript(snapshot)
                        + "\nПодготовь краткую сводку для владельца.";
                String summary = TelegaAIAgent.completeWithSystemPrompt(
                        TelegaSettings.getAiBaseUrl(), TelegaSettings.getAiApiKey(),
                        TelegaSettings.getAiModel(), systemPrompt, request);
                if (TextUtils.isEmpty(summary)) return;

                final String finalSummary = summary.trim();
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        if (!isForegroundNow() || !TelegaSettings.isAiConnected()) return;
                        // Only delete captured unread incoming messages and this agent's replies;
                        // revoke=false means do not delete messages for the other participant.
                        ArrayList<Integer> ids = new ArrayList<>();
                        JSONArray current = readConversation(account, dialogId);
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
                                ? finalSummary.substring(0, 3499) + "…" : finalSummary;
                        SendMessagesHelper.SendMessageParams params =
                                SendMessagesHelper.SendMessageParams.of("Кратко, пока меня не было:\n" + cleanSummary, dialogId);
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
