package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.google.mlkit.nl.translate.TranslateLanguage;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegaSettings;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Settings for Telega's free on-device message translator. */
public class TelegaTranslatorSettingsActivity extends BaseFragment {

    private TextView targetValue;
    private Switch enabledSwitch;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Переводчик");
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
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(30));
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TextView intro = new TextView(context);
        intro.setText("Переводите входящие и исходящие сообщения. Можно использовать офлайн-перевод или подключённый ИИ-агент Telega.");
        intro.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        intro.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        intro.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(16));
        content.addView(intro, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundWhite)));
        content.addView(group, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout enableRow = new LinearLayout(context);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        group.addView(enableRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        enableRow.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView enableTitle = new TextView(context);
        enableTitle.setText("Автоматический перевод");
        enableTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        enableTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        labels.addView(enableTitle);

        TextView enableSubtitle = new TextView(context);
        enableSubtitle.setText("Переводить новые входящие сообщения в открытых чатах");
        enableSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        enableSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        labels.addView(enableSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        enabledSwitch = new Switch(context);
        enabledSwitch.setChecked(TelegaSettings.isLocalAutoTranslateEnabled());
        enableRow.addView(enabledSwitch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            TelegaSettings.setLocalAutoTranslateEnabled(checked);
            refreshTranslators();
        });
        enableRow.setOnClickListener(v -> enabledSwitch.setChecked(!enabledSwitch.isChecked()));

        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        group.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));

        LinearLayout aiRow = new LinearLayout(context);
        aiRow.setGravity(Gravity.CENTER_VERTICAL);
        aiRow.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        group.addView(aiRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        LinearLayout aiLabels = new LinearLayout(context);
        aiLabels.setOrientation(LinearLayout.VERTICAL);
        aiRow.addView(aiLabels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView aiTitle = new TextView(context);
        aiTitle.setText("Переводить через ИИ");
        aiTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        aiTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        aiLabels.addView(aiTitle);
        TextView aiSubtitle = new TextView(context);
        aiSubtitle.setText(TelegaSettings.isAiConnected()
                ? "Используется подключённый ИИ-агент Telega. Переводы сохраняются на устройстве."
                : "Сначала подключи ИИ-агент в Настройки Telega → Другое.");
        aiSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        aiSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        aiLabels.addView(aiSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        Switch aiSwitch = new Switch(context);
        aiSwitch.setChecked(TelegaSettings.isAiTranslationEnabled());
        aiSwitch.setEnabled(TelegaSettings.isAiConnected() && TelegaSettings.isAiEnabled());
        aiRow.addView(aiSwitch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        aiSwitch.setOnCheckedChangeListener((button, checked) -> {
            TelegaSettings.setAiTranslationEnabled(checked);
            if (checked && !TelegaSettings.isAiTranslationEnabled()) {
                aiSwitch.setChecked(false);
                return;
            }
            refreshTranslators();
        });
        aiRow.setOnClickListener(v -> {
            if (aiSwitch.isEnabled()) aiSwitch.setChecked(!aiSwitch.isChecked());
        });

        View languageDivider = new View(context);
        languageDivider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        group.addView(languageDivider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));

        LinearLayout languageRow = new LinearLayout(context);
        languageRow.setOrientation(LinearLayout.VERTICAL);
        languageRow.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        languageRow.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
        group.addView(languageRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView languageTitle = new TextView(context);
        languageTitle.setText("Язык перевода");
        languageTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        languageTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        languageRow.addView(languageTitle);
        targetValue = new TextView(context);
        targetValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        targetValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        languageRow.addView(targetValue, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        languageRow.setOnClickListener(v -> showLanguagePicker());

        TextView note = new TextView(context);
        note.setText("Офлайн-модель загружается при первом переводе. В режиме ИИ используется подключение из раздела «Другое → ИИ агент», а готовые переводы сохраняются локально.");
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        note.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4), 0);
        content.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateTargetValue();
        fragmentView = root;
        return fragmentView;
    }

    private String getCurrentTargetLanguage() {
        String language = TelegaSettings.getTranslatorTargetLanguage();
        if (TextUtils.isEmpty(language)) {
            language = TranslateController.currentLanguage();
        }
        if (TextUtils.isEmpty(language) || TranslateLanguage.fromLanguageTag(language) == null) {
            language = TranslateLanguage.RUSSIAN;
        }
        return language;
    }

    private void updateTargetValue() {
        if (targetValue == null) return;
        String language = TelegaSettings.getTranslatorTargetLanguage();
        boolean appLanguage = TextUtils.isEmpty(language);
        if (appLanguage) {
            language = getCurrentTargetLanguage();
        }
        Locale locale = Locale.forLanguageTag(language);
        String name = locale.getDisplayName(Locale.getDefault());
        if (TextUtils.isEmpty(name)) name = language;
        targetValue.setText(appLanguage ? name + " · язык приложения" : name);
    }

    private void showLanguagePicker() {
        final List<String> languages = new ArrayList<>(TranslateLanguage.getAllLanguages());
        Collections.sort(languages, (left, right) -> {
            String a = Locale.forLanguageTag(left).getDisplayName(Locale.getDefault());
            String b = Locale.forLanguageTag(right).getDisplayName(Locale.getDefault());
            return a.compareToIgnoreCase(b);
        });

        final String[] labels = new String[languages.size() + 1];
        final String current = getCurrentTargetLanguage();
        int selected = 0;
        labels[0] = "Язык приложения (" + Locale.forLanguageTag(TranslateController.currentLanguage()).getDisplayName(Locale.getDefault()) + ")";
        for (int i = 0; i < languages.size(); i++) {
            String code = languages.get(i);
            labels[i + 1] = Locale.forLanguageTag(code).getDisplayName(Locale.getDefault());
            if (code.equals(current) && !TextUtils.isEmpty(TelegaSettings.getTranslatorTargetLanguage())) {
                selected = i + 1;
            }
        }

        new AlertDialog.Builder(getParentActivity())
                .setTitle("Переводить на")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    TelegaSettings.setTranslatorTargetLanguage(which == 0 ? null : languages.get(which - 1));
                    updateTargetValue();
                    refreshTranslators();
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void refreshTranslators() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            try {
                MessagesController.getInstance(account).getTranslateController().onLocalTranslatorSettingsChanged();
            } catch (Throwable error) {
                FileLog.e(error);
            }
        }
    }
}
