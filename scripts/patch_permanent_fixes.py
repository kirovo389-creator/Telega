#!/usr/bin/env python3
"""Permanent runtime fixes applied every build (proxy SharedConfig + avatar color)."""
from pathlib import Path

def patch_bypass():
    path = Path("TMessagesProj/src/main/java/org/telegram/messenger/TelegaLocalBypassProxy.java")
    t = path.read_text(encoding="utf-8")
    changed = False

    if "SharedConfig.currentProxy" not in t:
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
                info.checking = false;
                info.ping = Math.max(1, info.ping);
            } catch (Throwable error) {
                Log.w(TAG, "Unable to update SharedConfig proxy list", error);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to persist local proxy settings", error);
        }
    }'''
        if old in t:
            t = t.replace(old, new, 1)
            changed = True
            print("persistLocalProxy strengthened")
        else:
            print("WARN persistLocalProxy")

    # Full replace when still on the original setEnabled body.
    if "proxySettingsChanged" not in t:
        old_full = '''        int port = ensureRunning();
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
    }'''
        new_full = '''        stop();
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
            try {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                if (SharedConfig.currentProxy != null) {
                    SharedConfig.currentProxy.available = true;
                    SharedConfig.currentProxy.checking = false;
                    SharedConfig.currentProxy.ping = 1;
                    NotificationCenter.getGlobalInstance().postNotificationName(
                            NotificationCenter.proxyCheckDone, SharedConfig.currentProxy);
                }
            } catch (Throwable ignore) {
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
    }'''
        if old_full in t:
            t = t.replace(old_full, new_full, 1)
            changed = True
            print("setEnabled strengthened (full)")
        else:
            # Partial path: patch_proxy_autoreply already rewrote setEnabled without notifications.
            inject_old = '''                    cm.setAppPaused(false, false);
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to refresh network after enabling local bypass", error);
                }
            }
            return true;'''
            inject_new = '''                    cm.setAppPaused(false, false);
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to refresh network after enabling local bypass", error);
                }
            }
            try {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                if (SharedConfig.currentProxy != null) {
                    SharedConfig.currentProxy.available = true;
                    SharedConfig.currentProxy.checking = false;
                    SharedConfig.currentProxy.ping = 1;
                    NotificationCenter.getGlobalInstance().postNotificationName(
                            NotificationCenter.proxyCheckDone, SharedConfig.currentProxy);
                }
            } catch (Throwable ignore) {
            }
            return true;'''
            if inject_old in t:
                t = t.replace(inject_old, inject_new, 1)
                changed = True
                print("setEnabled notifications injected")
            else:
                # Last resort: insert before the success return of setEnabled after setProxySettings
                inject_old2 = '''            ConnectionsManager.setProxySettings(true, createLocalSettings(port));
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                try {
                    ConnectionsManager.getInstance(account).checkConnection();
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to refresh network after enabling local bypass", error);
                }
            }
            return true;'''
                inject_new2 = '''            ConnectionsManager.setProxySettings(true, createLocalSettings(port));
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                try {
                    ConnectionsManager.getInstance(account).checkConnection();
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to refresh network after enabling local bypass", error);
                }
            }
            try {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                if (SharedConfig.currentProxy != null) {
                    SharedConfig.currentProxy.available = true;
                    SharedConfig.currentProxy.checking = false;
                    SharedConfig.currentProxy.ping = 1;
                    NotificationCenter.getGlobalInstance().postNotificationName(
                            NotificationCenter.proxyCheckDone, SharedConfig.currentProxy);
                }
            } catch (Throwable ignore) {
            }
            return true;'''
                if inject_old2 in t:
                    t = t.replace(inject_old2, inject_new2, 1)
                    changed = True
                    print("setEnabled notifications injected (v2)")
                else:
                    print("WARN setEnabled notifications")

    if changed:
        path.write_text(t, encoding="utf-8")
    print("bypass done")


def patch_avatar():
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
                hasGradient = false;
                hasAdvancedGradient = false;
            }
        } catch (Throwable ignore) {
        }
'''
    old = '''        needApplyColorAccent = id == 5; // Tinting manually set blue color


        avatarType = AVATAR_TYPE_NORMAL;'''
    new = '''        needApplyColorAccent = id == 5; // Tinting manually set blue color
''' + snippet + '''
        avatarType = AVATAR_TYPE_NORMAL;'''
    if old not in t:
        old = '''        needApplyColorAccent = id == 5; // Tinting manually set blue color

        avatarType = AVATAR_TYPE_NORMAL;'''
        new = '''        needApplyColorAccent = id == 5; // Tinting manually set blue color
''' + snippet + '''
        avatarType = AVATAR_TYPE_NORMAL;'''
    if old in t:
        t = t.replace(old, new, 1)
        print("AvatarDrawable setInfo hooked")
    else:
        print("WARN AvatarDrawable setInfo")

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
        print("AvatarDrawable setInfo(id) hooked")

    path.write_text(t, encoding="utf-8")
    print("AvatarDrawable done")


if __name__ == "__main__":
    patch_bypass()
    patch_avatar()
    print("permanent fixes applied")
