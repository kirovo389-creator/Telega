package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.GhostModeTitle));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(16), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // Hero card: a clear visual entry point without relying on external assets.
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(22), dp(24), dp(22), dp(24));
        hero.setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_windowBackgroundWhite)));
        content.addView(hero, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout illustration = new FrameLayout(context);
        illustration.setBackground(Theme.createRoundRectDrawable(dp(30), Theme.getColor(Theme.key_windowBackgroundGray)));
        GhostIllustrationView ghost = new GhostIllustrationView(context);
        illustration.addView(ghost, new FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER));
        hero.addView(illustration, LayoutHelper.createLinear(dp(124), dp(124), Gravity.CENTER_HORIZONTAL));

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.GhostModeStoriesTitle));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        LinearLayout.LayoutParams titleParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        titleParams.topMargin = dp(20);
        hero.addView(title, titleParams);

        TextView description = new TextView(context);
        description.setText(LocaleController.getString(R.string.GhostModeStoriesDescription));
        description.setTextSize(14);
        description.setLineSpacing(dp(3), 1.0f);
        description.setGravity(Gravity.CENTER);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams descriptionParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        descriptionParams.topMargin = dp(10);
        hero.addView(description, descriptionParams);

        // Status is separated from the primary action and remains readable in every state.
        LinearLayout statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        statusCard.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
        LinearLayout.LayoutParams statusCardParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        statusCardParams.topMargin = dp(12);
        content.addView(statusCard, statusCardParams);

        statusDot = new TextView(context);
        statusDot.setText("●");
        statusDot.setTextSize(12);
        statusDot.setGravity(Gravity.CENTER);
        statusDot.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusCard.addView(statusDot, LayoutHelper.createLinear(dp(20), LayoutHelper.WRAP_CONTENT));

        LinearLayout statusTextContainer = new LinearLayout(context);
        statusTextContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statusTextParams = LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1f);
        statusTextParams.leftMargin = dp(10);
        statusCard.addView(statusTextContainer, statusTextParams);

        TextView statusLabel = new TextView(context);
        statusLabel.setText(LocaleController.getString(R.string.GhostModeTitle));
        statusLabel.setTextSize(12);
        statusLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusTextContainer.addView(statusLabel, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusView = new TextView(context);
        statusView.setText(LocaleController.getString(R.string.GhostModeStatusUnverified));
        statusView.setTextSize(14);
        statusView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusView.setLineSpacing(dp(2), 1f);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        LinearLayout.LayoutParams statusTextViewParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        statusTextViewParams.topMargin = dp(3);
        statusTextContainer.addView(statusView, statusTextViewParams);

        activateButton = new TextView(context);
        activateButton.setText(LocaleController.getString(R.string.GhostModeActivate));
        activateButton.setTextSize(15);
        activateButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        activateButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        activateButton.setGravity(Gravity.CENTER);
        activateButton.setMinHeight(dp(52));
        activateButton.setPadding(dp(20), dp(14), dp(20), dp(14));
        activateButton.setBackground(Theme.createRoundRectDrawable(dp(16),
                Theme.getColor(Theme.key_featuredStickers_addButton)));
        activateButton.setOnClickListener(this::activateStealthMode);
        LinearLayout.LayoutParams buttonParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        buttonParams.topMargin = dp(14);
        content.addView(activateButton, buttonParams);

        // A quieter information card keeps limitations visible without competing with the action.
        LinearLayout limitationCard = new LinearLayout(context);
        limitationCard.setOrientation(LinearLayout.HORIZONTAL);
        limitationCard.setGravity(Gravity.TOP);
        limitationCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        limitationCard.setBackground(Theme.createRoundRectDrawable(dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite)));
        LinearLayout.LayoutParams limitationCardParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        limitationCardParams.topMargin = dp(12);
        content.addView(limitationCard, limitationCardParams);

        TextView infoMark = new TextView(context);
        infoMark.setText("i");
        infoMark.setTextSize(15);
        infoMark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoMark.setGravity(Gravity.CENTER);
        infoMark.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        infoMark.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_windowBackgroundGray)));
        limitationCard.addView(infoMark, LayoutHelper.createLinear(dp(28), dp(28)));

        TextView limitation = new TextView(context);
        limitation.setText(LocaleController.getString(R.string.GhostModeLimitation));
        limitation.setTextSize(13);
        limitation.setLineSpacing(dp(3), 1f);
        limitation.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams limitationParams = LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1f);
        limitationParams.leftMargin = dp(12);
        limitationCard.addView(limitation, limitationParams);

        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(scrollView, scrollParams);

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
    }

    @Override
    public void onFragmentDestroy() {
        requestInFlight = false;
        statusView = null;
        activateButton = null;
        statusDot = null;
        super.onFragmentDestroy();
    }

    /** Small vector-style ghost illustration, drawn in code to avoid adding a bitmap asset. */
    private static class GhostIllustrationView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path ghostPath = new Path();

        GhostIllustrationView(Context context) {
            super(context);
            setContentDescription(getContext().getString(R.string.GhostModeIllustrationDescription));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float unit = dp(1);
            float radius = 43 * unit;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            canvas.drawCircle(cx, cy, radius, paint);

            float scale = unit;
            canvas.save();
            canvas.translate(cx - 24 * scale, cy - 27 * scale);
            ghostPath.reset();
            ghostPath.moveTo(24 * scale, 2 * scale);
            ghostPath.cubicTo(11 * scale, 2 * scale, 4 * scale, 12 * scale, 4 * scale, 24 * scale);
            ghostPath.lineTo(4 * scale, 47 * scale);
            ghostPath.cubicTo(8 * scale, 47 * scale, 9 * scale, 41 * scale, 14 * scale, 44 * scale);
            ghostPath.cubicTo(18 * scale, 47 * scale, 20 * scale, 49 * scale, 24 * scale, 45 * scale);
            ghostPath.cubicTo(28 * scale, 49 * scale, 31 * scale, 47 * scale, 35 * scale, 44 * scale);
            ghostPath.cubicTo(39 * scale, 41 * scale, 41 * scale, 47 * scale, 44 * scale, 47 * scale);
            ghostPath.lineTo(44 * scale, 24 * scale);
            ghostPath.cubicTo(44 * scale, 12 * scale, 37 * scale, 2 * scale, 24 * scale, 2 * scale);
            ghostPath.close();
            paint.setColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            canvas.drawPath(ghostPath, paint);

            paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            float eyeRadius = 2.2f * scale;
            canvas.drawCircle(18 * scale, 24 * scale, eyeRadius, paint);
            canvas.drawCircle(30 * scale, 24 * scale, eyeRadius, paint);
            canvas.restore();
        }
    }
}
