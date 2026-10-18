package org.telegram.messenger;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

import org.telegram.tgnet.TLRPC;

import java.util.List;

/**
 * Builds the text of an edited message:
 *
 *   \uD83C\uDF1F Предыдущее содержимое:
 *   <previous text>
 *
 *   \uD83C\uDF1F Новое содержимое:
 *   <current text>
 *
 * Used by the wrapper around MessageObject.generateLayout (see scripts/patch_edit_text.py).
 * Only the layout is built from this text; MessageObject.messageText is restored afterwards,
 * so the chat list preview, copy and forward keep working with the real text.
 */
public final class TelegaEditedText {

    private static final String TITLE_PREVIOUS = "\uD83C\uDF1F Предыдущее содержимое";
    private static final String TITLE_NEW = "\uD83C\uDF1F Новое содержимое:";
    private static final int MAX_PREVIOUS = 1000;

    private TelegaEditedText() {
    }

    /** @return text with the previous version in front, or the same object if there is nothing to show. */
    public static CharSequence wrap(MessageObject mo, CharSequence text) {
        try {
            if (mo == null || text == null || text.length() == 0 || mo.messageOwner == null) {
                return text;
            }
            if (!TelegaSettings.isShowEditedMessages()) {
                return text;
            }
            TLRPC.MessageMedia media = mo.messageOwner.media;
            if (media != null && !(media instanceof TLRPC.TL_messageMediaEmpty) && !(media instanceof TLRPC.TL_messageMediaWebPage)) {
                return text;
            }
            if (text.toString().startsWith(TITLE_PREVIOUS)) {
                return text;
            }
            List<String> history = TelegaEditedMessages.getHistory(mo.currentAccount, mo.getDialogId(), mo.getId());
            if (history == null || history.isEmpty()) {
                return text;
            }
            String previous = history.get(history.size() - 1);
            if (previous.length() > MAX_PREVIOUS) {
                previous = previous.substring(0, MAX_PREVIOUS) + "…";
            }

            SpannableStringBuilder sb = new SpannableStringBuilder();
            String prevTitle = history.size() > 1
                    ? TITLE_PREVIOUS + " (правок: " + history.size() + "):"
                    : TITLE_PREVIOUS + ":";
            int start = sb.length();
            sb.append(prevTitle);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append('\n').append(previous).append("\n\n");
            start = sb.length();
            sb.append(TITLE_NEW);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append('\n');
            sb.append(text);
            return sb;
        } catch (Throwable t) {
            FileLog.e(t);
            return text;
        }
    }

    /** Entities of the real message are relative to the real text, so move them by the added prefix. */
    public static void shiftEntities(TLRPC.Message message, int delta) {
        if (message == null || message.entities == null) {
            return;
        }
        for (int i = 0; i < message.entities.size(); i++) {
            message.entities.get(i).offset += delta;
        }
    }
}
