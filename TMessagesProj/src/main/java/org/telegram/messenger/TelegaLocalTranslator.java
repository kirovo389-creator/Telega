package org.telegram.messenger;

import android.text.TextUtils;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.Locale;

/**
 * Free on-device translation used by Telega's automatic chat translation.
 * Language models are downloaded by ML Kit and kept on the device.
 */
public final class TelegaLocalTranslator {
    // Reuse translators for frequently used language pairs instead of creating and closing one
    // for every single message in an automatically translated chat.
    private static final HashMap<String, Translator> TRANSLATORS = new HashMap<>();

    private TelegaLocalTranslator() {
    }

    public static String normalizeLanguage(String language) {
        if (TextUtils.isEmpty(language)) {
            return null;
        }
        String normalized = language.trim().replace('_', '-');
        int separator = normalized.indexOf('-');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator);
        }
        normalized = normalized.toLowerCase(Locale.US);
        if ("nb".equals(normalized)) {
            normalized = "no";
        } else if ("iw".equals(normalized)) {
            normalized = "he";
        } else if ("in".equals(normalized)) {
            normalized = "id";
        } else if ("ji".equals(normalized)) {
            normalized = "yi";
        }
        return normalized;
    }

    public static void translate(String text, String sourceLanguage, String targetLanguage, Utilities.Callback<String> callback) {
        if (callback == null) {
            return;
        }
        if (TextUtils.isEmpty(text)) {
            AndroidUtilities.runOnUIThread(() -> callback.run(null));
            return;
        }

        final String target = normalizeLanguage(targetLanguage);
        if (target == null || "und".equals(target)) {
            AndroidUtilities.runOnUIThread(() -> callback.run(null));
            return;
        }

        String source = normalizeLanguage(sourceLanguage);
        if (source == null || "und".equals(source)) {
            LanguageDetector.detectLanguage(text, detected -> {
                String language = normalizeLanguage(detected);
                if (language == null || "und".equals(language)) {
                    AndroidUtilities.runOnUIThread(() -> callback.run(null));
                } else {
                    translateKnownLanguage(text, language, target, callback);
                }
            }, error -> AndroidUtilities.runOnUIThread(() -> callback.run(null)));
            return;
        }
        translateKnownLanguage(text, source, target, callback);
    }

    private static Translator getTranslator(String sourceCode, String targetCode) {
        String key = sourceCode + "->" + targetCode;
        synchronized (TRANSLATORS) {
            Translator translator = TRANSLATORS.get(key);
            if (translator == null) {
                TranslatorOptions options = new TranslatorOptions.Builder()
                        .setSourceLanguage(sourceCode)
                        .setTargetLanguage(targetCode)
                        .build();
                translator = Translation.getClient(options);
                TRANSLATORS.put(key, translator);
            }
            return translator;
        }
    }

    private static void translateKnownLanguage(String text, String source, String target, Utilities.Callback<String> callback) {
        if (TextUtils.equals(source, target)) {
            AndroidUtilities.runOnUIThread(() -> callback.run(text));
            return;
        }

        final String sourceCode = TranslateLanguage.fromLanguageTag(source);
        final String targetCode = TranslateLanguage.fromLanguageTag(target);
        if (sourceCode == null || targetCode == null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(null));
            return;
        }

        final Translator translator;
        try {
            translator = getTranslator(sourceCode, targetCode);
        } catch (Exception error) {
            FileLog.e(error);
            AndroidUtilities.runOnUIThread(() -> callback.run(null));
            return;
        }

        translator.downloadModelIfNeeded()
                .addOnSuccessListener(ignored -> translator.translate(text)
                        .addOnCompleteListener(task -> {
                            String result = task.isSuccessful() ? task.getResult() : null;
                            if (!task.isSuccessful() && task.getException() != null) {
                                FileLog.e(task.getException());
                            }
                            AndroidUtilities.runOnUIThread(() -> callback.run(result));
                        }))
                .addOnFailureListener(error -> {
                    FileLog.e(error);
                    AndroidUtilities.runOnUIThread(() -> callback.run(null));
                });
    }
}
