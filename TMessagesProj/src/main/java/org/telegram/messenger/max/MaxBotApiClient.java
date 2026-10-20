package org.telegram.messenger.max;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Small client for the official MAX Bot API.
 *
 * This API operates as a bot, not as a personal MAX user account.
 * The caller must run all methods off the Android main thread.
 */
public final class MaxBotApiClient {
    private static final String API_BASE = "https://platform-api2.max.ru";
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 18000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private MaxBotApiClient() {
    }

    public static JSONObject getMe(String accessToken) throws IOException {
        return request(accessToken, "GET", "/me", null);
    }

    /**
     * Long polling for development and testing. MAX recommends Webhooks for production.
     * A null marker asks the server for the latest available update only.
     */
    public static JSONObject getUpdates(String accessToken, Long marker) throws IOException {
        StringBuilder path = new StringBuilder("/updates?limit=100&timeout=0");
        if (marker != null && marker >= 0) {
            path.append("&marker=").append(marker);
        }
        return request(accessToken, "GET", path.toString(), null);
    }

    /**
     * Retrieves chat history. MAX allows this only where the bot has sufficient access,
     * including administrator privileges for the relevant chat.
     */
    public static JSONObject getMessages(String accessToken, long chatId, int count) throws IOException {
        if (chatId == 0) {
            throw new IllegalArgumentException("Укажите ID чата");
        }
        int safeCount = Math.max(1, Math.min(100, count));
        return request(accessToken, "GET",
                "/messages?chat_id=" + chatId + "&count=" + safeCount, null);
    }

    public static JSONObject sendMessage(
            String accessToken, long targetId, boolean userTarget, String text) throws IOException {
        if (targetId == 0) {
            throw new IllegalArgumentException("Укажите ID пользователя или чата");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Введите текст сообщения");
        }
        if (text.length() > 4000) {
            throw new IllegalArgumentException("Сообщение MAX не должно превышать 4000 символов");
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("text", text);
            payload.put("notify", true);
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить сообщение", e);
        }

        String targetType = userTarget ? "user_id=" : "chat_id=";
        return request(accessToken, "POST",
                "/messages?" + targetType + targetId, payload);
    }

    private static JSONObject request(
            String accessToken, String method, String path, JSONObject payload) throws IOException {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Введите токен MAX-бота");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_BASE + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", accessToken.trim());
            connection.setRequestProperty("Accept", "application/json");

            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = input == null ? "" : readBounded(input);
            JSONObject response;
            try {
                response = responseText.trim().isEmpty()
                        ? new JSONObject() : new JSONObject(responseText);
            } catch (org.json.JSONException e) {
                if (status < 200 || status >= 300) {
                    throw new IOException("MAX API вернул HTTP " + status);
                }
                throw new IOException("MAX API вернул некорректный JSON", e);
            }

            if (status < 200 || status >= 300) {
                String message = response.optString("message",
                        response.optString("description", response.optString("error", "")));
                if (message.length() > 240) {
                    message = message.substring(0, 240);
                }
                throw new IOException("Ошибка MAX API (HTTP " + status + ")"
                        + (message.isEmpty() ? "" : ": " + message));
            }
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Ответ MAX API слишком большой");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
