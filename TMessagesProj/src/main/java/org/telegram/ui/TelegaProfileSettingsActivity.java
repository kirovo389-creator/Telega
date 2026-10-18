package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
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

/** Profile-related privacy and presentation options. */
public class TelegaProfileSettingsActivity extends BaseFragment {
    private TextCheckCell storiesCell;

    @Override
    public View createView(Context context) {
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

        TextView heading = new TextView(context);
        heading.setText("ИСТОРИИ");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        heading.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(22),
                AndroidUtilities.dp(28), AndroidUtilities.dp(8));
        content.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        card.setBackground(background);
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        content.addView(card, cardLp);

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
}
