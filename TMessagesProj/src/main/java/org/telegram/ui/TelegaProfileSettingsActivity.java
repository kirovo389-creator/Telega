package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegaProfileColor;
import org.telegram.messenger.TelegaSettings;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

/** Profile-related privacy and presentation options. */
public class TelegaProfileSettingsActivity extends BaseFragment {
    private static final int[] PALETTE = {
            0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
            0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
            0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
            0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E,
            0xFF6D4C41, 0xFF546E7A, 0xFF37474F, 0xFF212121
    };

    private TextCheckCell storiesCell;
    private View colorSwatch;
    private int pendingColor;
    private int account;
    private long selfId;

    @Override
    public View createView(Context context) {
        account = currentAccount;
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        selfId = self != null ? self.id : 0;
        pendingColor = TelegaProfileColor.getColor(account, selfId);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Профиль");
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
        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(sectionTitle(context, "ЦВЕТ ПРОФИЛЯ"));
        LinearLayout colorCard = card(context);
        content.addView(colorCard, cardLp());

        LinearLayout colorRow = new LinearLayout(context);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        colorCard.addView(colorRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView colorLabel = new TextView(context);
        colorLabel.setText("Цвет аватарки");
        colorLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        colorLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        colorRow.addView(colorLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        colorSwatch = new View(context);
        updateSwatch();
        colorSwatch.setOnClickListener(v -> showPalette(context));
        colorRow.addView(colorSwatch, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(36), AndroidUtilities.dp(36)));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        colorCard.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView apply = actionButton(context, "Применить", true);
        apply.setOnClickListener(v -> {
            if (selfId == 0) {
                toast("Нет активного аккаунта");
                return;
            }
            if (pendingColor == 0) {
                toast("Выбери цвет на палитре");
                return;
            }
            TelegaProfileColor.setColor(account, selfId, pendingColor);
            updateSwatch();
            toast("Цвет сохранён");
        });
        TextView reset = actionButton(context, "Сброс", false);
        reset.setOnClickListener(v -> {
            pendingColor = 0;
            TelegaProfileColor.clear(account, selfId);
            updateSwatch();
            toast("Цвет сброшен");
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(44), 1f);
        btnLp.setMargins(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        buttons.addView(apply, btnLp);
        buttons.addView(reset, btnLp);

        TextView colorInfo = new TextView(context);
        colorInfo.setText("Цвет применяется только в Telega на этом устройстве к твоей аватарке-заглушке (когда нет фото). На сервер Telegram ничего не отправляется.");
        colorInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        colorInfo.setLineSpacing(AndroidUtilities.dp(2), 1f);
        colorInfo.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.65f));
        colorInfo.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        colorCard.addView(colorInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(sectionTitle(context, "ИСТОРИИ"));
        LinearLayout card = card(context);
        content.addView(card, cardLp());

        storiesCell = new TextCheckCell(context);
        storiesCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        storiesCell.setTextAndCheck("Отключить истории", TelegaSettings.isStoriesDisabled(), false);
        storiesCell.setColorfullIcon(0xFF52798B, R.drawable.telega_ic_user);
        storiesCell.setOnClickListener(v -> {
            boolean disabled = !TelegaSettings.isStoriesDisabled();
            TelegaSettings.setStoriesDisabled(disabled);
            storiesCell.setChecked(disabled);
        });
        card.addView(storiesCell, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(context);
        info.setText("Скрывает кольца историй, ленту историй и просмотр историй только в Telega на этом устройстве. Истории не удаляются с серверов и снова появятся после выключения настройки.");
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        info.setLineSpacing(AndroidUtilities.dp(2), 1f);
        info.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.65f));
        info.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        card.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fragmentView = root;
        return fragmentView;
    }

    private void showPalette(Context context) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        final int cols = 5;
        LinearLayout row = null;
        for (int i = 0; i < PALETTE.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                grid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final int color = PALETTE[i];
            View cell = new View(context);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(AndroidUtilities.dp(8));
            bg.setColor(color);
            if (pendingColor == color) {
                bg.setStroke(AndroidUtilities.dp(3), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
            cell.setBackground(bg);
            cell.setOnClickListener(v -> {
                pendingColor = color;
                updateSwatch();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(44), AndroidUtilities.dp(44));
            lp.setMargins(AndroidUtilities.dp(6), AndroidUtilities.dp(6),
                    AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            row.addView(cell, lp);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Палитра");
        builder.setView(grid);
        builder.setPositiveButton("Готово", (d, w) -> updateSwatch());
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void updateSwatch() {
        if (colorSwatch == null) {
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(AndroidUtilities.dp(8));
        int display = pendingColor != 0 ? pendingColor
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3);
        bg.setColor(display);
        bg.setStroke(AndroidUtilities.dp(1),
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.2f));
        colorSwatch.setBackground(bg);
    }

    private static TextView sectionTitle(Context context, String value) {
        TextView heading = new TextView(context);
        heading.setText(value);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        heading.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(22),
                AndroidUtilities.dp(28), AndroidUtilities.dp(8));
        return heading;
    }

    private static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        card.setBackground(background);
        card.setClipToOutline(true);
        return card;
    }

    private static LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        return cardLp;
    }

    private static TextView actionButton(Context context, String label, boolean primary) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(10));
        if (primary) {
            bg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            button.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.08f));
            button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        button.setBackground(bg);
        return button;
    }

    private void toast(String value) {
        try {
            Toast.makeText(getParentActivity(), value, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }
}
