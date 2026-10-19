package org.telegram.messenger;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.util.LruCache;

/**
 * Per-device visual overrides for incoming text messages.
 * These values are never sent to Telegram or to other participants.
 */
public final class TelegaLocalMessageEdits {
    private static final String PREFS = "telega_local_message_edits";
    private static final String NO_OVERRIDE = "\u0000";
    private static final LruCache<String, String> OVERRIDE_CACHE = new LruCache<>(512);

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

    private static String cacheKey(int account, long dialogId, int messageId) {
        return account + "_" + key(dialogId, messageId);
    }

    public static String getOverride(int account, long dialogId, int messageId) {
        if (messageId <= 0) {
            return null;
        }
        String memoryKey = cacheKey(account, dialogId, messageId);
        String cached = OVERRIDE_CACHE.get(memoryKey);
        if (cached != null) {
            return NO_OVERRIDE.equals(cached) ? null : cached;
        }
        android.content.SharedPreferences p = preferences(account);
        if (p == null) {
            return null;
        }
        String value = p.getString(key(dialogId, messageId), null);
        OVERRIDE_CACHE.put(memoryKey, value == null ? NO_OVERRIDE : value);
        return value;
    }

    public static void saveOverride(int account, long dialogId, int messageId, String text) {
        android.content.SharedPreferences p = preferences(account);
        if (p == null || messageId <= 0) {
            return;
        }
        String preferenceKey = key(dialogId, messageId);
        String memoryKey = cacheKey(account, dialogId, messageId);
        if (text == null || text.trim().isEmpty()) {
            p.edit().remove(preferenceKey).apply();
            OVERRIDE_CACHE.put(memoryKey, NO_OVERRIDE);
            return;
        }
        String safeText = text.length() > 16000 ? text.substring(0, 16000) : text;
        p.edit().putString(preferenceKey, safeText).apply();
        OVERRIDE_CACHE.put(memoryKey, safeText);
    }

    public static void clearOverride(int account, long dialogId, int messageId) {
        android.content.SharedPreferences p = preferences(account);
        if (p != null && messageId > 0) {
            p.edit().remove(key(dialogId, messageId)).apply();
            OVERRIDE_CACHE.put(cacheKey(account, dialogId, messageId), NO_OVERRIDE);
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

        final android.app.Dialog dialog = new android.app.Dialog(context);
        final android.widget.LinearLayout root = new android.widget.LinearLayout(context);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14), AndroidUtilities.dp(18), AndroidUtilities.dp(16));
        android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
        panel.setColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));
        float radius = AndroidUtilities.dp(22);
        panel.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        root.setBackground(panel);
        root.setElevation(AndroidUtilities.dp(12));

        android.widget.LinearLayout header = new android.widget.LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.TextView title = new android.widget.TextView(context);
        title.setText("Изменить для себя");
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText));
        header.addView(title, new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.TextView close = new android.widget.TextView(context);
        close.setText("×");
        close.setTextSize(28);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText2));
        header.addView(close, new android.widget.LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(36)));
        root.addView(header, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(context);
        input.setText(original == null ? "" : original);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(2);
        input.setMaxLines(8);
        input.setTextSize(16);
        input.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        input.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText2));
        input.setHint("Сообщение");
        android.graphics.drawable.GradientDrawable inputBackground = new android.graphics.drawable.GradientDrawable();
        inputBackground.setColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundGray));
        inputBackground.setCornerRadius(AndroidUtilities.dp(14));
        input.setBackground(inputBackground);
        android.widget.LinearLayout.LayoutParams inputParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = AndroidUtilities.dp(12);
        root.addView(input, inputParams);

        android.widget.LinearLayout actions = new android.widget.LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.TextView reset = actionText(context, "Сбросить", org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText2), false);
        android.widget.TextView cancel = actionText(context, "Отмена", org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText), false);
        android.widget.TextView save = actionText(context, "Сохранить", android.graphics.Color.WHITE, true);
        actions.addView(reset, new android.widget.LinearLayout.LayoutParams(0, AndroidUtilities.dp(42), 1f));
        actions.addView(cancel, new android.widget.LinearLayout.LayoutParams(0, AndroidUtilities.dp(42), 1f));
        actions.addView(save, new android.widget.LinearLayout.LayoutParams(0, AndroidUtilities.dp(42), 1.25f));
        android.widget.LinearLayout.LayoutParams actionParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = AndroidUtilities.dp(10);
        root.addView(actions, actionParams);

        close.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnClickListener(v -> dialog.dismiss());
        reset.setOnClickListener(v -> {
            clearOverride(account, dialogId, messageId);
            dialog.dismiss();
            if (onChanged != null) onChanged.run();
        });
        save.setOnClickListener(v -> {
            saveOverride(account, dialogId, messageId, input.getText().toString());
            dialog.dismiss();
            if (onChanged != null) onChanged.run();
        });
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setGravity(Gravity.BOTTOM);
                window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.WRAP_CONTENT);
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        dialog.show();
    }

    private static android.widget.TextView actionText(Context context, String label, int textColor, boolean primary) {
        android.widget.TextView view = new android.widget.TextView(context);
        view.setText(label);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(textColor);
        view.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        if (primary) {
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setColor(0xFF3688F5);
            background.setCornerRadius(AndroidUtilities.dp(12));
            view.setBackground(background);
        }
        return view;
    }

}