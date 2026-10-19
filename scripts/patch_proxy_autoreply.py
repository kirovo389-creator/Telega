#!/usr/bin/env python3
"""Fix local bypass SharedConfig/mainconfig persist and auto-reply reliability."""
from pathlib import Path
import re

def patch_bypass():
    path = Path("TMessagesProj/src/main/java/org/telegram/messenger/TelegaLocalBypassProxy.java")
    t = path.read_text(encoding="utf-8")
    if 'putBoolean("proxy_enabled"' in t and "SharedConfig.currentProxy" in t:
        print("bypass persist already strong")
    else:
        old = '''    private static void persistLocalProxy(int port) {
        if (ApplicationLoader.applicationContext == null) return;
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(
                    "telega_settings", Activity.MODE_PRIVATE)
                    .edit().putInt("local_bypass_port", port).apply();
        } catch (Throwable error) {
            Log.w(TAG, "Unable to persist local proxy port", error);
        }
    }'''
        new = '''    private static void persistLocalProxy(int port) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(
                    "telega_settings", Activity.MODE_PRIVATE)
                    .edit().putInt("local_bypass_port", port).apply();

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

            ProxySettings local = createLocalSettings(port);
            try {
                SharedConfig.loadProxyList();
                SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo(local));
                SharedConfig.currentProxy = info;
                info.available = true;
                info.ping = 1;
            } catch (Throwable error) {
                Log.w(TAG, "Unable to update SharedConfig proxy list", error);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to persist local proxy settings", error);
        }
    }'''
        if old in t:
            t = t.replace(old, new, 1)
            print("patched persistLocalProxy")
        else:
            print("WARN persistLocalProxy")

    old_set = '''        int port = ensureRunning();
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
            return true;'''
    new_set = '''        stop();
        TelegaSettings.setLocalBypassEnabled(true);
        int port = start();
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
                    ConnectionsManager cm = ConnectionsManager.getInstance(account);
                    cm.checkConnection();
                    cm.setAppPaused(false, false);
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to refresh network after enabling local bypass", error);
                }
            }
            return true;'''
    if old_set in t:
        t = t.replace(old_set, new_set, 1)
        print("patched setEnabled")
    else:
        print("WARN setEnabled")

    path.write_text(t, encoding="utf-8")
    print("bypass done")


def patch_auto_reply():
    path = Path("TMessagesProj/src/main/java/org/telegram/messenger/TelegaAutoReplyAgent.java")
    t = path.read_text(encoding="utf-8")

    new_can = '''    private static boolean canAutoReplyNow() {
        // Prefer answering whenever the UI is not actively in the foreground.
        // ForegroundDetector alone is unreliable around push wake-ups.
        if (!isForegroundNow()) {
            return true;
        }
        return isBackground();
    }'''
    t2, n = re.subn(
        r"    private static boolean canAutoReplyNow\(\) \{.*?\n    \}",
        new_can,
        t,
        count=1,
        flags=re.S,
    )
    if n:
        t = t2
        print("patched canAutoReplyNow")

    old_gate = '''            if (!canAutoReplyNow()) continue;
            String body = message.messageOwner.message;
            if (TextUtils.isEmpty(body)) continue;

            if (TelegaSettings.isOfflineAutoReplyEnabled()) {
                startOfflineReplyWorker(account, dialogId);
                continue;
            }
            if (!isConfigured()) continue;
            appendRecord(account, dialogId, message.getId(), message.messageOwner.date, body, false, false);
            startReplyWorker(account, dialogId);'''
    new_gate = '''            String body = message.messageOwner.message;
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
            }'''
    if old_gate in t:
        t = t.replace(old_gate, new_gate, 1)
        print("patched capture gate")
    else:
        print("WARN capture gate")

    old_bg = '''        @Override
        public void onBecameBackground() {
            // Incoming updates trigger the reply worker.
        }'''
    new_bg = '''        @Override
        public void onBecameBackground() {
            for (Integer account : OBSERVERS.keySet()) {
                flushPendingReplies(account);
            }
        }'''
    if old_bg in t:
        t = t.replace(old_bg, new_bg, 1)
        print("patched background listener")

    if "flushPendingReplies" not in t:
        helper = '''
    private static void flushPendingReplies(int account) {
        if (!canAutoReplyNow()) return;
        SharedPreferences p = prefs();
        if (p == null) return;
        for (String key : p.getAll().keySet()) {
            if (!key.startsWith(CONVERSATION_PREFIX + account + "_")) continue;
            try {
                long dialogId = Long.parseLong(key.substring((CONVERSATION_PREFIX + account + "_").length()));
                if (dialogId <= 0) continue;
                if (TelegaSettings.isOfflineAutoReplyEnabled()) {
                    startOfflineReplyWorker(account, dialogId);
                } else if (isConfigured()) {
                    startReplyWorker(account, dialogId);
                }
            } catch (Throwable ignore) {
            }
        }
    }

'''
        marker = "    private static void startReplyWorker"
        if marker in t:
            t = t.replace(marker, helper + marker, 1)
            print("added flushPendingReplies")

    path.write_text(t, encoding="utf-8")
    print("auto reply done")


def patch_avatar_color():
    path = Path("TMessagesProj/src/main/java/org/telegram/ui/Components/AvatarDrawable.java")
    t = path.read_text(encoding="utf-8")
    if "TelegaProfileColor" in t:
        print("AvatarDrawable already hooked")
        return
    snippet = '''
        try {
            int telegaColor = org.telegram.messenger.TelegaProfileColor.getColor(
                    org.telegram.messenger.UserConfig.selectedAccount, id);
            if (telegaColor != 0) {
                color = color2 = telegaColor;
            }
        } catch (Throwable ignore) {
        }
'''
    # After default id-based color assignment in the main setInfo overload
    old = '''                color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
            }
        }
        needApplyColorAccent = id == 5; // Tinting manually set blue color'''
    new = '''                color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
            }
        }
''' + snippet + '''        needApplyColorAccent = id == 5; // Tinting manually set blue color'''
    if old in t:
        t = t.replace(old, new, 1)
        print("patched AvatarDrawable setInfo")
    else:
        print("WARN AvatarDrawable anchor")

    old2 = '''        color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
        color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;
        getAvatarSymbols("", "", "", stringBuilder);'''
    new2 = '''        color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
        color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
''' + snippet + '''        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;
        getAvatarSymbols("", "", "", stringBuilder);'''
    if old2 in t:
        t = t.replace(old2, new2, 1)
        print("patched AvatarDrawable setInfo(id)")

    path.write_text(t, encoding="utf-8")
    print("AvatarDrawable done")


if __name__ == "__main__":
    patch_bypass()
    patch_auto_reply()
    patch_avatar_color()
    print("All runtime fixes applied")
