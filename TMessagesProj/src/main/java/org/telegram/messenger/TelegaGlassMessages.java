package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Lightweight glass-style highlight drawn on text bubbles, with optional moving reflection. */
public final class TelegaGlassMessages {
    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF bubbleRect = new RectF();
    private static final RectF shineRect = new RectF();
    private static final Path clipPath = new Path();

    static {
        fillPaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.STROKE);
        shinePaint.setStyle(Paint.Style.FILL);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    private TelegaGlassMessages() {
    }

    public static int adjustAlpha(int originalAlpha) {
        if (!TelegaSettings.isGlassMessagesEnabled()) {
            return originalAlpha;
        }
        return Math.max(0, Math.min(255, Math.round(originalAlpha * 0.84f)));
    }

    public static void draw(Canvas canvas, View view, Rect bounds, boolean outgoing) {
        if (!TelegaSettings.isGlassMessagesEnabled() || canvas == null || view == null
                || bounds == null || bounds.width() < AndroidUtilities.dp(24)
                || bounds.height() < AndroidUtilities.dp(14)) {
            return;
        }

        final float d = AndroidUtilities.density;
        bubbleRect.set(bounds.left + d, bounds.top + d, bounds.right - d, bounds.bottom - d);
        final float radius = Math.min(AndroidUtilities.dp(15), bubbleRect.height() * 0.48f);
        final int saveCount = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(bubbleRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        fillPaint.setColor(outgoing ? 0x19FFFFFF : 0x12FFFFFF);
        canvas.drawRoundRect(bubbleRect, radius, radius, fillPaint);
        shineRect.set(bubbleRect.left + AndroidUtilities.dp(3), bubbleRect.top + AndroidUtilities.dp(2),
                bubbleRect.right - AndroidUtilities.dp(3), bubbleRect.top + AndroidUtilities.dp(4));
        shinePaint.setColor(outgoing ? 0x3DFFFFFF : 0x2AFFFFFF);
        canvas.drawRoundRect(shineRect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), shinePaint);
        edgePaint.setStrokeWidth(Math.max(0.6f * d, 0.5f));
        edgePaint.setColor(outgoing ? 0x55FFFFFF : 0x35FFFFFF);
        canvas.drawRoundRect(bubbleRect, radius, radius, edgePaint);

        if (TelegaSettings.isGlassMessagesAnimationEnabled() && view.isShown()) {
            float width = Math.max(AndroidUtilities.dp(18), bubbleRect.width() * 0.13f);
            float phase = (SystemClock.uptimeMillis() % 3000L) / 3000f;
            float left = bubbleRect.left - width + phase * (bubbleRect.width() + width);
            float pulse = 0.5f + 0.5f * (float) Math.sin(SystemClock.uptimeMillis() / 480.0);
            int alpha = Math.round(8 + 16 * pulse);
            shineRect.set(left, bubbleRect.top + AndroidUtilities.dp(2),
                    left + width, bubbleRect.bottom - AndroidUtilities.dp(2));
            shinePaint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawRoundRect(shineRect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), shinePaint);
            view.postInvalidateDelayed(64, bounds.left, bounds.top, bounds.right, bounds.bottom);
        }
        canvas.restoreToCount(saveCount);
    }
}
