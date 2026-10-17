package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class TelegaSettingsActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки Telega");
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

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(16));

        TextView heading = new TextView(context);
        heading.setText("Изменения");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        content.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView details = new TextView(context);
        details.setText("+ Добавлен раздел «Настройки Telega».");
        details.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        details.setLineSpacing(dp(3), 1.0f);
        details.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        details.setGravity(Gravity.START);
        details.setPadding(0, dp(12), 0, 0);
        content.addView(details, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        fragmentView = root;
        return fragmentView;
    }
}
