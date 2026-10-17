package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class TelegaSettingsActivity extends BaseFragment {

    private final String[] sectionTitles = {
            "Сообщения", "Общее", "Профиль", "Другое", "Кастомизация"
    };
    private final int[] sectionIcons = {
            R.drawable.settings_chat,
            R.drawable.settings_features,
            R.drawable.settings_account,
            R.drawable.settings_faq,
            R.drawable.settings_gram_24
    };
    private final int[] sectionColors = {
            Color.rgb(47, 143, 230),
            Color.rgb(43, 174, 112),
            Color.rgb(153, 102, 230),
            Color.rgb(120, 130, 145),
            Color.rgb(232, 139, 48)
    };

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки Telega");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(16));

        TextView caption = new TextView(context);
        caption.setText("РАЗДЕЛЫ");
        caption.setTextSize(12);
        caption.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        caption.setLetterSpacing(0.08f);
        caption.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        caption.setPadding(dp(4), dp(8), 0, dp(10));
        content.addView(caption, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < sectionTitles.length; i++) {
            final int index = i;
            content.addView(createSectionRow(context, i), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));
            View divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(0.5f));
            dividerParams.leftMargin = dp(60);
            content.addView(divider, dividerParams);
        }

        TextView changes = new TextView(context);
        changes.setText("Изменения");
        changes.setTextSize(14);
        changes.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        changes.setPadding(dp(4), dp(18), dp(4), dp(6));
        content.addView(changes);

        TextView update = new TextView(context);
        update.setText("+ Добавлен раздел «Настройки Telega».");
        update.setTextSize(14);
        update.setLineSpacing(dp(3), 1f);
        update.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        update.setPadding(dp(4), dp(4), dp(4), dp(8));
        content.addView(update);

        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fragmentView = root;
        return fragmentView;
    }

    private View createSectionRow(Context context, int index) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        row.setClickable(true);
        row.setFocusable(true);

        FrameLayout iconBox = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(sectionColors[index]);
        background.setCornerRadius(dp(12));
        iconBox.setBackground(background);
        ImageView icon = new ImageView(context);
        icon.setImageResource(sectionIcons[index]);
        icon.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
        iconBox.addView(icon, iconParams);
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = new TextView(context);
        title.setText(sectionTitles[index]);
        title.setTextSize(16);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        titleParams.leftMargin = dp(14);
        row.addView(title, titleParams);

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(42)));

        row.setOnClickListener(v -> presentFragment(new TelegaCategorySettingsActivity(sectionTitles[index])));
        return row;
    }
}
