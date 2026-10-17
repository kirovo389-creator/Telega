package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class TelegaCategorySettingsActivity extends BaseFragment {

    private final String category;

    public TelegaCategorySettingsActivity(String category) {
        this.category = category;
    }

    private String[] getItems() {
        switch (category) {
            case "Сообщения":
                return new String[] {"История изменений сообщений", "Отображение удалённых сообщений", "Реакции и эмодзи"};
            case "Общее":
                return new String[] {"Статус эмодзи", "Уведомления", "Работа в фоне"};
            case "Профиль":
                return new String[] {"Цвет профиля", "Цвет имени", "Эмодзи-фон профиля"};
            case "Другое":
                return new String[] {"Обновления Telega", "Информация о приложении"};
            case "Кастомизация":
                return new String[] {"Оформление чатов", "Стикеры и премиум-эмодзи", "Анимации и производительность"};
            default:
                return new String[0];
        }
    }

    private int getIcon(int index) {
        switch (category) {
            case "Сообщения":
                return index == 0 ? R.drawable.settings_chat : R.drawable.settings_features;
            case "Общее":
                return index == 0 ? R.drawable.settings_features : R.drawable.settings_sounds;
            case "Профиль":
                return R.drawable.settings_account;
            case "Другое":
                return index == 0 ? R.drawable.settings_power : R.drawable.settings_faq;
            case "Кастомизация":
                return index == 0 ? R.drawable.settings_chat : R.drawable.settings_gram_24;
            default:
                return R.drawable.settings_features;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(category);
        actionBar.setActionBarMenuOnItemClick(id -> {
            if (id == -1) finishFragment();
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(12), dp(16), dp(16));

        TextView caption = new TextView(context);
        caption.setText(category.toUpperCase());
        caption.setTextSize(12);
        caption.setLetterSpacing(0.08f);
        caption.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        caption.setPadding(dp(4), dp(8), 0, dp(10));
        list.addView(caption, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        String[] items = getItems();
        for (int i = 0; i < items.length; i++) {
            final String itemTitle = items[i];
            list.addView(createItemRow(context, items[i], getIcon(i)), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
            View divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(0.5f));
            dividerParams.leftMargin = dp(52);
            list.addView(divider, dividerParams);
        }

        TextView note = new TextView(context);
        note.setText("Пункты меню добавлены. Логику каждой настройки подключим отдельно.");
        note.setTextSize(13);
        note.setLineSpacing(dp(2), 1f);
        note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        note.setPadding(dp(4), dp(18), dp(4), dp(4));
        list.addView(note);

        root.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fragmentView = root;
        return fragmentView;
    }

    private View createItemRow(Context context, String titleText, int iconResource) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(8), 0);
        row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        row.setClickable(true);
        row.setFocusable(true);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconResource);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        row.addView(icon, iconParams);

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(15);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        titleParams.leftMargin = dp(14);
        row.addView(title, titleParams);

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextSize(26);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(42)));

        row.setOnClickListener(v -> new AlertDialog.Builder(getParentActivity())
                .setTitle(titleText)
                .setMessage("Пункт добавлен в меню Telega. Саму настройку подключим отдельно.")
                .setPositiveButton("Понятно", null)
                .show());
        return row;
    }
}
