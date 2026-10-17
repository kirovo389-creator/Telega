package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class TelegaDeletedMessagesSettingsActivity extends BaseFragment {

    private static final String PREFS = "telega_settings";
    private static final String KEY_KEEP_DELETED_MESSAGES = "keep_deleted_messages";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Удалённые сообщения");
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
        content.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout option = new LinearLayout(context);
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(16), dp(12), dp(16), dp(12));
        option.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundWhite)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("Сохранять удалённые сообщения");
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        labels.addView(title);

        TextView description = new TextView(context);
        description.setText("Оставлять копию сообщения в этом чате после удаления.");
        description.setTextSize(13);
        description.setLineSpacing(dp(2), 1f);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(5);
        labels.addView(description, descriptionParams);

        option.addView(labels, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(context);
        toggle.setChecked(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_KEEP_DELETED_MESSAGES, false));
        toggle.setOnCheckedChangeListener((button, checked) ->
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(KEY_KEEP_DELETED_MESSAGES, checked).apply());
        option.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(option, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView explanation = new TextView(context);
        explanation.setText("Сообщения сохраняются локально на этом устройстве и остаются в чате после удаления с прозрачностью 30% и подписью «🗑 Удалено». На сервер копии не отправляются.");
        explanation.setTextSize(13);
        explanation.setLineSpacing(dp(3), 1f);
        explanation.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        explanation.setPadding(dp(4), dp(14), dp(4), 0);
        content.addView(explanation);

        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fragmentView = root;
        return fragmentView;
    }
}
