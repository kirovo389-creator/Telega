package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local-only profile accent color for the current account's avatar in Telega.
 * Does not upload anything to Telegram servers.
 */
public final class TelegaProfileColor {
    private static final String PREFS = "telega_profile_color";

    private TelegaProfileColor() {
    }

    private static SharedPreferences prefs() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int account, long userId) {
        return "color_" + account + "_" + userId;
    }

    /** @return ARGB color or 0 when not set */
    public static int getColor(int account, long userId) {
        SharedPreferences p = prefs();
        if (p == null || userId == 0) {
            return 0;
        }
        return p.getInt(key(account, userId), 0);
    }

    public static boolean hasColor(int account, long userId) {
        return getColor(account, userId) != 0;
    }

    public static void setColor(int account, long userId, int color) {
        SharedPreferences p = prefs();
        if (p == null || userId == 0) {
            return;
        }
        if (color == 0) {
            p.edit().remove(key(account, userId)).apply();
        } else {
            p.edit().putInt(key(account, userId), color | 0xFF000000).apply();
        }
    }

    public static void clear(int account, long userId) {
        setColor(account, userId, 0);
    }
}
