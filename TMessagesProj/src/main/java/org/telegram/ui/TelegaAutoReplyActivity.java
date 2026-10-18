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
import org.telegram.messenger.TelegaAIAgent;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Separate in-app conversation used to configure the AI auto-responder prompt. */
public class TelegaAutoReplyActivity extends BaseFragment {

    private static final int MP = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WC = LinearLayout.LayoutParams.WRAP_CONTENT;
    private Context ctx;
    private LinearLayout thread;
    private ScrollView threadScroll;
    private EditText promptInput;
    private boolean connecting;

    @Override
    public View createView(Context context) {
        ctx = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("ИИ автоответчик");
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
        root.addView(page, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (!TelegaSettings.isAiConnected()) {
            buildConnectionForm(page);
        } else {
            buildPromptChat(page);
        }
        fragmentView = root;
        return fragmentView;
    }

    private void buildConnectionForm(LinearLayout page) {
        ScrollView scroll = new ScrollView(ctx);
        page.addView(scroll, new LinearLayout.LayoutParams(MP, 0, 1f));
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(MP, WC));

        addBubble(content, "Чтобы включить автоответчик, сначала подключи ИИ-модель. Укажи данные выбранного OpenAI-совместимого API.", false);

        EditText url = field("https://openrouter.ai/api/v1", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText key = field("API key", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText model = field("ID модели, например openai/gpt-4o-mini", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        url.setText(TelegaSettings.getAiBaseUrl());
        key.setText(TelegaSettings.getAiApiKey());
        model.setText(TelegaSettings.getAiModel());
        addLabel(content, "Base URL", url);
        addLabel(content, "API Key", key);
        addLabel(content, "Модель", model);

        TextView connect = actionButton("Подключить ИИ");
        content.addView(connect, lp(MP, AndroidUtilities.dp(48), 0, 16, 0, 0));
        connect.setOnClickListener(v -> {
            if (connecting) return;
            String u = url.getText().toString().trim();
            String k = key.getText().toString().trim();
            String m = model.getText().toString().trim();
            if (u.isEmpty() || k.isEmpty() || m.isEmpty()) {
                toast("Заполни все три поля", false);
                return;
            }
            connecting = true;
            connect.setText("Проверка подключения…");
            new Thread(() -> {
                String error = TelegaAIAgent.test(u, k, m);
                AndroidUtilities.runOnUIThread(() -> {
                    connecting = false;
                    if (page.getParent() == null) return;
                    if (error == null) {
                        TelegaSettings.saveAiConnection(u, k, m);
                        toast("ИИ подключён", false);
                        page.removeAllViews();
                        buildPromptChat(page);
                    } else {
                        connect.setText("Подключить ИИ");
                        toast("Ошибка подключения: " + error, true);
                    }
                });
            }, "TelegaAutoReplyConnect").start();
        });
    }

    private void buildPromptChat(LinearLayout page) {
        threadScroll = new ScrollView(ctx);
        threadScroll.setFillViewport(false);
        threadScroll.setClipToPadding(false);
        threadScroll.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        page.addView(threadScroll, new LinearLayout.LayoutParams(MP, 0, 1f));

        thread = new LinearLayout(ctx);
        thread.setOrientation(LinearLayout.VERTICAL);
        threadScroll.addView(thread, new ScrollView.LayoutParams(MP, WC));

        addBubble(thread,
                "Это отдельный чат настройки автоответчика. Опиши, как ИИ должен отвечать людям, пока тебя нет в сети. Он честно сообщит, что является ИИ, и при твоём возвращении подготовит краткую сводку переписки.",
                false);
        String saved = TelegaSettings.getAiAutoReplyPrompt();
        if (!saved.trim().isEmpty()) {
            addBubble(thread, saved, true);
            addBubble(thread, "Промт принят", false);
        }

        LinearLayout composer = new LinearLayout(ctx);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        composer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        page.addView(composer, new LinearLayout.LayoutParams(MP, WC));

        promptInput = field("Напиши промт для автоответчика…",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        promptInput.setSingleLine(false);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(4);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, WC, 1f);
        inputLp.setMargins(0, 0, AndroidUtilities.dp(8), 0);
        composer.addView(promptInput, inputLp);

        TextView send = actionButton("➤");
        send.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        composer.addView(send, new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(46)));
        send.setOnClickListener(v -> {
            String prompt = promptInput.getText().toString().trim();
            if (prompt.isEmpty()) {
                toast("Сначала напиши промт", false);
                return;
            }
            if (!TelegaSettings.isAiConnected()) {
                toast("Сначала подключи ИИ", true);
                return;
            }
            TelegaSettings.setAiAutoReplyPrompt(prompt);
            TelegaSettings.setAiAutoReplyEnabled(true);
            addBubble(thread, prompt, true);
            addBubble(thread, "Промт принят", false);
            promptInput.setText("");
            scrollToBottom();
        });
        scrollToBottom();
    }

    private void addBubble(LinearLayout parent, String text, boolean outgoing) {
        TextView bubble = new TextView(ctx);
        bubble.setText(text);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bubble.setTextColor(outgoing ? Color.WHITE
                : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bubble.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(10), AndroidUtilities.dp(13), AndroidUtilities.dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(outgoing ? Color.parseColor("#2F80ED") : Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(15));
        bubble.setBackground(bg);
        bubble.setMaxWidth(AndroidUtilities.dp(320));
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(outgoing ? Gravity.END : Gravity.START);
        row.addView(bubble, new LinearLayout.LayoutParams(WC, WC));
        parent.addView(row, lp(MP, WC, 0, 0, 0, 10));
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        e.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        e.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(10));
        e.setBackground(bg);
        e.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(9), AndroidUtilities.dp(12), AndroidUtilities.dp(9));
        return e;
    }

    private void addLabel(LinearLayout parent, String label, EditText field) {
        TextView title = new TextView(ctx);
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        title.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(12), AndroidUtilities.dp(2), AndroidUtilities.dp(4));
        parent.addView(title, lp(MP, WC, 0, 4, 0, 0));
        parent.addView(field, lp(MP, WC, 0, 0, 0, 0));
    }

    private TextView actionButton(String label) {
        TextView button = new TextView(ctx);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#2F80ED"));
        bg.setCornerRadius(AndroidUtilities.dp(12));
        button.setBackground(bg);
        return button;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(AndroidUtilities.dp(l), AndroidUtilities.dp(t), AndroidUtilities.dp(r), AndroidUtilities.dp(b));
        return p;
    }

    private void scrollToBottom() {
        if (threadScroll != null) {
            threadScroll.post(() -> threadScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void toast(String message, boolean longDuration) {
        try {
            Toast.makeText(ctx, message, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }
}
