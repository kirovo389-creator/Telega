package org.telegram.messenger;

import android.text.TextUtils;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Free on-device translation used by Telega's automatic chat translation.
 * Translation is serialized off the UI thread to avoid CPU spikes while a
 * conversation contains many messages.
 */
public final class TelegaLocalTranslator {
    private static final Object LOCK = new Object();

    // Reuse translators and remember successfully prepared models per language pair.
    private static final HashMap<String, Translator> TRANSLATORS = new HashMap<>();
    private static final Set<String> READY_MODELS = new HashSet<>();

    // A small LRU cache prevents repeated ML Kit work when cells are rebound.
    private static final int MAX_CACHE_ENTRIES = 300;
    private static final LinkedHashMap<String, String> RESULT_CACHE =
            new LinkedHashMap<String, String>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    // Identical requests share one in-flight translation and all receive its result.
    private static final HashMap<String, ArrayList<Utilities.Callback<String>>> IN_FLIGHT = new HashMap<>();

    // Do not run many ML Kit translations simultaneously on the device CPU.
    private static final ExecutorService TRANSLATION_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TelegaLocalTranslate");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

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
        normalized = normalized.toLowerCase(java.util.Locale.US);
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
        synchronized (LOCK) {
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

        final String pairKey = sourceCode + "->" + targetCode;
        final String requestKey = pairKey + "\u0000" + text;
        synchronized (LOCK) {
            String cached = RESULT_CACHE.get(requestKey);
            if (cached != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(cached));
                return;
            }
            ArrayList<Utilities.Callback<String>> waiting = IN_FLIGHT.get(requestKey);
            if (waiting != null) {
                waiting.add(callback);
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(callback);
            IN_FLIGHT.put(requestKey, waiting);
        }

        final Translator translator;
        try {
            translator = getTranslator(sourceCode, targetCode);
        } catch (Exception error) {
            FileLog.e(error);
            complete(requestKey, null);
            return;
        }

        try {
            TRANSLATION_EXECUTOR.execute(() -> {
                String result = null;
                try {
                    boolean modelReady;
                    synchronized (LOCK) {
                        modelReady = READY_MODELS.contains(pairKey);
                    }
                    if (!modelReady) {
                        Tasks.await(translator.downloadModelIfNeeded());
                        synchronized (LOCK) {
                            READY_MODELS.add(pairKey);
                        }
                    }
                    result = Tasks.await(translator.translate(text));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    FileLog.e(error);
                } catch (Exception error) {
                    FileLog.e(error);
                }
                complete(requestKey, result);
            });
        } catch (Exception error) {
            FileLog.e(error);
            complete(requestKey, null);
        }
    }

    private static void complete(String requestKey, String result) {
        final ArrayList<Utilities.Callback<String>> callbacks;
        synchronized (LOCK) {
            callbacks = IN_FLIGHT.remove(requestKey);
            if (!TextUtils.isEmpty(result)) {
                RESULT_CACHE.put(requestKey, result);
            }
        }
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        final String finalResult = result;
        AndroidUtilities.runOnUIThread(() -> {
            for (Utilities.Callback<String> callback : callbacks) {
                try {
                    callback.run(finalResult);
                } catch (Exception error) {
                    FileLog.e(error);
                }
            }
        });
    }
}
