package org.telegram.messenger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renames the word "Telegram" to "Telega" in interface strings.
 * Links (telegram.org, t.me/..., @Telegram) are left untouched.
 */
public final class TelegaBrand {

    private static final Pattern NAME = Pattern.compile("(?<![/@._\\w])Telegram(?!\\w)");

    private TelegaBrand() {
    }

    public static String replace(String value) {
        if (value == null || value.indexOf("Telegram") < 0) {
            return value;
        }
        return NAME.matcher(value).replaceAll("Telega");
    }

    /** Rebrands all values of a loaded language pack in place. */
    public static HashMap<String, String> rebrand(HashMap<String, String> map) {
        if (map == null) {
            return null;
        }
        try {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String v = entry.getValue();
                if (v != null && v.indexOf("Telegram") >= 0) {
                    entry.setValue(replace(v));
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return map;
    }
}
