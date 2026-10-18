#!/usr/bin/env python3
"""Fix AutoReply, local bypass persist, checkProxy."""
from pathlib import Path

def patch_auto_reply():
    path = Path("TMessagesProj/src/main/java/org/telegram/messenger/TelegaAutoReplyAgent.java")
    if not path.exists():
        print("AutoReply missing"); return
    t = path.read_text(encoding="utf-8")
    if "canAutoReplyNow" in t:
        print("AutoReply already patched"); return

    old_bg = """    private static boolean isBackground() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        return detector != null && detector.isBackground();
    }

    private static boolean isForegroundNow() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        return detector != null && detector.isForeground();
    }"""
    new_bg = """    private static boolean isBackground() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        if (detector == null) {
            return true;
        }
        return detector.isBackground() || !detector.isForeground();
    }

    private static boolean isForegroundNow() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        return detector != null && detector.isForeground();
    }

    private static boolean canAutoReplyNow() {
        return isBackground() || !isForegroundNow();
    }"""
    if old_bg in t:
        t = t.replace(old_bg, new_bg, 1)
        print("patched isBackground")
    else:
        print("WARN isBackground")

    # Current tree may already drop the unread check; still switch to canAutoReplyNow.
    old_h_variants = [
        """            // Only capture new incoming messages which were unread while the app was in background.
            if (!isBackground() || !isConfigured() || !message.messageOwner.unread) continue;
            String body = message.messageOwner.message;
            if (TextUtils.isEmpty(body)) continue;""",
        """            // The callback is already a new-message event. Telegram may clear the unread flag
            // before delivery, so do not require messageOwner.unread here.
            if (!isBackground() || !isConfigured()) continue;
            String body = message.messageOwner.message;
            if (TextUtils.isEmpty(body)) continue;""",
        """            if (!isBackground() || !isConfigured()) continue;
            String body = message.messageOwner.message;
            if (TextUtils.isEmpty(body)) continue;""",
    ]
    new_h = """            // Capture incoming private messages while the app is not in the foreground.
            if (!canAutoReplyNow() || !isConfigured()) continue;
            String body = message.messageOwner.message;
            if (TextUtils.isEmpty(body)) continue;"""
    for old_h in old_h_variants:
        if old_h in t:
            t = t.replace(old_h, new_h, 1)
            print("patched handle gate")
            break

    replacements = [
        ("if (!isBackground() || !isConfigured()) return;",
         "if (!canAutoReplyNow() || !isConfigured()) return;"),
        ("while (rounds++ < 8 && isBackground() && isConfigured()) {",
         "while (rounds++ < 8 && canAutoReplyNow() && isConfigured()) {"),
        ("if (!isBackground() || !isConfigured()) break;",
         "if (!canAutoReplyNow() || !isConfigured()) break;"),
        ("if (isBackground() && isConfigured()) {",
         "if (canAutoReplyNow() && isConfigured()) {"),
        ("if (isBackground() && isConfigured()) failed[0] = true;",
         "if (canAutoReplyNow() && isConfigured()) failed[0] = true;"),
        ("if (!failed[0] && isBackground() && isConfigured() && hasUnanswered(account, dialogId)) {",
         "if (!failed[0] && canAutoReplyNow() && isConfigured() && hasUnanswered(account, dialogId)) {"),
        ("if (!isBackground() || !isConfigured()) return null;",
         "if (!canAutoReplyNow() || !isConfigured()) return null;"),
        ("if (attempt < 2 && isBackground() && isConfigured()) {",
         "if (attempt < 2 && canAutoReplyNow() && isConfigured()) {"),
    ]
    for old, new in replacements:
        if old in t:
            t = t.replace(old, new)

    old_call = """                    String answer = TelegaAIAgent.completeWithSystemPrompt(
                            TelegaSettings.getAiBaseUrl(), TelegaSettings.getAiApiKey(),
                            TelegaSettings.getAiModel(), systemPrompt, context);
                    if (TextUtils.isEmpty(answer)) {
                        failed[0] = true;
                        break;
                    }"""
    new_call = """                    String answer;
                    try {
                        answer = TelegaAIAgent.completeWithSystemPrompt(
                                TelegaSettings.getAiBaseUrl(), TelegaSettings.getAiApiKey(),
                                TelegaSettings.getAiModel(), systemPrompt, context);
                    } catch (Throwable apiError) {
                        FileLog.e(apiError);
                        failed[0] = true;
                        break;
                    }
                    if (TextUtils.isEmpty(answer)) {
                        FileLog.e("TelegaAutoReply empty AI response");
                        failed[0] = true;
                        break;
                    }"""
    if old_call in t:
        t = t.replace(old_call, new_call, 1)
        print("patched AI call")

    path.write_text(t, encoding="utf-8")
    print("AutoReply done")


