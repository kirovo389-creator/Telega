package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Chat-like editor for the fixed text sent by the offline auto responder. */
public class TelegaOfflineAutoReplyActivity extends BaseFragment {
    private static final int MP = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WC = LinearLayout.LayoutParams.WRAP_CONTENT;
    private EditText messageInput;
    private LinearLayout thread;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Автоответчик");
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, LayoutHelper.createFrame(MP, MP));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        page.addView(scroll, new LinearLayout.LayoutParams(MP, 0, 1f));
        thread = new LinearLayout(context);
        thread.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(thread, new ScrollView.LayoutParams(MP, WC));

        addBubble("Здесь задаётся готовый текст, который Telega отправит в личном чате, пока приложение в фоне. Ответ отправляется не чаще одного раза за 10 минут одному человеку.", false);
        String saved = TelegaSettings.getOfflineAutoReplyMessage().trim();
        if (!saved.isEmpty()) addBubble(saved, true);
        else addBubble("Напиши текст автоответа в поле ниже и нажми «Сохранить и включить». ИИ-модель для этого не нужна.", false);

        LinearLayout composer = new LinearLayout(context);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(9),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        composer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        page.addView(composer, new LinearLayout.LayoutParams(MP, WC));

        messageInput = new EditText(context);
        messageInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        messageInput.setHint("Текст автоответа…");
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageInput.setSingleLine(false);
        messageInput.setMinLines(2);
        messageInput.setMaxLines(5);
        messageInput.setText(saved);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        inputBg.setCornerRadius(AndroidUtilities.dp(12));
        messageInput.setBackground(inputBg);
        messageInput.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        composer.addView(messageInput, new LinearLayout.LayoutParams(MP, WC));

        TextView save = new TextView(context);
        save.setText("Сохранить и включить");
        save.setGravity(Gravity.CENTER);
        save.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        save.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        save.setTextColor(Color.WHITE);
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.parseColor("#2F80ED"));
        buttonBg.setCornerRadius(AndroidUtilities.dp(12));
        save.setBackground(buttonBg);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(MP, AndroidUtilities.dp(48));
        saveLp.topMargin = AndroidUtilities.dp(8);
        composer.addView(save, saveLp);

        save.setOnClickListener(v -> {
            String message = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
            if (message.isEmpty()) {
                Toast.makeText(context, "Сначала напиши сообщение", Toast.LENGTH_SHORT).show();
                return;
            }
            if (message.length() > 3900) message = message.substring(0, 3899) + "…";
            TelegaSettings.setOfflineAutoReplyMessage(message);
            TelegaSettings.setOfflineAutoReplyEnabled(true);
            Toast.makeText(context, "Автоответчик включён", Toast.LENGTH_SHORT).show();
            finishFragment();
        });

        fragmentView = root;
        return fragmentView;
    }

    private void addBubble(String value, boolean outgoing) {
        TextView bubble = new TextView(getParentActivity());
        bubble.setText(value);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bubble.setTextColor(outgoing ? Color.WHITE
                : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bubble.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(10),
                AndroidUtilities.dp(13), AndroidUtilities.dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(outgoing ? Color.parseColor("#2F80ED")
                : Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(15));
        bubble.setBackground(bg);
        bubble.setMaxWidth(AndroidUtilities.dp(320));
        LinearLayout row = new LinearLayout(getParentActivity());
        row.setGravity(outgoing ? Gravity.END : Gravity.START);
        row.addView(bubble, new LinearLayout.LayoutParams(WC, WC));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MP, WC);
        params.bottomMargin = AndroidUtilities.dp(10);
        thread.addView(row, params);
    }
}
