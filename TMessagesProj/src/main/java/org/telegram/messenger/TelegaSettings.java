package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/** Feature toggles for Telega-specific behaviour. */
public final class TelegaSettings {
    private static final String PREFS = "telega_settings";

    /** Snow modes of the chat background. */
    public static final int SNOW_OFF = 0;
    public static final int SNOW_MINECRAFT = 1;
    public static final int SNOW_REAL = 2;
    public static final int SNOW_RAIN = 3;

    private static int snowModeCache = -1;
    private static int garlandCache = -1;
    private static int glassMessagesCache = -1;
    private static int glassAnimationCache = -1;
    private static int avatarFrameCache = -1;

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

    // ------------------------------------------------------------ customization

    /** 0 = off, 1 = Minecraft snow, 2 = real snow (tilt + shake blizzard). Cached: read on every frame. */
    public static int getSnowMode() {
        if (snowModeCache < 0) {
            SharedPreferences p = prefs();
            if (p == null) {
                return SNOW_OFF;
            }
            snowModeCache = p.getInt("snow_mode", SNOW_OFF);
        }
        return snowModeCache;
    }

    public static void setSnowMode(int mode) {
        snowModeCache = mode;
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putInt("snow_mode", mode).apply();
        }
    }

    /** Garland on the top edge of every message bubble. Cached: read on every draw. */
    public static boolean isGarlandEnabled() {
        if (garlandCache < 0) {
            SharedPreferences p = prefs();
            if (p == null) {
                return false;
            }
            garlandCache = p.getBoolean("garland", false) ? 1 : 0;
        }
        return garlandCache == 1;
    }

    public static void setGarlandEnabled(boolean value) {
        garlandCache = value ? 1 : 0;
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("garland", value).apply();
        }
    }

    // --------------------------------------------------------- Glass messages

    /** Enables translucent glass-style message bubbles. */
    public static boolean isGlassMessagesEnabled() {
        if (glassMessagesCache < 0) {
            SharedPreferences p = prefs();
            if (p == null) {
                return false;
            }
            glassMessagesCache = p.getBoolean("glass_messages_enabled", false) ? 1 : 0;
        }
        return glassMessagesCache == 1;
    }

    public static void setGlassMessagesEnabled(boolean enabled) {
        glassMessagesCache = enabled ? 1 : 0;
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("glass_messages_enabled", enabled).apply();
        }
        TelegaGlassMessages.refreshAnimationState();
    }

    /** The moving highlight can be toggled independently from the glass surface. */
    public static boolean isGlassMessagesAnimationEnabled() {
        if (glassAnimationCache < 0) {
            SharedPreferences p = prefs();
            if (p == null) {
                return true;
            }
            glassAnimationCache = p.getBoolean("glass_messages_animation", true) ? 1 : 0;
        }
        return glassAnimationCache == 1;
    }

    public static void setGlassMessagesAnimationEnabled(boolean enabled) {
        glassAnimationCache = enabled ? 1 : 0;
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("glass_messages_animation", enabled).apply();
        }
        TelegaGlassMessages.refreshAnimationState();
    }

    // ----------------------------------------------------------- Video wallpaper

    public static String getChatVideoWallpaperUri() {
        SharedPreferences p = prefs();
        return p == null ? "" : p.getString("chat_video_wallpaper_uri", "");
    }

    public static void setChatVideoWallpaperUri(String uri) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putString("chat_video_wallpaper_uri", uri == null ? "" : uri).apply();
        }
    }

    // ------------------------------------------------------------- Avatar frame

    public static boolean isAvatarFrameEnabled() {
        if (avatarFrameCache < 0) {
            SharedPreferences p = prefs();
            if (p == null) {
                return false;
            }
            avatarFrameCache = p.getBoolean("avatar_frame_enabled", false) ? 1 : 0;
        }
        return avatarFrameCache == 1;
    }

    public static void setAvatarFrameEnabled(boolean enabled) {
        avatarFrameCache = enabled ? 1 : 0;
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("avatar_frame_enabled", enabled).apply();
        }
    }

    // ------------------------------------------------------ Profile and stories

    public static boolean isStoriesDisabled() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean("stories_disabled", false);
    }

    public static void setStoriesDisabled(boolean disabled) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("stories_disabled", disabled).apply();
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                try {
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.storiesUpdated);
                } catch (Throwable error) {
                    FileLog.e(error);
                }
            }
        });
    }

    // ------------------------------------------------------ Local bypass proxy

    /** Enables the in-process local SOCKS5/WSS route while Telega is running. */
    public static boolean isLocalBypassEnabled() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean("local_bypass_enabled", false);
    }

    public static void setLocalBypassEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean("local_bypass_enabled", enabled).apply();
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

    // -------------------------------------------------------- AI auto-responder

    public static String getAiAutoReplyPrompt() {
        SharedPreferences p = prefs();
        return p == null ? "" : p.getString("ai_auto_reply_prompt", "");
    }

    public static void setAiAutoReplyPrompt(String prompt) {
        SharedPreferences p = prefs();
        if (p != null) {
            String value = prompt == null ? "" : prompt.trim();
            SharedPreferences.Editor editor = p.edit().putString("ai_auto_reply_prompt", value);
            if (!value.isEmpty()) {
                editor.putBoolean("ai_auto_reply_enabled", true);
            }
            editor.apply();
        }
    }

    public static boolean isAiAutoReplyEnabled() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean("ai_auto_reply_enabled", false)
                && !getAiAutoReplyPrompt().trim().isEmpty() && isAiConnected();
    }

    public static void setAiAutoReplyEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) {
            boolean valid = enabled && isAiConnected() && !getAiAutoReplyPrompt().trim().isEmpty();
            p.edit().putBoolean("ai_auto_reply_enabled", valid).apply();
        }
    }

    /** Fixed-message auto responder, independent from the optional AI connection. */
    public static String getOfflineAutoReplyMessage() {
        SharedPreferences p = prefs();
        return p == null ? "" : p.getString("offline_auto_reply_message", "");
    }

    public static void setOfflineAutoReplyMessage(String message) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putString("offline_auto_reply_message", message == null ? "" : message.trim()).apply();
    }

    public static boolean isOfflineAutoReplyEnabled() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean("offline_auto_reply_enabled", false)
                && !getOfflineAutoReplyMessage().trim().isEmpty();
    }

    public static void setOfflineAutoReplyEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) {
            boolean valid = enabled && !getOfflineAutoReplyMessage().trim().isEmpty();
            p.edit().putBoolean("offline_auto_reply_enabled", valid).apply();
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
