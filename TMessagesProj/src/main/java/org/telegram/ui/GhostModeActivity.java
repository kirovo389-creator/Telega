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

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Призрак");

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        content.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));

        TextView title = new TextView(context);
        title.setText("Невидимость в историях");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText("Включает серверный режим скрытого просмотра историй: просмотренные истории не должны появляться у автора в списке просмотров. Действие ограничено Telegram и автоматически заканчивается по таймеру сервера. Реакции и ответы могут раскрыть просмотр.");
        description.setTextSize(14);
        description.setLineSpacing(dp(2), 1f);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams descriptionParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        descriptionParams.topMargin = dp(10);
        content.addView(description, descriptionParams);

        statusView = new TextView(context);
        statusView.setText("Режим не активирован");
        statusView.setTextSize(14);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        LinearLayout.LayoutParams statusParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        statusParams.topMargin = dp(18);
        content.addView(statusView, statusParams);

        activateButton = new TextView(context);
        activateButton.setText("Включить невидимость");
        activateButton.setTextSize(15);
        activateButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        activateButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        activateButton.setGravity(Gravity.CENTER);
        activateButton.setPadding(dp(16), dp(13), dp(16), dp(13));
        activateButton.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton)));
        activateButton.setOnClickListener(this::activateStealthMode);
        LinearLayout.LayoutParams buttonParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        buttonParams.topMargin = dp(20);
        content.addView(activateButton, buttonParams);

        TextView limitation = new TextView(context);
        limitation.setText("Важно: это включает только штатную невидимость историй. Скрытие прочтения обычных сообщений, статуса «в сети» и индикатора набора текста требует отдельных изменений сетевой логики и здесь пока не заявляется как работающая функция.");
        limitation.setTextSize(13);
        limitation.setLineSpacing(dp(2), 1f);
        limitation.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams limitationParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        limitationParams.topMargin = dp(18);
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
        if (getParentActivity() == null || activateButton == null) {
            return;
        }
        activateButton.setEnabled(false);
        activateButton.setAlpha(0.6f);

        TL_stories.TL_stories_activateStealthMode request = new TL_stories.TL_stories_activateStealthMode();
        request.past = true;
        request.future = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (activateButton == null) {
                        return;
                    }
                    activateButton.setEnabled(true);
                    activateButton.setAlpha(1f);
                    if (response instanceof TLRPC.Updates) {
                        TLRPC.Updates updates = (TLRPC.Updates) response;
                        MessagesController.getInstance(currentAccount).processUpdateArray(
                                updates.updates, updates.users, updates.chats, false, updates.date);
                        statusView.setText("Запрос принят сервером");
                        activateButton.setText("Активировать повторно");
                        Toast.makeText(getParentActivity(), "Невидимость историй включена", Toast.LENGTH_SHORT).show();
                    } else {
                        String message = error != null && error.text != null
                                ? error.text
                                : "Не удалось включить режим. Возможно, действует ограничение по времени.";
                        statusView.setText(message);
                        Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                    }
                }));
    }

    @Override
    public void onFragmentDestroy() {
        statusView = null;
        activateButton = null;
        super.onFragmentDestroy();
    }
}
