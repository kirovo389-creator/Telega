package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Keeps a small, private on-device copy of messages before they are removed
 * from the normal chat history. These copies are never uploaded to a server.
 */
public final class TelegaDeletedMessagesController {
    private static final String PREFERENCES = "telega_settings";
    private static final String KEEP_KEY = "keep_deleted_messages";
    private static final String DATA_PREFIX = "telega_deleted_messages_";
    private static final int MAX_MESSAGES_PER_DIALOG = 200;

    private TelegaDeletedMessagesController() {
    }

    public static boolean isEnabled() {
        Context context = ApplicationLoader.applicationContext;
        return context != null && context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEEP_KEY, true);
    }

    public static void saveMessage(int account, long dialogId, TLRPC.Message message) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || message == null || message.id <= 0 || !isEnabled()) {
            return;
        }

        try {
            SerializedData serialized = new SerializedData();
            message.serializeToStream(serialized);
            String encoded = Base64.encodeToString(serialized.toByteArray(), Base64.NO_WRAP);
            serialized.cleanup();

            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            String key = DATA_PREFIX + dialogId;
            JSONArray current = new JSONArray(preferences.getString(key, "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < current.length(); i++) {
                JSONObject entry = current.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                if (entry.optInt("id", 0) == message.id) {
                    // Prefer the first copy captured before deletion; it may be newer than the DB row.
                    if (!entry.optString("data").isEmpty()) {
                        String existingAttachPath = entry.optString("attachPath", "");
                        if (existingAttachPath.isEmpty() && message.attachPath != null && !message.attachPath.isEmpty()) {
                            entry.put("attachPath", message.attachPath);
                            preferences.edit().putString(key, current.toString()).apply();
                        }
                        return;
                    }
                    continue;
                }
                updated.put(entry);
            }

            JSONObject entry = new JSONObject();
            entry.put("id", message.id);
            entry.put("date", message.date);
            entry.put("data", encoded);
            if (message.attachPath != null && !message.attachPath.isEmpty()) {
                entry.put("attachPath", message.attachPath);
            }
            updated.put(entry);

            while (updated.length() > MAX_MESSAGES_PER_DIALOG) {
                updated.remove(0);
            }
            preferences.edit().putString(key, updated.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static ArrayList<MessageObject> loadMessages(int account, long dialogId) {
        ArrayList<MessageObject> result = new ArrayList<>();
        Context context = ApplicationLoader.applicationContext;
        if (context == null || !isEnabled()) {
            return result;
        }

        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            JSONArray stored = new JSONArray(preferences.getString(DATA_PREFIX + dialogId, "[]"));
            MessagesController controller = MessagesController.getInstance(account);
            for (int i = 0; i < stored.length(); i++) {
                JSONObject entry = stored.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                try {
                    byte[] bytes = Base64.decode(entry.getString("data"), Base64.DEFAULT);
                    SerializedData serialized = new SerializedData(bytes);
                    int constructor = serialized.readInt32(true);
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(serialized, constructor, true);
                    serialized.cleanup();
                    if (message == null || message.id <= 0) {
                        continue;
                    }
                    // dialog_id and attachPath are not guaranteed to be in TL serialization.
                    message.dialog_id = dialogId;
                    String attachPath = entry.optString("attachPath", "");
                    if (!attachPath.isEmpty()) {
                        message.attachPath = attachPath;
                    }

                    MessageObject object = new MessageObject(account, message, controller.getUsers(), controller.getChats(), true, false);
                    object.deleted = true;
                    object.telegaDeleted = true;
                    result.add(object);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }
}
