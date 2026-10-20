package org.telegram.messenger.max;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MAX private WebSocket protocol client based on observed MIN-Safe-MAX traffic.
 *
 * This protocol is unofficial and can change without notice. Do not log tokens,
 * OTP codes, or full server payloads. Call methods from a worker thread only.
 */
public final class MaxPersonalClient implements AutoCloseable {
    private static final String ENDPOINT = "wss://ws-api.oneme.ru/websocket";
    private static final int PROTOCOL_VERSION = 11;
    private static final long RESPONSE_TIMEOUT_SECONDS = 18;

    private final AtomicInteger sequence = new AtomicInteger(1);
    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    private final String deviceId = UUID.randomUUID().toString();
    private WebSocket socket;
    private boolean initialized;
    private String verificationToken;
    private String phone;
    private String accessToken;
    private JSONObject loginPayload;

    public static final class ProtocolException extends IOException {
        private final JSONObject response;

        ProtocolException(String message, JSONObject response) {
            super(message);
            this.response = response;
        }

        public JSONObject getResponse() {
            return response;
        }
    }

    public synchronized void connect() throws IOException {
        if (socket != null && socket.isOpen()) {
            return;
        }
        initialized = false;
        inbox.clear();

        try {
            socket = new WebSocketFactory()
                    .setConnectionTimeout(12000)
                    .createSocket(ENDPOINT)
                    .addHeader("Origin", "https://web.max.ru")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                    .addListener(new WebSocketAdapter() {
                        @Override
                        public void onTextMessage(WebSocket websocket, String message) {
                            try {
                                inbox.offer(new JSONObject(message));
                            } catch (Exception ignored) {
                                // Ignore non-JSON frames; never log auth/session contents.
                            }
                        }

                        @Override
                        public void onError(WebSocket websocket, WebSocketException cause) {
                            inbox.offer(cause);
                        }
                    })
                    .connect();
        } catch (WebSocketException e) {
            socket = null;
            throw new IOException("Не удалось подключиться к MAX. Проверь интернет и попробуй ещё раз.", e);
        }

        JSONObject userAgent = new JSONObject();
        JSONObject init = new JSONObject();
        try {
            userAgent.put("deviceType", "ANDROID");
            userAgent.put("pushDeviceType", "ANDROID");
            userAgent.put("locale", "ru");
            userAgent.put("deviceLocale", "ru");
            userAgent.put("osVersion", "Android");
            userAgent.put("deviceName", "Telega Android");
            userAgent.put("headerUserAgent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36");
            userAgent.put("appVersion", "26.34.1");
            userAgent.put("screen", "1080x2400 1.0x");
            userAgent.put("timezone", java.util.TimeZone.getDefault().getID());
            init.put("deviceId", deviceId);
            init.put("userAgent", userAgent);
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить инициализацию MAX.", e);
        }

        JSONObject response = sendAndWait(6, init);
        throwIfError(response);
        initialized = true;
    }

    public JSONObject requestCode(String phoneNumber, String captchaToken) throws IOException {
        connect();
        if (phoneNumber == null || !phoneNumber.trim().matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new IllegalArgumentException("Введи номер в международном формате, например +79991234567.");
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("phone", phoneNumber.trim());
            payload.put("type", "START_AUTH");
            payload.put("language", "ru");
            if (captchaToken != null && !captchaToken.trim().isEmpty()) {
                payload.put("captchaToken", captchaToken.trim());
            }
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить запрос кода.", e);
        }

        JSONObject response;
        try {
            response = sendAndWait(17, payload);
            throwIfError(response);
        } catch (ProtocolException e) {
            JSONObject details = e.getResponse();
            String error = details == null ? "" : details.toString().toLowerCase(java.util.Locale.ROOT);
            if (error.contains("captcha") && (captchaToken == null || captchaToken.trim().isEmpty())) {
                try {
                    JSONObject captcha = new JSONObject();
                    captcha.put("source", "auth");
                    captcha.put("identifier", phoneNumber.trim());
                    JSONObject challenge = sendAndWait(224, captcha);
                    throwIfError(challenge);
                    JSONObject challengePayload = challenge.optJSONObject("payload");
                    String link = challengePayload == null ? "" : challengePayload.optString("link", "");
                    throw new ProtocolException("MAX потребовал CAPTCHA."
                            + (link.isEmpty() ? "" : " Открой ссылку CAPTCHA: " + link), challenge);
                } catch (ProtocolException nested) {
                    throw nested;
                } catch (org.json.JSONException jsonError) {
                    throw new IOException("Не удалось запросить CAPTCHA.", jsonError);
                }
            }
            throw e;
        }

        JSONObject data = response.optJSONObject("payload");
        if (data == null || data.optString("token", "").isEmpty()) {
            throw new ProtocolException("MAX не вернул токен подтверждения SMS. Возможно, требуется CAPTCHA или дополнительная проверка.", response);
        }
        verificationToken = data.optString("token");
        phone = phoneNumber.trim();
        return data;
    }