def patch_bypass():
    path = Path("TMessagesProj/src/main/java/org/telegram/messenger/TelegaLocalBypassProxy.java")
    if not path.exists():
        print("Bypass missing"); return
    t = path.read_text(encoding="utf-8")
    if "persistLocalProxy" in t:
        print("Bypass already patched"); return

    helpers = """
    public static boolean isLocalEndpoint(String address, int port) {
        if (address == null) {
            return false;
        }
        String host = address.trim();
        return isRunning()
                && port == listenPort
                && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host));
    }

    public static int ensureRunning() {
        if (!isEnabled()) {
            return 0;
        }
        if (isRunning()) {
            return listenPort;
        }
        return start();
    }

"""
    marker = "    public static boolean setEnabled(boolean enabled) {"
    if marker in t and "isLocalEndpoint" not in t:
        t = t.replace(marker, helpers + marker, 1)

    old = """    public static boolean setEnabled(boolean enabled) {
        TelegaSettings.setLocalBypassEnabled(enabled);
        if (!enabled) {
            stop();
            restoreConfiguredProxy();
            return true;
        }

        int port = start();
        if (port <= 0) {
            TelegaSettings.setLocalBypassEnabled(false);
            restoreConfiguredProxy();
            return false;
        }
        try {
            ConnectionsManager.setProxySettings(true, createLocalSettings(port));
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Unable to apply local proxy settings", error);
            lastError = safeMessage(error);
            stop();
            TelegaSettings.setLocalBypassEnabled(false);
            restoreConfiguredProxy();
            return false;
        }
    }"""
    new = """    public static boolean setEnabled(boolean enabled) {
        TelegaSettings.setLocalBypassEnabled(enabled);
        if (!enabled) {
            stop();
            restoreConfiguredProxy();
            return true;
        }

        int port = start();
        if (port <= 0) {
            TelegaSettings.setLocalBypassEnabled(false);
            restoreConfiguredProxy();
            return false;
        }
        try {
            persistLocalProxy(port);
            ConnectionsManager.setProxySettings(true, createLocalSettings(port));
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                try {
                    ConnectionsManager.getInstance(a).checkConnection();
                } catch (Throwable ignore) {
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

    private static void persistLocalProxy(int port) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                    "mainconfig", Activity.MODE_PRIVATE);
            preferences.edit()
                    .putBoolean("proxy_enabled", true)
                    .putString("proxy_ip", LOOPBACK)
                    .putInt("proxy_port", port)
                    .putInt("proxy_type", ProxySettings.typeToInt(ProxySettings.Type.SOCKS5))
                    .remove("proxy_secret")
                    .remove("proxy_user")
                    .remove("proxy_pass")
                    .apply();
        } catch (Throwable error) {
            Log.e(TAG, "Unable to persist local proxy prefs", error);
        }
    }"""
    if old in t:
        t = t.replace(old, new, 1)
        print("patched setEnabled")
    else:
        print("WARN setEnabled")

    path.write_text(t, encoding="utf-8")
    print("Bypass done")


def patch_check_proxy():
    path = Path("TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java")
    if not path.exists():
        print("ConnectionsManager missing"); return
    t = path.read_text(encoding="utf-8")
    # Repo already routes loopback checkProxy through the live local SOCKS port.
    if "isLoopbackProxyAddress" in t or "TelegaLocalBypassProxy.ensureRunning" in t:
        print("checkProxy already has loopback handling")
        return

    old = """    public long checkProxy(ProxySettings settings, RequestTimeDelegate requestTimeDelegate) {
        if (settings == null || !settings.isValid()) {
            return 0;
        }
        if (settings.getType() == ProxySettings.Type.WEB) {
            WebProxyConnectionTester.getInstance().checkProxy(settings, requestTimeDelegate, this::checkWebProxyInternal);
            return 0;
        }

        return native_checkProxy(currentAccount, settings.getAddress(), settings.getPort(), settings.getUser(), settings.getPassword(), settings.getSecret(), requestTimeDelegate);
    }"""
    new = """    public long checkProxy(ProxySettings settings, RequestTimeDelegate requestTimeDelegate) {
        if (settings == null || !settings.isValid()) {
            return 0;
        }
        if (settings.getType() == ProxySettings.Type.WEB) {
            WebProxyConnectionTester.getInstance().checkProxy(settings, requestTimeDelegate, this::checkWebProxyInternal);
            return 0;
        }

        try {
            if (org.telegram.messenger.TelegaLocalBypassProxy.isEnabled()) {
                int port = org.telegram.messenger.TelegaLocalBypassProxy.start();
                if (port > 0 && ("127.0.0.1".equals(settings.getAddress())
                        || "localhost".equalsIgnoreCase(settings.getAddress()))) {
                    return native_checkProxy(currentAccount, "127.0.0.1", port, "", "", "", requestTimeDelegate);
                }
            }
        } catch (Throwable ignore) {
        }

        return native_checkProxy(currentAccount, settings.getAddress(), settings.getPort(), settings.getUser(), settings.getPassword(), settings.getSecret(), requestTimeDelegate);
    }"""
    if old in t:
        t = t.replace(old, new, 1)
        path.write_text(t, encoding="utf-8")
        print("patched checkProxy")
    else:
        print("WARN checkProxy anchor missing")


if __name__ == "__main__":
    patch_auto_reply()
    patch_bypass()
    patch_check_proxy()
    print("All bugfixes applied")
