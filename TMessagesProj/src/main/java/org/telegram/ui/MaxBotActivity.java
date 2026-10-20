package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.max.MaxBotApiClient;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MAX integration through the official Bot API.
 *
 * The bot token is held in memory only; it is not persisted or written to logs.
 * This does not sign in to a personal MAX account.
 */
public class MaxBotActivity extends BaseFragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText tokenInput;
    private EditText targetInput;
    private EditText messageInput;
    private RadioGroup targetTypeGroup;
    private RadioButton userTargetButton;
    private TextView botInfoView;
    private TextView statusView;
    private TextView updatesView;
    private TextView verifyButton;
    private TextView updatesButton;
    private TextView historyButton;
    private TextView sendButton;

    private Long updateMarker;
    private boolean requestInFlight;

    private interface ApiOperation {
        JSONObject execute(String token) throws Exception;
    }

    private interface ApiResult {
        void onSuccess(JSONObject response);
    }

    @Override
    public View createView(Context context) {
        if (actionBar != null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle("MAX Bot API");
        }

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView intro = addLabel(content,
                "Подключение работает через официальный MAX Bot API. Создай бота в панели MAX для разработчиков и вставь его токен. Это не вход в личный аккаунт MAX.");
        intro.setTextSize(14);
        intro.setLineSpacing(dp(2), 1f);
        intro.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        tokenInput = addField(content, "Токен MAX-бота", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setSingleLine(true);
        tokenInput.setAutofillHints((String[]) null);

        verifyButton = addButton(content, "Проверить токен");
        verifyButton.setOnClickListener(v -> verifyToken());

        botInfoView = addLabel(content, "Бот не подключён");
        botInfoView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        addSectionTitle(content, "Чат для отправки сообщения");
        targetInput = addField(content, "ID пользователя, чата или канала",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        targetInput.setSingleLine(true);

        targetTypeGroup = new RadioGroup(context);
        targetTypeGroup.setOrientation(RadioGroup.VERTICAL);
        userTargetButton = new RadioButton(context);
        userTargetButton.setText("Отправлять пользователю (user_id)");
        userTargetButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        userTargetButton.setId(View.generateViewId());
        targetTypeGroup.addView(userTargetButton, new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));
        RadioButton chatTargetButton = new RadioButton(context);
        chatTargetButton.setText("Отправлять в групповой чат/канал (chat_id)");
        chatTargetButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        chatTargetButton.setId(View.generateViewId());
        targetTypeGroup.addView(chatTargetButton, new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));
        targetTypeGroup.check(userTargetButton.getId());
        content.addView(targetTypeGroup, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        messageInput = addField(content, "Текст сообщения (до 4000 символов)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageInput.setMinLines(3);
        messageInput.setGravity(Gravity.TOP | Gravity.START);

        sendButton = addButton(content, "Отправить через MAX");
        sendButton.setOnClickListener(v -> sendMessage());

        addSectionTitle(content, "Данные бота");
        historyButton = addButton(content, "Загрузить историю указанного чата");
        historyButton.setOnClickListener(v -> loadHistory());

        updatesButton = addButton(content, "Получить обновления бота");
        updatesButton.setOnClickListener(v -> loadUpdates());

        TextView updateHint = addLabel(content,
                "Обновления через Long Polling предназначены MAX для разработки и тестирования. Для рабочих интеграций MAX рекомендует Webhook. В группах и каналах боту могут потребоваться права администратора.");
        updateHint.setTextSize(13);
        updateHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        statusView = addLabel(content, "Готово. Токен не сохраняется на устройстве.");
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        updatesView = addLabel(content, "");
        updatesView.setTextSize(13);
        updatesView.setTextIsSelectable(true);

        fragmentView = root;
        return fragmentView;
    }

    private TextView addSectionTitle(LinearLayout parent, String text) {
        TextView view = addLabel(parent, text);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextSize(16);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = dp(18);
        params.bottomMargin = dp(4);
        view.setLayoutParams(params);
        return view;
    }

    private TextView addLabel(LinearLayout parent, String text) {
        TextView view = new TextView(parent.getContext());
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        LinearLayout.LayoutParams params = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        parent.addView(view, params);
        return view;
    }

    private EditText addField(LinearLayout parent, String hint, int inputType) {
        EditText field = new EditText(parent.getContext());
        field.setHint(hint);
        field.setInputType(inputType);
        field.setTextSize(15);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        field.setBackground(Theme.createRoundRectDrawable(dp(10),
                Theme.getColor(Theme.key_windowBackgroundWhite)));
        LinearLayout.LayoutParams params = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        parent.addView(field, params);
        return field;
    }

    private TextView addButton(LinearLayout parent, String text) {
        TextView button = new TextView(parent.getContext());
        button.setText(text);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(46));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setBackground(Theme.createRoundRectDrawable(dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton)));
        LinearLayout.LayoutParams params = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        params.topMargin = dp(4);
        params.bottomMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    private String readToken() {
        String token = tokenInput == null ? "" : tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            showStatus("Сначала вставь токен MAX-бота.");
            return null;
        }
        return token;
    }

    private void verifyToken() {
        execute("Проверяю токен…", MaxBotApiClient::getMe, response -> {
            String name = response.optString("name", "MAX-бот");
            String username = response.optString("username", "");
            botInfoView.setText(username.isEmpty() ? "Подключён: " + name
                    : "Подключён: " + name + " (@" + username + ")");
            showStatus("Токен принят MAX API.");
        });
    }

    private void loadUpdates() {
        execute("Получаю обновления…", token -> MaxBotApiClient.getUpdates(token, updateMarker), response -> {
            if (response.has("marker") && !response.isNull("marker")) {
                updateMarker = response.optLong("marker", -1L);
            }
            JSONArray updates = response.optJSONArray("updates");
            if (updates == null || updates.length() == 0) {
                updatesView.setText("Новых обновлений нет.");
                showStatus("Запрос обновлений выполнен.");
                return;
            }

            StringBuilder output = new StringBuilder();
            for (int i = 0; i < updates.length(); i++) {
                JSONObject update = updates.optJSONObject(i);
                if (update == null) {
                    continue;
                }
                if (output.length() > 0) {
                    output.append("\n\n");
                }
                String type = update.optString("update_type", "event");
                output.append(type);

                JSONObject message = update.optJSONObject("message");
                long userId = 0;
                long chatId = update.optLong("chat_id", 0);
                String text = "";
                if (message != null) {
                    JSONObject sender = message.optJSONObject("sender");
                    JSONObject recipient = message.optJSONObject("recipient");
                    if (sender != null) {
                        userId = sender.optLong("user_id", 0);
                    }
                    if (recipient != null) {
                        chatId = recipient.optLong("chat_id", chatId);
                        if (userId == 0) {
                            userId = recipient.optLong("user_id", 0);
                        }
                    }
                    JSONObject body = message.optJSONObject("body");
                    if (body != null) {
                        text = body.optString("text", "");
                    }
                }

                if (chatId != 0) {
                    output.append("\nchat_id: ").append(chatId);
                    targetInput.setText(Long.toString(chatId));
                    targetTypeGroup.check(targetTypeGroup.getChildAt(1).getId());
                } else if (userId != 0) {
                    output.append("\nuser_id: ").append(userId);
                    targetInput.setText(Long.toString(userId));
                    targetTypeGroup.check(userTargetButton.getId());
                }
                if (!TextUtils.isEmpty(text)) {
                    output.append("\n").append(text);
                } else {
                    output.append("\n").append(update.toString());
                }
            }
            updatesView.setText(output.toString());
            showStatus("Получено событий: " + updates.length()
                    + (updateMarker == null ? "" : ". Следующий marker сохранён."));
        });
    }

    private void loadHistory() {
        Long target = parseTarget();
        if (target == null) {
            return;
        }
        execute("Загружаю сообщения…", token -> MaxBotApiClient.getMessages(token, target, 50), response -> {
            JSONArray messages = response.optJSONArray("messages");
            if (messages == null || messages.length() == 0) {
                updatesView.setText("История пуста или MAX не вернул сообщения для этого ID.");
                showStatus("История получена.");
                return;
            }

            StringBuilder output = new StringBuilder();
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject message = messages.optJSONObject(i);
                if (message == null) {
                    continue;
                }
                JSONObject sender = message.optJSONObject("sender");
                JSONObject body = message.optJSONObject("body");
                String author = sender == null ? "Канал/бот"
                        : sender.optString("name", sender.optString("user_id", "пользователь"));
                String text = body == null ? "" : body.optString("text", "");
                if (output.length() > 0) {
                    output.append("\n\n");
                }
                output.append(author).append(": ").append(text.isEmpty()
                        ? "(сообщение без текста)" : text);
            }
            updatesView.setText(output.toString());
            showStatus("Загружено сообщений: " + messages.length());
        });
    }

    private Long parseTarget() {
        if (targetInput == null) {
            return null;
        }
        String value = targetInput.getText().toString().trim();
        try {
            long id = Long.parseLong(value);
            if (id == 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            showStatus("Укажи корректный числовой ID пользователя или чата.");
            return null;
        }
    }

    private void sendMessage() {
        Long target = parseTarget();
        if (target == null) {
            return;
        }
        String text = messageInput == null ? "" : messageInput.getText().toString();
        boolean userTarget = targetTypeGroup.getCheckedRadioButtonId() == userTargetButton.getId();
        execute("Отправляю сообщение…",
                token -> MaxBotApiClient.sendMessage(token, target, userTarget, text),
                response -> {
                    JSONObject message = response.optJSONObject("message");
                    String id = message == null ? "" : message.optString("message_id", "");
                    showStatus(id.isEmpty() ? "MAX принял запрос на отправку."
                            : "Сообщение отправлено. ID: " + id);
                    messageInput.setText("");
                });
    }

    private void execute(String progress, ApiOperation operation, ApiResult resultHandler) {
        if (requestInFlight) {
            return;
        }
        final String token = readToken();
        if (token == null) {
            return;
        }
        requestInFlight = true;
        setButtonsEnabled(false);
        showStatus(progress);

        executor.execute(() -> {
            try {
                JSONObject response = operation.execute(token);
                AndroidUtilities.runOnUIThread(() -> {
                    if (fragmentView == null) {
                        return;
                    }
                    requestInFlight = false;
                    setButtonsEnabled(true);
                    if (resultHandler != null) {
                        resultHandler.onSuccess(response);
                    } else {
                        showStatus("Готово.");
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                String error = e.getMessage();
                if (TextUtils.isEmpty(error)) {
                    error = "Не удалось выполнить запрос к MAX API.";
                }
                final String errorText = error;
                AndroidUtilities.runOnUIThread(() -> {
                    if (fragmentView == null) {
                        return;
                    }
                    requestInFlight = false;
                    setButtonsEnabled(true);
                    showStatus(errorText);
                    Toast.makeText(getParentActivity(), errorText, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        if (verifyButton != null) verifyButton.setEnabled(enabled);
        if (updatesButton != null) updatesButton.setEnabled(enabled);
        if (historyButton != null) historyButton.setEnabled(enabled);
        if (sendButton != null) sendButton.setEnabled(enabled);
    }

    private void showStatus(String text) {
        if (statusView != null) {
            statusView.setText(text);
        }
    }

    @Override
    public void onFragmentDestroy() {
        requestInFlight = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        if (tokenInput != null) {
            tokenInput.setText("");
        }
        tokenInput = null;
        targetInput = null;
        messageInput = null;
        botInfoView = null;
        statusView = null;
        updatesView = null;
        verifyButton = null;
        updatesButton = null;
        historyButton = null;
        sendButton = null;
        super.onFragmentDestroy();
    }
}
