package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.MessageObject;

/**
 * Disabled on purpose.
 *
 * The edit history ("\uD83C\uDF1F Предыдущее содержимое / \uD83C\uDF1F Новое содержимое") is now part of the message text
 * itself, see {@link org.telegram.messenger.TelegaEditedText}. This way the bubble is sized and
 * wrapped by Telegram's own text layout. The class stays only because the ChatMessageCell patch
 * still references it.
 */
public class TelegaEditHistoryHelper {

    public TelegaEditHistoryHelper(View parent) {
    }

    public void bind(MessageObject messageObject, int maxWidth) {
    }

    public int getHeight() {
        return 0;
    }

    public boolean hasContent() {
        return false;
    }

    public int draw(Canvas canvas, float left, float top) {
        return 0;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    public void toggle() {
    }
}
