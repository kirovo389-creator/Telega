package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores previous text versions of edited messages locally.
 * Versions are captured only after Telega is installed (Telegram API does not send full history).
 */
public final class TelegaEditedMessages {
    private static final String PREFS = "telega_edited_messages";
    private static final Set<String> expandedKeys = new HashSet<>();

    private TelegaEditedMessages() {
    }

    private static SharedPreferences preferences(int account) {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(
                PREFS + "_" + account, Context.MODE_PRIVATE);
    }

    private static String key(long dialogId, int messageId) {
        return dialogId + "_" + messageId;
    }

    /** Append previous text before it is replaced by a newer edit. */
    public static void addPreviousVersion(int account, long dialogId, int messageId, String previousText) {
        if (!TelegaSettings.isShowEditedMessages()) {
            return;
        }
        if (TextUtils.isEmpty(previousText) || messageId == 0) {
            return;
        }
        SharedPreferences prefs = preferences(account);
        if (prefs == null) {
            return;
        }
        String k = key(dialogId, messageId);
        try {
            JSONArray arr;
            String raw = prefs.getString(k, null);
            if (raw == null) {
                arr = new JSONArray();
            } else {
                arr = new JSONArray(raw);
            }
            // avoid consecutive duplicates
            if (arr.length() > 0) {
                String last = arr.getString(arr.length() - 1);
                if (previousText.equals(last)) {
                    return;
                }
            }
            arr.put(previousText);
            // keep last 20 versions
            if (arr.length() > 20) {
                JSONArray trimmed = new JSONArray();
                for (int i = arr.length() - 20; i < arr.length(); i++) {
                    trimmed.put(arr.getString(i));
                }
                arr = trimmed;
            }
            prefs.edit().putString(k, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<String> getHistory(int account, long dialogId, int messageId) {
        ArrayList<String> out = new ArrayList<>();
        SharedPreferences prefs = preferences(account);
        if (prefs == null || messageId == 0) {
            return out;
        }
        String raw = prefs.getString(key(dialogId, messageId), null);
        if (raw == null) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getString(i));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean hasHistory(int account, long dialogId, int messageId) {
        return !getHistory(account, dialogId, messageId).isEmpty();
    }

    public static boolean isExpanded(long dialogId, int messageId) {
        return expandedKeys.contains(key(dialogId, messageId));
    }

    public static void setExpanded(long dialogId, int messageId, boolean expanded) {
        String k = key(dialogId, messageId);
        if (expanded) {
            expandedKeys.add(k);
        } else {
            expandedKeys.remove(k);
        }
    }

    public static void toggleExpanded(long dialogId, int messageId) {
        setExpanded(dialogId, messageId, !isExpanded(dialogId, messageId));
    }
}
