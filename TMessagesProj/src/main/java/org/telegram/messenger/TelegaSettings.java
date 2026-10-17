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

    // ---------------------------------------------------------------- AI agent

    public static String getAiBaseUrl() {
        SharedPreferences p = prefs();
        return p == null ? "" : p.getString("ai_base_url", "");
    }

    public static String getAiApiKey() {
        SharedPreferences p = prefs();
        return p == null ? "" : p.getString("ai_api_key", "");
    }

    public static String getAiModel() {
        SharedPreferences p = prefs();
        return p == null ? "" : p.getString("ai_model", "");
    }

    /** True after the user filled base url / api key / model id and the test request succeeded. */
    public static boolean isAiConnected() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean("ai_connected", false);
    }

    /** On/off switch of the agent. ON by default, so it is ON right after connecting. */
    public static boolean isAiEnabled() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean("ai_enabled", true);
    }

    public static void setAiEnabled(boolean value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("ai_enabled", value).apply();
        }
    }

    public static void saveAiConnection(String baseUrl, String apiKey, String model) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit()
                    .putString("ai_base_url", baseUrl)
                    .putString("ai_api_key", apiKey)
                    .putString("ai_model", model)
                    .putBoolean("ai_connected", true)
                    .putBoolean("ai_enabled", true)
                    .apply();
        }
    }

    /** Forget the connection state; saved fields stay so the form is pre-filled. */
    public static void clearAiConnection() {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("ai_connected", false).apply();
        }
    }
}