    public JSONObject verifyCode(String code) throws IOException {
        if (verificationToken == null || phone == null) {
            throw new IllegalStateException("Сначала запроси код из SMS.");
        }
        if (code == null || !code.trim().matches("[0-9]{4,8}")) {
            throw new IllegalArgumentException("Введи код из SMS.");
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("token", verificationToken);
            payload.put("verifyCode", code.trim());
            payload.put("authTokenType", "CHECK_CODE");
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить подтверждение кода.", e);
        }

        JSONObject response = sendAndWait(18, payload);
        throwIfError(response);
        JSONObject data = response.optJSONObject("payload");
        JSONObject attrs = data == null ? null : data.optJSONObject("tokenAttrs");
        JSONObject login = attrs == null ? null : attrs.optJSONObject("LOGIN");
        String token = login == null ? "" : login.optString("token", "");
        if (token.isEmpty()) {
            // A password challenge or another factor may have been returned instead of a LOGIN token.
            throw new ProtocolException("Код принят, но MAX не вернул готовый токен входа. Возможно, аккаунту требуется дополнительный пароль или другой этап авторизации.", response);
        }
        accessToken = token;
        verificationToken = null;
        return login(token);
    }

    private JSONObject login(String token) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", token);
            payload.put("interactive", true);
            payload.put("chatsCount", 100);
            payload.put("chatsSync", 100);
            payload.put("contactsSync", 0);
            payload.put("presenceSync", 0);
            payload.put("draftsSync", 0);
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить вход в MAX.", e);
        }

        JSONObject response = sendAndWait(19, payload);
        throwIfError(response);
        loginPayload = response.optJSONObject("payload");
        if (loginPayload == null) {
            throw new ProtocolException("MAX не вернул профиль после входа.", response);
        }
        return loginPayload;
    }

    public JSONObject getProfile() {
        JSONObject profile = loginPayload == null ? null : loginPayload.optJSONObject("profile");
        JSONObject contact = profile == null ? null : profile.optJSONObject("contact");
        return contact == null ? profile : contact;
    }

    public String getDisplayName() {
        JSONObject contact = getProfile();
        if (contact == null) return phone == null ? "MAX" : phone;
        org.json.JSONArray names = contact.optJSONArray("names");
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                JSONObject entry = names.optJSONObject(i);
                if (entry != null && "FULL_NAME".equals(entry.optString("type"))) {
                    return entry.optString("name", phone);
                }
            }
        }
        return phone == null ? "MAX" : phone;
    }

    public JSONObject getChats(int count) throws IOException {
        ensureLoggedIn();
        JSONObject payload = new JSONObject();
        try {
            payload.put("count", Math.max(1, Math.min(100, count)));
            payload.put("marker", 0);
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить список чатов.", e);
        }
        JSONObject response = sendAndWait(53, payload);
        throwIfError(response);
        return response.optJSONObject("payload") == null ? new JSONObject() : response.optJSONObject("payload");
    }

    public JSONObject getHistory(long chatId, int count) throws IOException {
        ensureLoggedIn();
        JSONObject payload = new JSONObject();
        try {
            payload.put("chatId", chatId);
            payload.put("backward", Math.max(1, Math.min(100, count)));
            payload.put("forward", 0);
            payload.put("from", System.currentTimeMillis());
            payload.put("getMessages", true);
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить историю чата.", e);
        }
        JSONObject response = sendAndWait(49, payload);
        throwIfError(response);
        return response.optJSONObject("payload") == null ? new JSONObject() : response.optJSONObject("payload");
    }

    public JSONObject sendMessage(long chatId, String text) throws IOException {
        ensureLoggedIn();
        if (chatId == 0) throw new IllegalArgumentException("Не выбран чат.");
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Введите текст сообщения.");
        if (text.length() > 4000) throw new IllegalArgumentException("Сообщение слишком длинное.");

        JSONObject message = new JSONObject();
        JSONObject payload = new JSONObject();
        try {
            message.put("text", text);
            message.put("cid", System.currentTimeMillis());
            message.put("elements", new org.json.JSONArray());
            message.put("attaches", new org.json.JSONArray());
            payload.put("chatId", chatId);
            payload.put("message", message);
            payload.put("notify", true);
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить сообщение.", e);
        }
        JSONObject response = sendAndWait(64, payload);
        throwIfError(response);
        return response.optJSONObject("payload") == null ? new JSONObject() : response.optJSONObject("payload");
    }

    private void ensureLoggedIn() throws IOException {
        if (accessToken == null || loginPayload == null) {
            throw new IOException("Сначала войди в аккаунт MAX.");
        }
    }

    private JSONObject sendAndWait(int opcode, JSONObject payload) throws IOException {
        if (socket == null || !socket.isOpen()) {
            throw new IOException("Соединение с MAX закрыто. Начни вход заново.");
        }
        int seq = sequence.getAndIncrement();
        JSONObject request = new JSONObject();
        try {
            request.put("ver", PROTOCOL_VERSION);
            request.put("cmd", 0);
            request.put("seq", seq);
            request.put("opcode", opcode);
            request.put("payload", payload == null ? new JSONObject() : payload);
        } catch (org.json.JSONException e) {
            throw new IOException("Не удалось подготовить запрос MAX.", e);
        }

        socket.sendText(request.toString());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RESPONSE_TIMEOUT_SECONDS);
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IOException("MAX не ответил вовремя (операция " + opcode + ").");
            }
            Object item;
            try {
                item = inbox.poll(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Запрос MAX прерван.", e);
            }
            if (item == null) {
                throw new IOException("MAX не ответил вовремя (операция " + opcode + ").");
            }
            if (item instanceof WebSocketException) {
                throw new IOException("Соединение с MAX оборвалось.", (WebSocketException) item);
            }
            JSONObject response = (JSONObject) item;
            if (response.optInt("cmd", -1) == 0) {
                continue; // unsolicited push event
            }
            if (response.optInt("seq", -1) != seq) {
                continue;
            }
            if (response.optInt("opcode", opcode) != opcode) {
                continue;
            }
            return response;
        }
    }

    private static void throwIfError(JSONObject response) throws ProtocolException {
        if (response == null) {
            throw new ProtocolException("Пустой ответ MAX.", null);
        }
        int cmd = response.optInt("cmd", -1);
        JSONObject payload = response.optJSONObject("payload");
        if (cmd == 3 || (payload != null && payload.has("error"))) {
            String message = payload == null ? "" : payload.optString("localizedMessage",
                    payload.optString("message", payload.optString("error", "")));
            if (message.isEmpty()) message = "MAX отклонил запрос.";
            throw new ProtocolException(message, response);
        }
    }

    @Override
    public synchronized void close() {
        verificationToken = null;
        accessToken = null;
        loginPayload = null;
        initialized = false;
        if (socket != null) {
            try {
                socket.disconnect();
            } catch (Exception ignored) {
            }
            socket = null;
        }
        inbox.clear();
    }
}
