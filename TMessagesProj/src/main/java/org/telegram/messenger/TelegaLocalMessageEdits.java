package org.telegram.messenger;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;

/**
 * Per-device visual overrides for incoming text messages.
 * These values are never sent to Telegram or to other participants.
 */
public final class TelegaLocalMessageEdits {
    private static final String PREFS = "telega_local_message_edits";

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

    public static void showEditor(Context context, int account, long dialogId, int messageId, String original, Runnable onChanged) {
        if (context == null || messageId <= 0) {
            return;
        }
        EditText input = new EditText(context);
        input.setText(original == null ? "" : original);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(2);
        input.setMaxLines(10);
        input.setTextSize(16);
        int padding = AndroidUtilities.dp(14);
        input.setPadding(padding, padding, padding, padding);
        input.setSelection(input.length());
        input.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        input.setHintTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextHint));
        input.setHint("Сообщение");
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        final org.telegram.ui.ActionBar.AlertDialog dialog = new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                .setTitle("Изменить сообщение")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setNeutralButton("Сбросить", (d, which) -> {
                    clearOverride(account, dialogId, messageId);
                    if (onChanged != null) onChanged.run();
                })
                .setPositiveButton("Готово", (d, which) -> {
                    saveOverride(account, dialogId, messageId, input.getText().toString());
                    if (onChanged != null) onChanged.run();
                })
                .create();
        dialog.setOnShowListener(d -> {
            int color = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack);
            if (dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE) != null) {
                ((android.widget.TextView) dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)).setTextColor(color);
            }
            if (dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_NEGATIVE) != null) {
                ((android.widget.TextView) dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)).setTextColor(color);
            }
            if (dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_NEUTRAL) != null) {
                ((android.widget.TextView) dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)).setTextColor(color);
            }
        });
        dialog.show();
    }
}