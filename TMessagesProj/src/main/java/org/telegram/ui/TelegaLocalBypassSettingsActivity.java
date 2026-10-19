package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegaLocalBypassProxy;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

/** Settings for Telega's in-process local Telegram unblock proxy. */
public class TelegaLocalBypassSettingsActivity extends BaseFragment {
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private Context context;
    private TextCheckCell toggle;
    private TextView connectButton;
    private TextView status;

    @Override
    public View createView(Context context) {
        this.context = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Без VPN");
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
        fragmentView = root;

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(32));
        scroll.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        content.addView(sectionTitle("ОБХОД БЛОКИРОВОК"));
        LinearLayout card = newCard(context);
        content.addView(card, lp(MATCH, WRAP, 16, 0, 16, 0));

        toggle = new TextCheckCell(context);
        toggle.setTextAndCheck("Автоматический обход", TelegaSettings.isLocalBypassEnabled(), true);
        toggle.setColorfullIcon(0xFF52798B, R.drawable.telega_ic_wind);
        toggle.setOnClickListener(v -> onToggle());
        card.addView(toggle, lp(MATCH, WRAP, 0, 0, 0, 0));

        connectButton = new TextView(context);
        connectButton.setText("Подключить");
        connectButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        connectButton.setTypeface(Typeface.DEFAULT_BOLD);
        connectButton.setGravity(Gravity.CENTER);
        connectButton.setTextColor(Color.WHITE);
        connectButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13),
                AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        GradientDrawable connectBackground = new GradientDrawable();
        connectBackground.setColor(0xFF3688F5);
        connectBackground.setCornerRadius(AndroidUtilities.dp(12));
        connectButton.setBackground(connectBackground);
        connectButton.setVisibility(TelegaSettings.isLocalBypassEnabled() ? View.VISIBLE : View.GONE);
        connectButton.setOnClickListener(v -> onConnect());
        card.addView(connectButton, lp(MATCH, WRAP, 12, 10, 12, 0));

        status = text(TelegaLocalBypassProxy.getStatusText(), 14, 0.66f);
        status.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        status.setLineSpacing(AndroidUtilities.dp(2), 1f);
        card.addView(status, lp(MATCH, WRAP, 0, 0, 0, 0));

        content.addView(sectionTitle("КАК ЭТО РАБОТАЕТ"));
        LinearLayout details = newCard(context);
        details.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        content.addView(details, lp(MATCH, WRAP, 16, 0, 16, 0));
        TextView description = text(
                "Telega поднимает локальный SOCKS5-прокси внутри собственного процесса " +
                "и направляет через него соединения Telegram по защищённому WebSocket-транспорту.\n\n" +
                "Отдельный VPN не создаётся. MordaTG не устанавливается и не запускается, " +
                "а его уведомления и фоновые службы не используются. При выключении режима " +
                "восстанавливаются обычные настройки прокси Telega.",
                14, 0.82f);
        description.setLineSpacing(AndroidUtilities.dp(3), 1f);
        details.addView(description, lp(MATCH, WRAP, 0, 0, 0, 0));

        TextView note = text(
                "Доступность обходных маршрутов зависит от текущей сети и блокировок. " +
                "Если соединение не установится, выключи режим и проверь обычные настройки прокси.",
                13, 0.55f);
        note.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(12),
                AndroidUtilities.dp(32), AndroidUtilities.dp(8));
        note.setLineSpacing(AndroidUtilities.dp(2), 1f);
        content.addView(note, lp(MATCH, WRAP, 0, 0, 0, 0));
        return fragmentView;
    }

    private void onToggle() {
        if (toggle == null) return;
        boolean enabled = !TelegaSettings.isLocalBypassEnabled();
        boolean succeeded = TelegaLocalBypassProxy.setEnabled(enabled);
        boolean actual = succeeded && enabled;
        toggle.setChecked(actual);
        if (connectButton != null) {
            connectButton.setVisibility(actual ? View.VISIBLE : View.GONE);
            connectButton.setText("Подключить");
            connectButton.setEnabled(true);
        }
        if (!succeeded) {
            if (status != null) {
                status.setText(TelegaLocalBypassProxy.getStatusText());
            }
            toast("Не удалось запустить локальный прокси. Обычные настройки сохранены.", true);
        } else if (enabled) {
            if (status != null) {
                status.setText("Проверяю WSS-маршрут и сессию Telegram…");
            }
            TelegaLocalBypassProxy.checkLocalEndpoint(TelegaLocalBypassProxy.getPort(), elapsed -> {
                if (!TelegaSettings.isLocalBypassEnabled()) return;
                if (elapsed <= 0) {
                    String routeError = TelegaLocalBypassProxy.getLastRouteError();
                    String failure = "Маршрут Telegram не подтверждён. Обход выключен, чтобы не показывать ложное подключение."
                            + (routeError == null || routeError.isEmpty() ? " Проверь сеть и повтори подключение."
                            : "\nПричина: " + routeError);
                    TelegaLocalBypassProxy.setEnabled(false);
                    if (toggle != null) toggle.setChecked(false);
                    if (connectButton != null) {
                        connectButton.setVisibility(View.VISIBLE);
                        connectButton.setText("Подключить");
                    }
                    if (status != null) status.setText(failure);
                    if (context != null) toast("Маршрут Telegram не ответил; обход выключен.", true);
                    return;
                }
                if (status == null || context == null) return;
                confirmNativeSession(TelegaLocalBypassProxy.getPort(), elapsed, 0);
            });
            toast("Обход включён; проверяю соединение с Telegram", false);
        } else {
            if (status != null) status.setText(TelegaLocalBypassProxy.getStatusText());
            toast("Обход блокировок выключен", false);
        }
    }

    private void onConnect() {
        if (connectButton == null || context == null) {
            return;
        }
        connectButton.setEnabled(false);
        connectButton.setText("Подключение…");
        boolean connected = TelegaSettings.isLocalBypassEnabled() && TelegaLocalBypassProxy.isRunning();
        if (!connected) connected = TelegaLocalBypassProxy.setEnabled(true);
        if (toggle != null) {
            toggle.setChecked(connected);
        }
        if (!connected) {
            connectButton.setEnabled(true);
            connectButton.setText("Подключить");
            connectButton.setVisibility(View.VISIBLE);
            if (status != null) {
                status.setText(TelegaLocalBypassProxy.getStatusText());
            }
            toast("Не удалось запустить локальный прокси.", true);
            return;
        }

        connectButton.setVisibility(View.VISIBLE);
        final int probePort = TelegaLocalBypassProxy.getPort();
        if (probePort <= 0) {
            connectButton.setEnabled(true);
            connectButton.setText("Подключить");
            if (status != null) status.setText("Локальный прокси не запущен. Включи обход и повтори проверку.");
            toast("Локальный прокси не запущен.", true);
            return;
        }
        if (status != null) {
            status.setText("Проверяю WSS-маршрут и сессию Telegram…");
        }
        TelegaLocalBypassProxy.checkLocalEndpoint(probePort, elapsed -> {
            if (!TelegaSettings.isLocalBypassEnabled()) return;
            if (elapsed <= 0) {
                String routeError = TelegaLocalBypassProxy.getLastRouteError();
                String failure = "Маршрут Telegram не ответил. Обход выключен, чтобы не показывать ложное подключение."
                        + (routeError == null || routeError.isEmpty() ? " Проверь сеть и повтори подключение."
                        : "\nПричина: " + routeError);
                TelegaLocalBypassProxy.setEnabled(false);
                if (toggle != null) toggle.setChecked(false);
                if (connectButton != null) {
                    connectButton.setEnabled(true);
                    connectButton.setVisibility(View.VISIBLE);
                    connectButton.setText("Подключить");
                }
                if (status != null) status.setText(failure);
                if (context != null) toast("Маршрут Telegram не ответил; обход выключен.", true);
                return;
            }
            if (connectButton == null || context == null) return;
            confirmNativeSession(probePort, elapsed, 0);
        });
    }

    private void confirmNativeSession(int port, long handshakeMillis, int attempt) {
        if (!TelegaSettings.isLocalBypassEnabled() || !TelegaLocalBypassProxy.isRunning()) return;
        int state = org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).getConnectionState();
        if (state == org.telegram.tgnet.ConnectionsManager.ConnectionStateConnected
                || state == org.telegram.tgnet.ConnectionsManager.ConnectionStateUpdating) {
            if (connectButton != null) {
                connectButton.setText("Подключено");
                connectButton.setEnabled(false);
            }
            if (status != null) status.setText("Сессия Telegram подключена через 127.0.0.1:" + port
                    + ". WSS-рукопожатие: " + handshakeMillis + " мс (это не ping Telegram).");
            return;
        }
        if (attempt < 20) {
            AndroidUtilities.runOnUIThread(() -> confirmNativeSession(port, handshakeMillis, attempt + 1), 500);
            return;
        }
        String failure = "WSS-маршрут ответил, но сессия Telegram не подключилась. Обход выключен, чтобы не показывать ложный статус.";
        TelegaLocalBypassProxy.setEnabled(false);
        if (toggle != null) toggle.setChecked(false);
        if (connectButton != null) {
            connectButton.setVisibility(View.VISIBLE);
            connectButton.setEnabled(true);
            connectButton.setText("Подключить");
        }
        if (status != null) status.setText(failure);
        if (context != null) toast(failure, true);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        toggle = null;
        connectButton = null;
        status = null;
        context = null;
    }

    private static LinearLayout newCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        card.setBackground(background);
        return card;
    }

    private static LinearLayout.LayoutParams lp(int width, int height,
                                                 int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(AndroidUtilities.dp(left), AndroidUtilities.dp(top),
                AndroidUtilities.dp(right), AndroidUtilities.dp(bottom));
        return params;
    }

    private static TextView sectionTitle(String value) {
        TextView view = new TextView(org.telegram.messenger.ApplicationLoader.applicationContext);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        view.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(10),
                AndroidUtilities.dp(32), AndroidUtilities.dp(8));
        return view;
    }

    private static TextView text(String value, int sizeDp, float alpha) {
        TextView view = new TextView(org.telegram.messenger.ApplicationLoader.applicationContext);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        view.setTextColor(Theme.multAlpha(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), alpha));
        return view;
    }

    private void toast(String value, boolean longDuration) {
        try {
            Toast.makeText(context, value,
                    longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }
}
