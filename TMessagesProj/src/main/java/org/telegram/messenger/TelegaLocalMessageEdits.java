package org.telegram.messenger;

import android.content.Context;

/**
 * Per-device visual overrides for incoming text messages.
 * These values are never sent to Telegram or to other participants.
 */
public final class TelegaLocalMessageEdits {
    private static final String PREFS = "telega_local_message_edits";
    private static volatile int editingAccount = -1;
    private static volatile long editingDialogId;
    private static volatile int editingMessageId;

    public static void beginLocalEdit(int account, long dialogId, int messageId) {
        editingAccount = account;
        editingDialogId = dialogId;
        editingMessageId = messageId;
    }

    public static boolean isLocalEdit(int account, long dialogId, int messageId) {
        return editingAccount == account && editingDialogId == dialogId && editingMessageId == messageId;
    }

    public static void finishLocalEdit() {
        editingAccount = -1;
        editingDialogId = 0;
        editingMessageId = 0;
    }

    private TelegaLocalMessageEdits() {
    }

    private static android.content.SharedPreferences preferences(int account) {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS + "_" + account, Context.MODE_PRIVATE);
    }

    private static String key(long dialogId, int messageId) {
        return dialogId + "_" + messageId;
    }

    public static boolean hasOverride(int account, long dialogId, int messageId) {
        android.content.SharedPreferences p = preferences(account);
        return p != null && messageId > 0 && p.contains(key(dialogId, messageId));
    }

    public static String getOverride(int account, long dialogId, int messageId) {
        android.content.SharedPreferences p = preferences(account);
        return p == null || messageId <= 0 ? null : p.getString(key(dialogId, messageId), null);
    }

    public static void saveOverride(int account, long dialogId, int messageId, String text) {
        android.content.SharedPreferences p = preferences(account);
        if (p == null || messageId <= 0) {
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            p.edit().remove(key(dialogId, messageId)).apply();
            return;
        }
        String safeText = text.length() > 16000 ? text.substring(0, 16000) : text;
        p.edit().putString(key(dialogId, messageId), safeText).apply();
    }

    public static void clearOverride(int account, long dialogId, int messageId) {
        android.content.SharedPreferences p = preferences(account);
        if (p != null && messageId > 0) {
            p.edit().remove(key(dialogId, messageId)).apply();
        }
    }

    /** Returns null when no local override should be applied to the message layout. */
    public static String getTextForLayout(MessageObject message) {
        if (!TelegaSettings.isLocalEditMessagesEnabled() || message == null || message.messageOwner == null) {
            return null;
        }
        return getOverride(message.currentAccount, message.getDialogId(), message.getId());
    }


}