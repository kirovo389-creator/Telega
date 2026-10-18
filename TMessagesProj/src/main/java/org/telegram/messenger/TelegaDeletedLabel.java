package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;

/**
 * Time text of a deleted message: red trash icon + red "Удалено".
 * Both are part of the time layout itself, so there is exactly one label and it can not be misaligned.
 */
public final class TelegaDeletedLabel {

    private static final int RED = Color.rgb(226, 58, 65);
    private static Drawable trash;

    private TelegaDeletedLabel() {
    }

    public static CharSequence label() {
        SpannableStringBuilder sb = new SpannableStringBuilder("    \u0423\u0434\u0430\u043b\u0435\u043d\u043e");
        sb.setSpan(new TrashSpan(), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(RED), 4, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static Drawable trashIcon() {
        if (trash == null) {
            trash = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.msg_delete).mutate();
            trash.setColorFilter(RED, PorterDuff.Mode.SRC_IN);
        }
        return trash;
    }

    /** Replaces the four leading spaces with the trash icon (same width, so measuring stays correct). */
    private static final class TrashSpan extends ReplacementSpan {
        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return (int) Math.ceil(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Drawable icon = trashIcon();
            int size = Math.round(paint.getTextSize() * 0.95f);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float centerY = y + (fm.ascent + fm.descent) / 2f;
            int left = Math.round(x + AndroidUtilities.dp(1));
            int iconTop = Math.round(centerY - size / 2f);
            icon.setBounds(left, iconTop, left + size, iconTop + size);
            icon.draw(canvas);
        }
    }
}
