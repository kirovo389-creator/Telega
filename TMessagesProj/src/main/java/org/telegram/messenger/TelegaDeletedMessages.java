package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Keeps a local tombstone for messages deleted by another participant.
 * The original message remains in Telegram's local message storage so the
 * chat can display its content with a deleted marker instead of removing it.
 */
public final class TelegaDeletedMessages {
    private static final String PREFS = "telega_deleted_messages";

    private TelegaDeletedMessages() {
    }

    private static SharedPreferences preferences(int account) {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(
                PREFS + "_" + account, Context.MODE_PRIVATE);
    }

    private static String key(long dialogId, int messageId) {
        return dialogId == 0 ? "id_" + messageId : "dialog_" + dialogId + "_" + messageId;
    }

    public static void markDeleted(int account, long dialogId, int messageId) {
        SharedPreferences prefs = preferences(account);
        if (prefs == null || messageId == 0) {
            return;
        }
        prefs.edit().putBoolean(key(dialogId, messageId), true).apply();
    }

    public static boolean isDeleted(int account, long dialogId, int messageId) {
        SharedPreferences prefs = preferences(account);
        if (prefs == null || messageId == 0) {
            return false;
        }
        return prefs.getBoolean(key(dialogId, messageId), false)
                || (dialogId != 0 && prefs.getBoolean(key(0, messageId), false));
    }
}
