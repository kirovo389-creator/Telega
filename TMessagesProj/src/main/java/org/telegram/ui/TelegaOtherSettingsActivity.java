package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * "Другое" section. Contains the AI agent:
 *  - not connected: asks for base url, api key and model id + "Подключить" button;
 *  - connected: on/off switch (ON right after connecting).
 */
public class TelegaOtherSettingsActivity extends BaseFragment {

    private static final int MP = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WC = LinearLayout.LayoutParams.WRAP_CONTENT;

    private Context ctx;
    private LinearLayout content;
    private boolean connecting;

    @Override
    public View createView(Context context) {
        ctx = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("\u2728  Другое");
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
        fragmentView = root;

        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(32));
        scroll.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        rebuild();
        return fragmentView;
    }

    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        content = null;
    }

    private void rebuild() {
        if (content == null) {
            return;
        }
        content.removeAllViews();
        content.addView(sectionTitle("ИИ АГЕНТ"));
        if (TelegaSettings.isAiConnected()) {
            buildConnected();
        } else {
            buildForm();
        }
        TextView footer = text(
                "Команды работают в любом чате, в том числе в группах:\n" +
                ".ai — показать, что агент включён\n" +
                ".ai вопрос — получить ответ\n\n" +
                "Ответ отправляется в чат от вашего имени и виден всем участникам. " +
                "Подойдёт любой OpenAI-совместимый API (/chat/completions).",
                13, 0.55f);
        footer.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(12), AndroidUtilities.dp(32), AndroidUtilities.dp(8));
        footer.setLineSpacing(AndroidUtilities.dp(2), 1f);
        content.addView(footer, lp(MP, WC, 0, 0, 0, 0));
    }

    // ------------------------------------------------------------------ form

    private void buildForm() {
        LinearLayout card = newCard();
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        content.addView(card, lp(MP, WC, 16, 0, 16, 0));

        card.addView(text("ИИ агент ещё не настроен. Введи данные подключения:", 14, 0.7f), lp(MP, WC, 0, 0, 0, 6));

        final EditText url = field("https://api.openai.com/v1", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText key = field("sk-...", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText model = field("gpt-4o-mini", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        url.setText(TelegaSettings.getAiBaseUrl());
        key.setText(TelegaSettings.getAiApiKey());
        model.setText(TelegaSettings.getAiModel());

        addLabeled(card, "Base URL", url);
        addLabeled(card, "API Key", key);
        addLabeled(card, "ID модели", model);

        final TextView button = new TextView(ctx);
        button.setText("Подключить");
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#2F80ED"));
        bg.setCornerRadius(AndroidUtilities.dp(12));
        button.setBackground(bg);
        card.addView(button, lp(MP, AndroidUtilities.dp(48), 0, 18, 0, 0));

        button.setOnClickListener(v -> {
            if (connecting) {
                return;
            }
            final String u = url.getText().toString().trim();
            final String k = key.getText().toString().trim();
            final String m = model.getText().toString().trim();
            if (u.isEmpty() || k.isEmpty() || m.isEmpty()) {
                toast("Заполни Base URL, API Key и ID модели", false);
                return;
            }
            connecting = true;
            button.setText("Подключение…");
            button.setAlpha(0.6f);
            new Thread(() -> {
                final String err = TelegaAIAgent.test(u, k, m);
                AndroidUtilities.runOnUIThread(() -> {
                    connecting = false;
                    if (content == null) {
                        return;
                    }
                    if (err == null) {
                        TelegaSettings.saveAiConnection(u, k, m);
                        toast("\uD83E\uDD16 ИИ агент подключён", false);
                        rebuild();
                    } else {
                        button.setText("Подключить");
                        button.setAlpha(1f);
                        toast("Не удалось подключиться: " + err, true);
                    }
                });
            }, "TelegaAITest").start();
        });
    }

    // -------------------------------------------------------------- connected

    private void buildConnected() {
        LinearLayout card = newCard();
        content.addView(card, lp(MP, WC, 16, 0, 16, 0));

        final TextCheckCell cell = new TextCheckCell(ctx);
        cell.setTextAndCheck("ИИ-агент", TelegaSettings.isAiEnabled(), true);
        cell.setColorfullIcon(0xFF52798B, R.drawable.telega_ic_robot);
        cell.setOnClickListener(v -> {
            boolean n = !TelegaSettings.isAiEnabled();
            TelegaSettings.setAiEnabled(n);
            cell.setChecked(n);
        });
        card.addView(cell, lp(MP, WC, 0, 0, 0, 0));

        TextView info = text("Модель: " + TelegaSettings.getAiModel() + "\nСервер: " + TelegaSettings.getAiBaseUrl(), 14, 0.6f);
        info.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        card.addView(info, lp(MP, WC, 0, 0, 0, 0));

        TextView reset = text("Отключить и настроить заново", 16, 1f);
        reset.setTextColor(Color.parseColor("#E23A41"));
        reset.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        reset.setOnClickListener(v -> {
            TelegaSettings.clearAiConnection();
            rebuild();
        });
        card.addView(reset, lp(MP, WC, 0, 0, 0, 0));
    }

    // ---------------------------------------------------------------- helpers

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        d.setCornerRadius(AndroidUtilities.dp(12));
        card.setBackground(d);
        return card;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(AndroidUtilities.dp(l), AndroidUtilities.dp(t), AndroidUtilities.dp(r), AndroidUtilities.dp(b));
        return p;
    }

    private TextView sectionTitle(String s) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        tv.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(10), AndroidUtilities.dp(32), AndroidUtilities.dp(8));
        return tv;
    }

    private TextView text(String s, int sizeDp, float alpha) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        tv.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), alpha));
        return tv;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        e.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        e.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        d.setCornerRadius(AndroidUtilities.dp(10));
        e.setBackground(d);
        e.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(11), AndroidUtilities.dp(12), AndroidUtilities.dp(11));
        return e;
    }

    private void addLabeled(LinearLayout card, String label, EditText field) {
        TextView l = text(label, 13, 0.6f);
        card.addView(l, lp(MP, WC, 2, 12, 0, 4));
        card.addView(field, lp(MP, WC, 0, 0, 0, 0));
    }

    private void toast(String s, boolean longDuration) {
        try {
            Toast.makeText(ctx, s, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }
}
