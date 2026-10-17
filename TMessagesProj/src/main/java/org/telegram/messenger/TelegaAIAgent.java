package org.telegram.messenger;

import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Telega AI agent. Works with any OpenAI-compatible /chat/completions endpoint.
 *
 * Chat commands (any chat, groups included), only when the agent is connected and switched on:
 *   .ai           -> shows that the agent is active (nothing is sent to the chat)
 *   .ai question  -> asks the model, the answer is sent to the chat
 */
public final class TelegaAIAgent {

    public static final String COMMAND = ".ai";
    private static final int MAX_MESSAGE = 4000;

    private TelegaAIAgent() {
    }

    /** Called at the very start of SendMessagesHelper.sendMessage(SendMessageParams). @return true if consumed. */
    public static boolean intercept(int account, SendMessagesHelper.SendMessageParams params) {
        try {
            if (params == null || params.message == null) {
                return false;
            }
            if (!TelegaSettings.isAiConnected() || !TelegaSettings.isAiEnabled()) {
                return false;
            }
            String text = String.valueOf(params.message).trim();
            String lower = text.toLowerCase(Locale.ROOT);
            boolean isCommand = lower.equals(COMMAND)
                    || lower.startsWith(COMMAND + " ")
                    || lower.startsWith(COMMAND + "\n");
            if (!isCommand) {
                return false;
            }
            String question = text.substring(COMMAND.length()).trim();
            if (question.isEmpty()) {
                toast("\uD83E\uDD16 ИИ агент включён. Напиши: .ai твой вопрос");
                return true;
            }
            ask(account, params, question);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private static void ask(final int account, final SendMessagesHelper.SendMessageParams params, final String question) {
        toast("\uD83E\uDD16 ИИ думает…");
        final String base = TelegaSettings.getAiBaseUrl();
        final String key = TelegaSettings.getAiApiKey();
        final String model = TelegaSettings.getAiModel();
        new Thread(() -> {
            String answer = null;
            String error = null;
            try {
                answer = complete(base, key, model, question);
            } catch (Throwable t) {
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            }
            final String finalAnswer = answer;
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalAnswer == null) {
                    toast("\uD83E\uDD16 Ошибка ИИ: " + finalError);
                    return;
                }
                // Never starts with the command, so the hook cannot loop.
                String out = "\u2753 " + question + "\n\n\uD83E\uDD16 " + finalAnswer;
                if (out.length() > MAX_MESSAGE) {
                    out = out.substring(0, MAX_MESSAGE - 1) + "…";
                }
                params.message = out;
                params.entities = null;
                SendMessagesHelper.getInstance(account).sendMessage(params);
            });
        }, "TelegaAI").start();
    }

    // ------------------------------------------------------------------ HTTP

    static String endpoint(String base) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (!b.toLowerCase(Locale.ROOT).startsWith("http")) {
            b = "https://" + b;
        }
        if (!b.toLowerCase(Locale.ROOT).endsWith("/chat/completions")) {
            b = b + "/chat/completions";
        }
        return b;
    }

    public static String complete(String base, String key, String model, String question) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model.trim());
        JSONArray messages = new JSONArray();
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", "Ты ИИ агент в Telegram. Отвечай кратко и по делу, на языке вопроса, не длиннее 3000 символов, без Markdown-разметки.");
        messages.put(system);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", question);
        messages.put(user);
        body.put("messages", messages);

        HttpURLConnection c = (HttpURLConnection) new URL(endpoint(base)).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + key.trim());
            OutputStream os = c.getOutputStream();
            try {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            } finally {
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + shorten(resp));
            }
            JSONObject json = new JSONObject(resp);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new Exception("пустой ответ: " + shorten(resp));
            }
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            String content = msg != null ? msg.optString("content", "") : "";
            if (TextUtils.isEmpty(content)) {
                throw new Exception("пустой ответ");
            }
            return content.trim();
        } finally {
            c.disconnect();
        }
    }

    /** Test request used by the settings screen. @return null on success, error text otherwise. */
    public static String test(String base, String key, String model) {
        try {
            complete(base, key, model, "Ответь одним словом: ок");
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static void toast(final String text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
        });
    }
}
