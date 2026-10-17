package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/** Feature toggles for Telega-specific behaviour. */
public final class TelegaSettings {
    private static final String PREFS = "telega_settings";

    private TelegaSettings() {
    }

    private static SharedPreferences prefs() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isShowDeletedMessages() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean("show_deleted_messages", true);
    }

    public static void setShowDeletedMessages(boolean value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("show_deleted_messages", value).apply();
        }
    }

    public static boolean isShowEditedMessages() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean("show_edited_messages", true);
    }

    public static void setShowEditedMessages(boolean value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("show_edited_messages", value).apply();
        }
    }
}
