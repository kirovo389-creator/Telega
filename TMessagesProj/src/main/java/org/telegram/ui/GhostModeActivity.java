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
        statusDot.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusCard.addView(statusDot, LayoutHelper.createLinear(dp(20), LayoutHelper.WRAP_CONTENT));

        statusView = new TextView(context);
        statusView.setText(LocaleController.getString(R.string.GhostModeStatusUnverified));
        statusView.setTextSize(14);
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusView.setLineSpacing(dp(2), 1f);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        statusCard.addView(statusView, LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1f));

        activateButton = new TextView(context);
        activateButton.setText(LocaleController.getString(R.string.GhostModeActivate));
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
        if (getParentActivity() == null || fragmentView == null || activateButton == null || requestInFlight) {
            return;
        }
        requestInFlight = true;
        activateButton.setEnabled(false);
        activateButton.setAlpha(0.6f);
        if (statusView != null) {
            statusView.setText(LocaleController.getString(R.string.GhostModeRequesting));
        }
        if (statusDot != null) {
            statusDot.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        }

        TL_stories.TL_stories_activateStealthMode request = new TL_stories.TL_stories_activateStealthMode();
        request.past = true;
        request.future = true;
        try {
            ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    // A response may arrive after the fragment has been destroyed.
                    if (fragmentView == null || activateButton == null || statusView == null) {
                        return;
                    }
                    requestInFlight = false;
                    activateButton.setEnabled(true);
                    activateButton.setAlpha(1f);

                    if (response instanceof TLRPC.Updates && error == null) {
                        TLRPC.Updates updates = (TLRPC.Updates) response;
                        MessagesController.getInstance(currentAccount).processUpdateArray(
                                updates.updates, updates.users, updates.chats, false, updates.date);
                        statusView.setText(LocaleController.getString(R.string.GhostModeRequestAcceptedUnverified));
                        if (statusDot != null) {
                            statusDot.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                        }
                        if (getParentActivity() != null) {
                            Toast.makeText(getParentActivity(), LocaleController.getString(R.string.GhostModeRequestAcceptedToast), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        String message = error != null && error.text != null && !error.text.isEmpty()
                                ? error.text
                                : LocaleController.getString(R.string.GhostModeActivationError);
                        statusView.setText(LocaleController.getString(R.string.GhostModeErrorPrefix) + message);
                        if (statusDot != null) {
                            statusDot.setTextColor(0xffe05252);
                        }
                        if (getParentActivity() != null) {
                            Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                        }
                    }
                }));
        } catch (Throwable error) {
            requestInFlight = false;
            if (activateButton != null) {
                activateButton.setEnabled(true);
                activateButton.setAlpha(1f);
            }
            if (statusView != null) {
                statusView.setText(LocaleController.getString(R.string.GhostModeActivationError));
            }
            if (statusDot != null) {
                statusDot.setTextColor(0xffe05252);
            }
            FileLog.e(error);
        }
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
