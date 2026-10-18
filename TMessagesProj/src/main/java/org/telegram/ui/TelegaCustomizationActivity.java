package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Chat-wide visual effects. Choices are persisted and apply to private chats,
 * groups, and channels through TelegaChatEffects.
 */
public class TelegaCustomizationActivity extends BaseFragment {

    private Context ctx;
    private TextCheckCell minecraftSnowCell;
    private TextCheckCell realSnowCell;
    private TextCheckCell garlandCell;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public View createView(Context context) {
        ctx = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Кастомизация");
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSectionTitle(content, "Снег в чатах");
        addDescription(content,
                "Выбери один режим. Эффект отображается позади сообщений во всех личных чатах, группах и каналах.");

        LinearLayout snowCard = newCard();
        content.addView(snowCard, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 16, 4, 16, 0));

        minecraftSnowCell = createCheckCell(
                "Снег в стиле Minecraft",
                R.drawable.telega_ic_snow,
                0xFF52798B,
                TelegaSettings.getSnowMode() == TelegaSettings.SNOW_MINECRAFT,
                true
        );
        snowCard.addView(minecraftSnowCell, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

        realSnowCell = createCheckCell(
                "Реалистичный снег",
                R.drawable.telega_ic_wind,
                0xFF718B9A,
                TelegaSettings.getSnowMode() == TelegaSettings.SNOW_REAL,
                false
        );
        snowCard.addView(realSnowCell, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

        TextView snowInfo = text(
                "Minecraft — блочные пиксельные снежинки. Реалистичный — ярко-белый мягкий снег; его направление плавно меняется вместе с наклоном и поворотом экрана. Повторное нажатие отключает выбранный эффект.",
                13,
                0.65f
        );
        snowInfo.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(11), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        snowCard.addView(snowInfo, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

        addSectionTitle(content, "Гирлянда на сообщениях");
        LinearLayout garlandCard = newCard();
        content.addView(garlandCard, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 16, 4, 16, 0));

        garlandCell = createCheckCell(
                "Ёлочная гирлянда",
                R.drawable.telega_ic_lights,
                0xFF9B805E,
                TelegaSettings.isGarlandEnabled(),
                false
        );
        garlandCard.addView(garlandCell, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

        TextView garlandInfo = text(
                "Тонкий провод провисает над пузырём сообщения. Лампочки плавно покачиваются и медленно мигают; настройка сохраняется после перезапуска.",
                13,
                0.65f
        );
        garlandInfo.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        garlandCard.addView(garlandInfo, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

        minecraftSnowCell.setOnClickListener(v -> {
            int next = TelegaSettings.getSnowMode() == TelegaSettings.SNOW_MINECRAFT
                    ? TelegaSettings.SNOW_OFF : TelegaSettings.SNOW_MINECRAFT;
            TelegaSettings.setSnowMode(next);
            updateSnowChecks();
        });
        realSnowCell.setOnClickListener(v -> {
            int next = TelegaSettings.getSnowMode() == TelegaSettings.SNOW_REAL
                    ? TelegaSettings.SNOW_OFF : TelegaSettings.SNOW_REAL;
            TelegaSettings.setSnowMode(next);
            updateSnowChecks();
        });
        garlandCell.setOnClickListener(v -> {
            boolean enabled = !TelegaSettings.isGarlandEnabled();
            TelegaSettings.setGarlandEnabled(enabled);
            garlandCell.setChecked(enabled);
        });

        fragmentView = root;
        return fragmentView;
    }

    private TextCheckCell createCheckCell(String title, int icon, int iconColor, boolean checked, boolean divider) {
        TextCheckCell cell = new TextCheckCell(ctx);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndCheck(title, checked, divider);
        cell.setColorfullIcon(iconColor, icon);
        return cell;
    }

    private void updateSnowChecks() {
        int mode = TelegaSettings.getSnowMode();
        minecraftSnowCell.setChecked(mode == TelegaSettings.SNOW_MINECRAFT);
        realSnowCell.setChecked(mode == TelegaSettings.SNOW_REAL);
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        card.setBackground(background);
        card.setClipToOutline(true);
        return card;
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView view = text(title, 15, 1f);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(21), AndroidUtilities.dp(28), AndroidUtilities.dp(4));
        parent.addView(view, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));
    }

    private void addDescription(LinearLayout parent, String description) {
        TextView view = text(description, 13, 0.65f);
        view.setLineSpacing(AndroidUtilities.dp(2), 1f);
        view.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(4), AndroidUtilities.dp(28), AndroidUtilities.dp(8));
        parent.addView(view, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));
    }

    private TextView text(String value, int sizeDp, float alpha) {
        TextView view = new TextView(ctx);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        view.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), alpha));
        return view;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(AndroidUtilities.dp(left), AndroidUtilities.dp(top), AndroidUtilities.dp(right), AndroidUtilities.dp(bottom));
        return params;
    }
}
