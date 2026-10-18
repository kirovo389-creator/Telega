/*
 * Telega in-process local Telegram transport.
 *
 * Network flow: Telegram SOCKS5 client -> loopback-only SOCKS5 -> Telegram MTProto
 * over WSS. The architecture is compatible with MordaTG and Flowseal/tg-ws-proxy;
 * this Telega-specific implementation does not launch MordaTG's Activity or Service.
 * Endpoint pool compatibility reference: https://github.com/Flowseal/tg-ws-proxy (MIT).
 */
package org.telegram.messenger;

import android.app.Activity;
import android.os.Build;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.ProxySettings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/**
 * A SOCKS5 endpoint bound exclusively to loopback. It exists in the Telega process;
 * there is no foreground service, boot receiver, VPN interface, or notification.
 */
public final class TelegaLocalBypassProxy {
    private static final String TAG = "TelegaLocalBypass";
    private static final Object LOCK = new Object();
    private static final String LOOPBACK = "127.0.0.1";
    private static final int DEFAULT_PORT = 51837;
    private static final int PORT_ATTEMPTS = 16;
    private static final int MAX_CONNECTIONS = 64;
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 45_000;
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    private static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;
    private static final int WS_HEADER_LIMIT = 32 * 1024;
    private static final int MAX_ENDPOINT_ATTEMPTS = 8;

    private static volatile ServerSocket listener;
    private static volatile ExecutorService workers;
    private static volatile boolean running;
    private static volatile int listenPort;
    private static volatile String lastError;
    private static final Set<Socket> clients = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
    private static final Semaphore connectionPermits = new Semaphore(MAX_CONNECTIONS);
    private static final Map<String, Endpoint> lastSuccessfulEndpoints = new ConcurrentHashMap<>();

    private TelegaLocalBypassProxy() {
    }

    public static boolean isEnabled() {
        return TelegaSettings.isLocalBypassEnabled();
    }

    public static boolean isRunning() {
        ServerSocket current = listener;
        return running && current != null && !current.isClosed();
    }

    public static int getPort() {
        return isRunning() ? listenPort : 0;
    }
 
