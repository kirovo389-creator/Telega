package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.TelegaGhostMode;
import org.telegram.messenger.TelegaSettings;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class GhostModeActivity extends BaseFragment {

    private TextView statusView;
    private TextView activateButton;
    private TextView statusDot;
    private boolean requestInFlight;

    @Override
    public View createView(Context context) {
        if (actionBar != null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString(R.string.GhostModeTitle));
        }

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        content.setBackground(Theme.createRoundRectDrawable(dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite)));

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.GhostModeStoriesTitle));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        content.addView(title, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText(LocaleController.getString(R.string.GhostModeStoriesDescription));
        description.setTextSize(14);
        description.setLineSpacing(dp(2), 1f);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams descriptionParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        descriptionParams.topMargin = dp(10);
        content.addView(description, descriptionParams);

        LinearLayout statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusCard.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_windowBackgroundGray)));
        LinearLayout.LayoutParams statusCardParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        statusCardParams.topMargin = dp(18);
        content.addView(statusCard, statusCardParams);

        statusDot = new TextView(context);
        statusDot.setText("●");
        statusDot.setTextSize(12);
        statusDot.setGravity(Gravity.CENTER);
        statusDot.setTextColor(Theme.getColor(TelegaSettings.isGhostModeEnabled()
                ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteGrayText));
        statusCard.addView(statusDot, LayoutHelper.createLinear(dp(20), LayoutHelper.WRAP_CONTENT));

        statusView = new TextView(context);
        statusView.setText(LocaleController.getString(TelegaSettings.isGhostModeEnabled()
                ? R.string.GhostModeEnabled : R.string.GhostModeStatusUnverified));
        statusView.setTextSize(14);
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusView.setLineSpacing(dp(2), 1f);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        statusCard.addView(statusView, LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1f));

        activateButton = new TextView(context);
        activateButton.setText(LocaleController.getString(TelegaSettings.isGhostModeEnabled()
                ? R.string.GhostModeDeactivate : R.string.GhostModeActivate));
        activateButton.setTextSize(15);
        activateButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        activateButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        activateButton.setGravity(Gravity.CENTER);
        activateButton.setMinHeight(dp(48));
        activateButton.setPadding(dp(16), dp(13), dp(16), dp(13));
        activateButton.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton)));
        activateButton.setOnClickListener(this::activateStealthMode);
        LinearLayout.LayoutParams buttonParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        buttonParams.topMargin = dp(16);
        content.addView(activateButton, buttonParams);

        TextView limitation = new TextView(context);
        limitation.setText(LocaleController.getString(R.string.GhostModeLimitation));
        limitation.setTextSize(13);
        limitation.setLineSpacing(dp(2), 1f);
        limitation.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams limitationParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        limitationParams.topMargin = dp(16);
        content.addView(limitation, limitationParams);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        contentParams.setMargins(dp(16), dp(16), dp(16), dp(16));
        root.addView(content, contentParams);

        fragmentView = root;
        return fragmentView;
    }

    private void activateStealthMode(View view) {
        if (getParentActivity() == null || fragmentView == null || activateButton == null || requestInFlight) return;
        final boolean enable = !TelegaSettings.isGhostModeEnabled();
        requestInFlight = true;
        activateButton.setEnabled(false);
        activateButton.setAlpha(0.6f);
        if (statusView != null) statusView.setText(LocaleController.getString(R.string.GhostModeRequesting));
        if (statusDot != null) statusDot.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        TelegaGhostMode.setEnabled(currentAccount, enable, (success, message) -> {
            if (fragmentView == null || activateButton == null || statusView == null) return;
            requestInFlight = false;
            activateButton.setEnabled(true);
            activateButton.setAlpha(1f);
            if (success) {
                activateButton.setText(LocaleController.getString(enable ? R.string.GhostModeDeactivate : R.string.GhostModeActivate));
                statusView.setText(LocaleController.getString(enable ? R.string.GhostModeEnabled : R.string.GhostModeDisabled));
                if (statusDot != null) statusDot.setTextColor(Theme.getColor(enable
                        ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteGrayText));
                Toast.makeText(getParentActivity(), LocaleController.getString(enable
                        ? R.string.GhostModeEnabled : R.string.GhostModeDisabled), Toast.LENGTH_SHORT).show();
            } else {
                String errorText = message == null || message.isEmpty()
                        ? LocaleController.getString(R.string.GhostModeActivationError) : message;
                statusView.setText(LocaleController.getString(R.string.GhostModeErrorPrefix) + errorText);
                if (statusDot != null) statusDot.setTextColor(0xffe05252);
                Toast.makeText(getParentActivity(), errorText, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onFragmentDestroy() {
        requestInFlight = false;
        statusView = null;
        activateButton = null;
        statusDot = null;
        super.onFragmentDestroy();
    }

}
