package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** AI translation backed by the configured Telega AI agent, with batched requests and a persistent local result cache. */
public final class TelegaAITranslator {
    private static final String PREFS = "telega_ai_translation_cache";
    private static final int MAX_BATCH = 20;

    private TelegaAITranslator() { }

    public static void translateBatch(List<String> source, String targetLanguage,
                                      Utilities.Callback<ArrayList<String>> callback) {
        final ArrayList<String> input = new ArrayList<>();
        if (source != null) input.addAll(source);
        if (callback == null) return;
        if (input.isEmpty() || TextUtils.isEmpty(targetLanguage)) {
            AndroidUtilities.runOnUIThread(() -> callback.run(null));
            return;
        }
        new Thread(() -> {
            ArrayList<String> output = new ArrayList<>();
            ArrayList<Integer> missingIndexes = new ArrayList<>();
            ArrayList<String> missingTexts = new ArrayList<>();
            SharedPreferences cache = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (int i = 0; i < input.size(); i++) {
                String text = input.get(i) == null ? "" : input.get(i);
                String cached = cache.getString(cacheKey(text, targetLanguage), null);
                output.add(cached);
                if (cached == null && !text.trim().isEmpty()) {
                    missingIndexes.add(i);
                    missingTexts.add(text);
                }
            }
            try {
                for (int start = 0; start < missingTexts.size(); start += MAX_BATCH) {
                    int end = Math.min(start + MAX_BATCH, missingTexts.size());
                    JSONArray batch = new JSONArray();
                    for (int i = start; i < end; i++) batch.put(missingTexts.get(i));
                    String prompt = "Translate every string in the JSON array into language '" + targetLanguage
                            + "'. Preserve meaning, names, emojis and formatting. Return ONLY a valid JSON array of strings, "
                            + "in exactly the same order and with exactly " + batch.length() + " elements. "
                            + "Do not explain or add markdown.\n\n" + batch.toString();
                    String answer = TelegaAIAgent.completeWithSystemPrompt(
                            TelegaSettings.getAiBaseUrl(), TelegaSettings.getAiApiKey(), TelegaSettings.getAiModel(),
                            "You are a precise translation engine. Output only JSON.", prompt);
                    JSONArray translated = parseArray(answer);
                    if (translated.length() != batch.length()) {
                        throw new Exception("ИИ вернул неверное количество переводов");
                    }
                    SharedPreferences.Editor editor = cache.edit();
                    for (int i = 0; i < translated.length(); i++) {
                        String value = translated.optString(i, "").trim();
                        if (value.isEmpty()) throw new Exception("ИИ вернул пустой перевод");
                        int originalIndex = missingIndexes.get(start + i);
                        output.set(originalIndex, value);
                        editor.putString(cacheKey(missingTexts.get(start + i), targetLanguage), value);
                    }
                    editor.apply();
                }
                for (int i = 0; i < output.size(); i++) {
                    if (output.get(i) == null) output.set(i, input.get(i));
                }
                AndroidUtilities.runOnUIThread(() -> callback.run(output));
            } catch (Throwable error) {
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
            }
        }, "TelegaAITranslate").start();
    }

    public static void translate(String text, String targetLanguage, Utilities.Callback<String> callback) {
        ArrayList<String> one = new ArrayList<>();
        one.add(text);
        translateBatch(one, targetLanguage, result -> callback.run(result == null || result.isEmpty() ? null : result.get(0)));
    }

    private static JSONArray parseArray(String raw) throws Exception {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int first = value.indexOf('[');
        int last = value.lastIndexOf(']');
        if (first < 0 || last <= first) {
            JSONObject object = new JSONObject(value);
            JSONArray result = object.optJSONArray("translations");
            if (result == null) result = object.optJSONArray("results");
            if (result == null) throw new Exception("ИИ вернул ответ не в формате JSON-массива");
            return result;
        }
        return new JSONArray(value.substring(first, last + 1));
    }

    // Cache entries are isolated by target language and exact source text, so repeats are never sent again.\n    private static String cacheKey(String text, String language) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((language + "\u0000" + text).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("v1_");
            for (byte b : digest) result.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            return result.toString();
        } catch (Exception e) {
            return "fallback_" + language + "_" + text.hashCode();
        }
    }
}