    /** Stable preferred port for manual SOCKS5 clients; updated if fallback is required. */
    private static int getPreferredPort() {
        try {
            if (ApplicationLoader.applicationContext != null) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                        "telega_settings", Activity.MODE_PRIVATE);
                int saved = preferences.getInt("local_bypass_port", DEFAULT_PORT);
                if (saved >= 1024 && saved <= 65535) return saved;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to read saved local proxy port", error);
        }
        return DEFAULT_PORT;
    }

    public static String getStatusText() {
        if (!isEnabled()) {
            return "Выключен. Включи режим, чтобы использовать локальный обход.";
        }
        if (isRunning()) {
            return "Локальный SOCKS5 работает: " + LOOPBACK + ":" + listenPort;
        }
        String error = lastError;
        if (error != null && !error.isEmpty()) {
            return "Не удалось запустить локальный прокси: " + error;
        }
        return "Включён. Локальный прокси запускается вместе с сетевым модулем Telega.";
    }

    /**
     * Starts the loopback server synchronously so ConnectionsManager can point its
     * native SOCKS implementation at a bound port before opening Telegram sockets.
     */
    public static int start() {
        synchronized (LOCK) {
            if (isRunning()) {
                return listenPort;
            }
            ServerSocket bound = null;
            try {
                int preferredPort = getPreferredPort();
                List<Integer> candidatePorts = new ArrayList<>();
                candidatePorts.add(preferredPort);
                for (int i = 0; i < PORT_ATTEMPTS; i++) {
                    int candidatePort = DEFAULT_PORT + i;
                    if (!candidatePorts.contains(candidatePort)) candidatePorts.add(candidatePort);
                }
                for (int port : candidatePorts) {
                    ServerSocket candidate = new ServerSocket();
                    try {
                        candidate.setReuseAddress(true);
                        candidate.bind(new InetSocketAddress(
                                InetAddress.getByName(LOOPBACK), port), 32);
                        bound = candidate;
                        break;
                    } catch (BindException e) {
                        closeQuietly(candidate);
                    } catch (IOException e) {
                        closeQuietly(candidate);
                        throw e;
                    }
                }
                if (bound == null) {
                    throw new BindException("Не найден свободный loopback-порт");
                }
                ServerSocket server = bound;
                ExecutorService pool = Executors.newCachedThreadPool(task -> {
                    Thread thread = new Thread(task, "TelegaLocalBypass");
                    thread.setDaemon(true);
                    return thread;
                });
                listener = server;
                workers = pool;
                listenPort = server.getLocalPort();
                try {
                    if (ApplicationLoader.applicationContext != null) {
                        ApplicationLoader.applicationContext.getSharedPreferences(
                                "telega_settings", Activity.MODE_PRIVATE)
                                .edit().putInt("local_bypass_port", listenPort).apply();
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to persist local proxy port", error);
                }
                lastError = null;
                running = true;
                pool.execute(() -> acceptLoop(server, pool));
                return listenPort;
            } catch (Throwable error) {
                closeQuietly(bound);
                running = false;
                listener = null;
                listenPort = 0;
                lastError = safeMessage(error);
                Log.e(TAG, "Unable to start local SOCKS5", error);
                return 0;
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            running = false;
            ServerSocket server = listener;
            listener = null;
            listenPort = 0;
            closeQuietly(server);
            for (Socket client : clients) {
                closeQuietly(client);
            }
            clients.clear();
            ExecutorService pool = workers;
            workers = null;
            if (pool != null) {
                pool.shutdownNow();
            }
        }
    }

    /**
     * Persists the option, activates the local route, or restores the user's regular
     * proxy settings. A failed start never leaves Telegram pointed at a dead port.
     */
    public static boolean isLocalEndpoint(String address, int port) {
        if (address == null) return false;
        String host = address.trim();
        return isRunning() && port == listenPort
                && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host) || "[::1]".equals(host));
    }

    public static int ensureRunning() {
        if (!isEnabled()) return 0;
        if (isRunning()) return listenPort;
        return start();
    }

    private static void persistLocalProxy(int port) {
        if (ApplicationLoader.applicationContext == null) return;
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(
                    "telega_settings", Activity.MODE_PRIVATE)
                    .edit().putInt("local_bypass_port", port).apply();
        } catch (Throwable error) {
            Log.w(TAG, "Unable to persist local proxy port", error);
        }
    }

    public static boolean setEnabled(boolean enabled) {
        TelegaSettings.setLocalBypassEnabled(enabled);
        if (!enabled) {
            stop();
            restoreConfiguredProxy();
            return true;
        }

        int port = ensureRunning();
        if (port <= 0) {
            TelegaSettings.setLocalBypassEnabled(false);
            restoreConfiguredProxy();
            return false;
        }
        try {
            persistLocalProxy(port);
            ConnectionsManager.setProxySettings(true, createLocalSettings(port));
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                try {
                    ConnectionsManager.getInstance(account).checkConnection();
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to refresh network after enabling local bypass", error);
                }
            }
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Unable to apply local proxy settings", error);
            lastError = safeMessage(error);
            stop();
            TelegaSettings.setLocalBypassEnabled(false);
            restoreConfiguredProxy();
            return false;
        }
    }


    private static ProxySettings createLocalSettings(int port) {
        return ProxySettings.builder()
                .setType(ProxySettings.Type.SOCKS5)
                .setAddress(LOOPBACK)
                .setPort(port)
                .setUser("")
                .setPassword("")
                .setSecret("")
                .build();
    }

    private static void restoreConfiguredProxy() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                    "mainconfig", Activity.MODE_PRIVATE);
            ProxySettings saved = ProxySettings.fromSharedPreferences(preferences);
            boolean enabled = preferences.getBoolean("proxy_enabled", false)
                    && saved != null && saved.isValid();
            ConnectionsManager.setProxySettings(enabled, enabled ? saved : null);
        } catch (Throwable error) {
            Log.e(TAG, "Unable to restore configured proxy settings", error);
        }
    }

    private static void acceptLoop(ServerSocket server, ExecutorService pool) {
        while (running && server == listener && !server.isClosed()) {
            Socket client = null;
            try {
                client = server.accept();
                client.setTcpNoDelay(true);
                client.setKeepAlive(true);
                if (!connectionPermits.tryAcquire()) {
                    rejectBusy(client);
                    continue;
                }
                clients.add(client);
                Socket accepted = client;
                pool.execute(() -> handleClient(accepted));
            } catch (SocketException e) {
                if (running) {
                    lastError = safeMessage(e);
                    Log.w(TAG, "Local SOCKS listener stopped unexpectedly", e);
                }
                closeQuietly(client);
                break;
            } catch (Throwable e) {
                if (running) {
                    lastError = safeMessage(e);
                    Log.w(TAG, "Local SOCKS accept error", e);
                }
                closeQuietly(client);
            }
        }
    }

    private static void rejectBusy(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(1_000);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            negotiate(input, output);
            Request ignored = readConnectRequest(input, output);
            if (ignored != null) {
                writeSocksReply(output, 1);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void handleClient(Socket client) {
        WsConnection upstream = null;
        boolean connectRequestRead = false;
        boolean connectAccepted = false;
        try {
            client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            BufferedInputStream input = new BufferedInputStream(client.getInputStream(), 64 * 1024);
            BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream(), 64 * 1024);
            if (!negotiate(input, output)) {
                return;
            }
            Request request = readConnectRequest(input, output);
            if (request == null) {
                return;
            }
            connectRequestRead = true;

            Route route = resolveRoute(request.host, request.port);
            if (route == null) {
                writeSocksReply(output, 2, client);
                return;
            }

            upstream = connectUpstream(route);
            writeSocksReply(output, 0, client);
            connectAccepted = true;

            byte[] initBytes = readExact(input, MtProtoObfuscation.INIT_SIZE);
            MtProtoInit init = MtProtoObfuscation.parseClientInit(initBytes);
            if (init == null) {
                throw new IOException("Неизвестный MTProto-транспорт");
            }
            upstream.sendBinary(initBytes);
            client.setSoTimeout(0);
            relay(client, input, output, upstream, init);
            upstream = null; // relay owns and closes the WebSocket
        } catch (Throwable error) {
            if (connectRequestRead && !connectAccepted) {
                try {
                    writeSocksReply(new BufferedOutputStream(client.getOutputStream()), 4, client);
                } catch (Throwable ignore) {
                }
            }
            if (running && !(error instanceof SocketException) && !(error instanceof EOFException)) {
                Log.w(TAG, "Telegram local-proxy connection failed: " + safeMessage(error));
            }
        } finally {
            closeQuietly(upstream);
            closeQuietly(client);
            clients.remove(client);
            connectionPermits.release();
        }
    }

    private static boolean negotiate(InputStream input, OutputStream output) throws IOException {
        int version = readByte(input);
        int methodCount = readByte(input);
        if (version != 5 || methodCount < 1) {
            output.write(new byte[]{5, (byte) 0xff});
            output.flush();
            return false;
        }
        byte[] methods = readExact(input, methodCount);
        boolean noAuth = false;
        for (byte method : methods) {
            if ((method & 0xff) == 0) {
                noAuth = true;
                break;
            }
        }
        output.write(new byte[]{5, (byte) (noAuth ? 0 : 0xff)});
        output.flush();
        return noAuth;
    }

    private static Request readConnectRequest(InputStream input, OutputStream output) throws IOException {
        byte[] header = readExact(input, 4);
        if ((header[0] & 0xff) != 5) {
            writeSocksReply(output, 1);
            return null;
        }
        if ((header[1] & 0xff) != 1) {
            writeSocksReply(output, 7);
            return null;
        }
        if (header[2] != 0) {
            writeSocksReply(output, 1);
            return null;
        }

        String host;
        int addressType = header[3] & 0xff;
        if (addressType == 1) {
            host = InetAddress.getByAddress(readExact(input, 4)).getHostAddress();
        } else if (addressType == 3) {
            int length = readByte(input);
            if (length <= 0) {
                writeSocksReply(output, 8);
                return null;
            }
            host = new String(readExact(input, length), StandardCharsets.US_ASCII);
        } else if (addressType == 4) {
            host = InetAddress.getByAddress(readExact(input, 16)).getHostAddress();
        } else {
            writeSocksReply(output, 8);
            return null;
        }

        byte[] portBytes = readExact(input, 2);
        int port = ((portBytes[0] & 0xff) << 8) | (portBytes[1] & 0xff);
        if (port <= 0) {
            writeSocksReply(output, 2);
            return null;
        }
        return new Request(host, port);
    }

    private static void writeSocksReply(OutputStream output, int code) throws IOException {
        output.write(new byte[]{5, (byte) code, 0, 1, 127, 0, 0, 1, 0, 0});
        output.flush();
    }

    /** Include the actual local bind endpoint so SOCKS clients can validate the handshake. */
    private static void writeSocksReply(OutputStream output, int code, Socket client) throws IOException {
        byte[] address = client != null && client.getLocalAddress() != null
                ? client.getLocalAddress().getAddress() : new byte[]{127, 0, 0, 1};
        int addressType = address.length == 16 ? 4 : 1;
        output.write(new byte[]{5, (byte) code, 0, (byte) addressType});
        output.write(address);
        int port = client != null ? client.getLocalPort() : 0;
        output.write((port >>> 8) & 0xff);
        output.write(port & 0xff);
        output.flush();
    }

    private static Route resolveRoute(String inputHost, int port) {
        if (port != 80 && port != 443 && port != 5222) {
            return null;
        }
        if (inputHost == null || inputHost.trim().isEmpty()) {
            return null;
        }
        String host = inputHost.trim().toLowerCase(Locale.US);
        Matcher wsHost = Pattern.compile("^kws([1-5]|203)(-1)?\\.web\\.telegram\\.org$").matcher(host);
        if (wsHost.matches()) {
            return new Route(Integer.parseInt(wsHost.group(1)), wsHost.group(2) != null);
        }
        if (!looksLikeAddress(host) && !host.endsWith(".telegram.org")) {
            return null;
        }
        try {
            InetAddress[] addresses = looksLikeAddress(host)
                    ? new InetAddress[]{InetAddress.getByName(stripBrackets(host))}
                    : InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                int dc = resolveAddress(address);
                if (dc > 0) {
                    return new Route(dc, host.contains("-1.web.telegram.org"));
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean looksLikeAddress(String host) {
        String value = stripBrackets(host);
        return value.indexOf(':') >= 0 || value.matches("[0-9.]+");
    }

    private static String stripBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
    }

    private static int resolveAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            int c = bytes[2] & 0xff;
            int d = bytes[3] & 0xff;
            if (a == 91 && b == 105 && c == 192 && d == 100) return 203;
            if (a == 91 && b == 108 && c == 56) return 5;
            if (a == 149 && b == 154 && c == 175) return d < 64 ? 1 : 3;
            if (a == 149 && b == 154 && c == 167) return d < 64 ? 2 : 4;
            if (a == 149 && b == 154 && c == 171) return 5;
            return 0;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            // Use the actual address words: Android can expand "::" in getHostAddress(),
            // making a string-suffix parser misidentify DCs or reject valid Telegram IPv6.
            int word0 = ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
            int word1 = ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
            int word2 = ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff);
            int word3 = ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
            if (word0 != 0x2001) return 0;
            if (word1 == 0x0b28 && word2 == 0xf23d && word3 == 0xf001) return 1;
            if (word1 == 0x067c && word2 == 0x04e8 && word3 == 0xf002) return 2;
            if (word1 == 0x0b28 && word2 == 0xf23d && word3 == 0xf003) return 3;
            if (word1 == 0x067c && word2 == 0x04e8 && word3 == 0xf004) return 4;
            if (word1 == 0x0b28 && word2 == 0xf23f && word3 == 0xf005) return 5;
        }
        return 0;
    }

    private static WsConnection connectUpstream(Route route) throws IOException {
        String key = route.dcId + ":" + route.media;
        List<Endpoint> candidates = candidateEndpoints(route);
        Endpoint cached = lastSuccessfulEndpoints.get(key);
        if (cached != null) {
            candidates.add(0, cached);
        }
        Map<String, Endpoint> unique = new LinkedHashMap<>();
        for (Endpoint endpoint : candidates) {
            if (!unique.containsKey(endpoint.tlsHost)) {
                unique.put(endpoint.tlsHost, endpoint);
            }
        }

        Throwable last = null;
        int attempts = 0;
        for (Endpoint endpoint : unique.values()) {
            if (attempts >= MAX_ENDPOINT_ATTEMPTS) {
                break;
            }
            if (attempts > 0) {
                try {
                    Thread.sleep(Math.min(500, 100L * attempts));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Подключение прервано", e);
                }
            }
            attempts++;
            try {
                WsConnection connection = WsConnection.connect(endpoint.connectHost, endpoint.tlsHost, "/apiws");
                lastSuccessfulEndpoints.put(key, endpoint);
                return connection;
            } catch (Throwable error) {
                last = error;
                Log.w(TAG, "Telegram WSS endpoint failed: " + endpoint.tlsHost
                        + " (" + error.getClass().getSimpleName() + ")");
            }
        }
        throw new IOException("Нет доступного Telegram WSS-маршрута", last);
    }

    private static List<Endpoint> candidateEndpoints(Route route) {
        List<Endpoint> result = new ArrayList<>();
        if ((route.dcId >= 1 && route.dcId <= 5) || route.dcId == 203) {
            for (String base : CF_PROXY_DOMAINS) {
                String domain = "kws" + route.dcId + "." + base;
                result.add(new Endpoint(domain, domain));
            }
        }
        if (route.dcId != 203) {
            String[] domains = route.media
                    ? new String[]{"kws" + route.dcId + "-1.web.telegram.org",
                            "kws" + route.dcId + ".web.telegram.org"}
                    : new String[]{"kws" + route.dcId + ".web.telegram.org",
                            "kws" + route.dcId + "-1.web.telegram.org"};
            for (String domain : domains) {
                String directHost = (route.dcId == 2 || route.dcId == 4)
                        ? "149.154.167.220" : domain;
                result.add(new Endpoint(directHost, domain));
            }
        }
        return result;
    }

    private static final String[] CF_PROXY_DOMAINS = {
            "offshor.co.uk", "cakeisalie.co.uk", "pclead.co.uk", "noskomnadzor.co.uk",
            "lovetrue.co.uk", "sorokdva.co.uk", "pyatdesyatdva.co.uk", "kartoshka.co.uk",
            "sorokodin.co.uk", "pyatdesyatodin.co.uk", "notelega.co.uk", "ebally.co.uk",
            "nebally.co.uk", "havegreatday.co.uk", "pomogite.co.uk", "fixtelega.co.uk",
            "sadnews.co.uk", "onedaychamp.co.uk", "stopblocking.co.uk", "nothingthere.co.uk"
    };

    private static void relay(Socket client, InputStream input, OutputStream output,
                              WsConnection upstream, MtProtoInit init) throws IOException {
        MessageSplitter splitter = new MessageSplitter(init);
        WsConnection ws = upstream;
        Thread receiver = new Thread(() -> {
            try {
                while (running && !client.isClosed()) {
                    byte[] message = ws.receiveBinary();
                    if (message == null) break;
                    synchronized (output) {
                        output.write(message);
                        output.flush();
                    }
                }
            } catch (Throwable error) {
                if (running && !(error instanceof SocketException)) {
                    Log.w(TAG, "Telegram WSS receive failed: " + safeMessage(error));
                }
            } finally {
                closeQuietly(client);
                closeQuietly(ws);
            }
        }, "TelegaLocalBypassRx");
        receiver.setDaemon(true);
        receiver.start();

        try {
            byte[] buffer = new byte[64 * 1024];
            while (running && !client.isClosed()) {
                int count = input.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                byte[] chunk = Arrays.copyOf(buffer, count);
                for (byte[] frame : splitter.split(chunk)) {
                    ws.sendBinary(frame);
                }
            }
            for (byte[] tail : splitter.flush()) {
                ws.sendBinary(tail);
            }
        } finally {
            closeQuietly(ws);
            closeQuietly(client);
            try {
                receiver.join(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int readByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) throw new EOFException("Соединение SOCKS5 закрыто");
        return value;
    }

    private static byte[] readExact(InputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(result, offset, length - offset);
            if (count < 0) throw new EOFException("Соединение закрыто до завершения пакета");
            if (count == 0) continue;
            offset += count;
        }
        return result;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error == null ? "unknown error" : error.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ').substring(
                0, Math.min(180, message.length()));
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignore) {
            }
        }
    }

    private static final class Request {
        final String host;
        final int port;
        Request(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final class Route {
        final int dcId;
        final boolean media;
        Route(int dcId, boolean media) {
            this.dcId = dcId;
            this.media = media;
        }
    }

    private static final class Endpoint {
        final String connectHost;
        final String tlsHost;
        Endpoint(String connectHost, String tlsHost) {
            this.connectHost = connectHost;
            this.tlsHost = tlsHost;
        }
    }

    private static final class MtProtoInit {
        final Cipher decryptor;
        final int transport;
        MtProtoInit(Cipher decryptor, int transport) {
            this.decryptor = decryptor;
            this.transport = transport;
        }
    }

    private static final class MtProtoObfuscation {
        static final int INIT_SIZE = 64;
        static final int ABRIDGED = 1;
        static final int INTERMEDIATE = 2;
        static final int PADDED_INTERMEDIATE = 3;

        static MtProtoInit parseClientInit(byte[] init) throws IOException {
            if (init == null || init.length != INIT_SIZE) return null;
            try {
                byte[] key = Arrays.copyOfRange(init, 8, 40);
                byte[] iv = Arrays.copyOfRange(init, 40, 56);
                Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                        new IvParameterSpec(iv));
                byte[] plain = cipher.update(init);
                if (plain == null || plain.length < INIT_SIZE) return null;
                int transport = 0;
                if (plain[56] == (byte) 0xef && plain[57] == (byte) 0xef
                        && plain[58] == (byte) 0xef && plain[59] == (byte) 0xef) {
                    transport = ABRIDGED;
                } else if (plain[56] == (byte) 0xee && plain[57] == (byte) 0xee
                        && plain[58] == (byte) 0xee && plain[59] == (byte) 0xee) {
                    transport = INTERMEDIATE;
                } else if (plain[56] == (byte) 0xdd && plain[57] == (byte) 0xdd
                        && plain[58] == (byte) 0xdd && plain[59] == (byte) 0xdd) {
                    transport = PADDED_INTERMEDIATE;
                }
                if (transport == 0) return null;
                int dc = Math.abs((int) ByteBuffer.wrap(plain, 60, 2)
                        .order(ByteOrder.LITTLE_ENDIAN).getShort());
                if (!((dc >= 1 && dc <= 5) || dc == 203)) return null;
                return new MtProtoInit(cipher, transport);
            } catch (Exception error) {
                throw new IOException("Не удалось разобрать заголовок MTProto", error);
            }
        }
    }

    private static final class MessageSplitter {
        private final MtProtoInit init;
        private byte[] encrypted = new byte[0];
        private byte[] plain = new byte[0];
        private boolean disabled;

        MessageSplitter(MtProtoInit init) {
            this.init = init;
        }

        List<byte[]> split(byte[] chunk) throws IOException {
            if (chunk == null || chunk.length == 0) return Collections.emptyList();
            if (disabled) return Collections.singletonList(chunk);
            if ((long) encrypted.length + chunk.length > MAX_MESSAGE_BYTES) {
                throw new IOException("MTProto message exceeds safety limit");
            }
            byte[] decrypted = init.decryptor.update(chunk);
            if (decrypted == null) decrypted = new byte[0];
            encrypted = concat(encrypted, chunk);
            plain = concat(plain, decrypted);

            List<byte[]> parts = new ArrayList<>();
            int offset = 0;
            while (offset < encrypted.length) {
                int available = encrypted.length - offset;
                int length = packetLength(offset, available);
                if (length < 0) break;
                if (length == 0) {
                    parts.add(Arrays.copyOfRange(encrypted, offset, encrypted.length));
                    offset = encrypted.length;
                    disabled = true;
                    break;
                }
                if (available < length) break;
                parts.add(Arrays.copyOfRange(encrypted, offset, offset + length));
                offset += length;
            }
            if (offset > 0) {
                encrypted = Arrays.copyOfRange(encrypted, offset, encrypted.length);
                plain = Arrays.copyOfRange(plain, offset, plain.length);
            }
            return parts;
        }

        List<byte[]> flush() {
            if (encrypted.length == 0) return Collections.emptyList();
            byte[] tail = encrypted;
            encrypted = new byte[0];
            plain = new byte[0];
            return Collections.singletonList(tail);
        }

        private int packetLength(int offset, int available) {
            if (offset >= plain.length) return -1;
            if (init.transport == MtProtoObfuscation.ABRIDGED) {
                int first = plain[offset] & 0xff;
                if (first == 0x7f || first == 0xff) {
                    if (available < 4 || plain.length - offset < 4) return -1;
                    long words = (plain[offset + 1] & 0xff)
                            | ((long) (plain[offset + 2] & 0xff) << 8)
                            | ((long) (plain[offset + 3] & 0xff) << 16);
                    long payload = words * 4L;
                    if (payload <= 0 || payload > MAX_MESSAGE_BYTES - 4) return 0;
                    return (int) payload + 4;
                }
                long payload = (long) (first & 0x7f) * 4L;
                if (payload <= 0 || payload > MAX_MESSAGE_BYTES - 1) return 0;
                return (int) payload + 1;
            }
            if (available < 4 || plain.length - offset < 4) return -1;
            int payload = ByteBuffer.wrap(plain, offset, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x7fffffff;
            if (payload <= 0 || payload > MAX_MESSAGE_BYTES - 4) return 0;
            return payload + 4;
        }

        private static byte[] concat(byte[] first, byte[] second) {
            if (second.length == 0) return first;
            if (first.length == 0) return second;
            byte[] result = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }
    }

    private static final class WsConnection implements Closeable {
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private final SSLSocket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;
        private final Object writeLock = new Object();
        private final SecureRandom random = new SecureRandom();
        private volatile boolean closed;
        private byte[] fragments;

        private WsConnection(SSLSocket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream(), 64 * 1024);
            this.output = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
        }

        static WsConnection connect(String connectHost, String tlsHost, String path) throws IOException {
            if (path == null || !path.startsWith("/")) {
                throw new IOException("Invalid WebSocket path");
            }
            Socket raw = new Socket();
            try {
                raw.setTcpNoDelay(true);
                raw.setKeepAlive(true);
                raw.connect(new InetSocketAddress(connectHost, 443), CONNECT_TIMEOUT_MS);
                raw.setSoTimeout(READ_TIMEOUT_MS);

                SSLSocket ssl = (SSLSocket) SSLContext.getInstance("TLS").getSocketFactory()
                        .createSocket(raw, tlsHost, 443, true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    SSLParameters parameters = ssl.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    parameters.setServerNames(Collections.singletonList(new SNIHostName(tlsHost)));
                    ssl.setSSLParameters(parameters);
                }
                ssl.setSoTimeout(READ_TIMEOUT_MS);
                ssl.startHandshake();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                        && !HttpsURLConnection.getDefaultHostnameVerifier().verify(tlsHost, ssl.getSession())) {
                    throw new IOException("TLS certificate host mismatch");
                }

                BufferedInputStream input = new BufferedInputStream(ssl.getInputStream(), 64 * 1024);
                BufferedOutputStream output = new BufferedOutputStream(ssl.getOutputStream(), 64 * 1024);
                byte[] nonce = new byte[16];
                new SecureRandom().nextBytes(nonce);
                String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
                String request = "GET " + path + " HTTP/1.1\r\n"
                        + "Host: " + tlsHost + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + key + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Sec-WebSocket-Protocol: binary\r\n\r\n";
                output.write(request.getBytes(StandardCharsets.US_ASCII));
                output.flush();

                String statusLine = readHttpLine(input);
                String[] statusParts = statusLine.split(" ");
                int status = statusParts.length > 1 ? parseInt(statusParts[1], 0) : 0;
                Map<String, String> headers = new LinkedHashMap<>();
                int headerSize = statusLine.length();
                while (true) {
                    String line = readHttpLine(input);
                    headerSize += line.length();
                    if (headerSize > WS_HEADER_LIMIT) {
                        throw new IOException("WebSocket response headers are too large");
                    }
                    if (line.isEmpty()) break;
                    int separator = line.indexOf(':');
                    if (separator > 0) {
                        headers.put(line.substring(0, separator).trim().toLowerCase(Locale.US),
                                line.substring(separator + 1).trim());
                    }
                }
                String expectedAccept = Base64.encodeToString(
                        MessageDigest.getInstance("SHA-1").digest(
                                (key + WS_GUID).getBytes(StandardCharsets.US_ASCII)),
                        Base64.NO_WRAP);
                if (status != 101
                        || !expectedAccept.equals(headers.get("sec-websocket-accept"))
                        || !"websocket".equalsIgnoreCase(headers.get("upgrade"))) {
                    throw new IOException("WebSocket upgrade failed: HTTP " + status);
                }
                ssl.setSoTimeout(0);
                return new WsConnection(ssl);
            } catch (Throwable error) {
                closeQuietly(raw);
                if (error instanceof IOException) throw (IOException) error;
                throw new IOException("WebSocket connection failed", error);
            }
        }

        void sendBinary(byte[] payload) throws IOException {
            sendFrame(0x2, payload);
        }

        byte[] receiveBinary() throws IOException {
            while (!closed) {
                int first = input.read();
                if (first < 0) return null;
                int second = readByte(input);
                boolean fin = (first & 0x80) != 0;
                int opcode = first & 0x0f;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7f;
                if (length == 126) {
                    length = readUnsigned(input, 2);
                } else if (length == 127) {
                    length = readUnsigned(input, 8);
                }
                if (length < 0 || length > MAX_MESSAGE_BYTES || length > Integer.MAX_VALUE) {
                    throw new IOException("WebSocket frame exceeds safety limit");
                }
                byte[] mask = masked ? readExact(input, 4) : null;
                byte[] payload = readExact(input, (int) length);
                if (mask != null) applyMask(payload, mask);

                if (opcode == 0x8) {
                    if (!closed && payload.length <= 125) {
                        try { sendFrame(0x8, payload); } catch (Throwable ignore) {}
                    }
                    close();
                    return null;
                }
                if (opcode == 0x9) {
                    if (payload.length <= 125) sendFrame(0xA, payload);
                    continue;
                }
                if (opcode == 0xA) continue;
                if (opcode == 0x2 || opcode == 0x1) {
                    if (fragments == null && fin) return payload;
                    fragments = fragments == null ? payload : concat(fragments, payload);
                    if (fragments.length > MAX_MESSAGE_BYTES) {
                        throw new IOException("WebSocket message exceeds safety limit");
                    }
                    if (fin) {
                        byte[] completed = fragments;
                        fragments = null;
                        return completed;
                    }
                    continue;
                }
                if (opcode == 0x0) {
                    if (fragments == null) {
                        throw new IOException("Unexpected WebSocket continuation frame");
                    }
                    fragments = concat(fragments, payload);
                    if (fragments.length > MAX_MESSAGE_BYTES) {
                        throw new IOException("WebSocket message exceeds safety limit");
                    }
                    if (fin) {
                        byte[] completed = fragments;
                        fragments = null;
                        return completed;
                    }
                    continue;
                }
                throw new IOException("Unsupported WebSocket opcode");
            }
            return null;
        }

        private void sendFrame(int opcode, byte[] payload) throws IOException {
            if (payload == null) payload = new byte[0];
            if (closed) throw new IOException("WebSocket is closed");
            if (payload.length > MAX_MESSAGE_BYTES) {
                throw new IOException("WebSocket frame exceeds safety limit");
            }
            synchronized (writeLock) {
                if (closed) throw new IOException("WebSocket is closed");
                byte[] mask = new byte[4];
                random.nextBytes(mask);
                output.write(0x80 | opcode);
                if (payload.length < 126) {
                    output.write(0x80 | payload.length);
                } else if (payload.length <= 0xffff) {
                    output.write(0x80 | 126);
                    writeUnsigned(output, payload.length, 2);
                } else {
                    output.write(0x80 | 127);
                    writeUnsigned(output, payload.length, 8);
                }
                output.write(mask);
                byte[] masked = payload.clone();
                applyMask(masked, mask);
                output.write(masked);
                output.flush();
            }
        }

        @Override
        public void close() {
            synchronized (writeLock) {
                if (closed) return;
                closed = true;
                closeQuietly(socket);
            }
        }

        private static byte[] concat(byte[] first, byte[] second) throws IOException {
            if ((long) first.length + second.length > MAX_MESSAGE_BYTES) {
                throw new IOException("WebSocket message exceeds safety limit");
            }
            byte[] result = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }

        private static void applyMask(byte[] data, byte[] mask) {
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (data[i] ^ mask[i & 3]);
            }
        }

        private static long readUnsigned(InputStream input, int bytes) throws IOException {
            long result = 0;
            for (int i = 0; i < bytes; i++) {
                result = (result << 8) | readByte(input);
                if (result < 0) throw new IOException("Invalid WebSocket frame length");
            }
            return result;
        }

        private static void writeUnsigned(OutputStream output, long value, int bytes) throws IOException {
            for (int i = bytes - 1; i >= 0; i--) {
                output.write((int) ((value >>> (i * 8)) & 0xff));
            }
        }

        private static String readHttpLine(BufferedInputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (bytes.size() <= 8192) {
                int value = input.read();
                if (value < 0) throw new EOFException("Unexpected end of WebSocket HTTP response");
                if (value == '\n') break;
                if (value != '\r') bytes.write(value);
            }
            if (bytes.size() > 8192) throw new IOException("WebSocket HTTP header line is too long");
            return bytes.toString("ISO-8859-1");
        }

        private static int parseInt(String value, int fallback) {
            try { return Integer.parseInt(value); } catch (Exception ignore) { return fallback; }
        }
    }
}