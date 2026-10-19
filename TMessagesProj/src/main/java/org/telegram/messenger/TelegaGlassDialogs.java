package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Lightweight frosted-glass surfaces for closed conversations in the dialog list. */
public final class TelegaGlassDialogs {
    private static final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF rect = new RectF();
    private static int shaderWidth = -1;
    private static int shaderHeight = -1;
    private static LinearGradient surfaceShader;

    static {
        surface.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 0.7f));
        shine.setStyle(Paint.Style.STROKE);
        shine.setStrokeCap(Paint.Cap.ROUND);
        shine.setStrokeWidth(Math.max(1f, AndroidUtilities.density));
    }

    private TelegaGlassDialogs() {
    }

    public static void draw(Canvas canvas, View view, int left, int top, int right, int bottom) {
        if (canvas == null || view == null || right <= left || bottom <= top) {
            return;
        }
        int width = right - left;
        int height = bottom - top;
        if (width != shaderWidth || height != shaderHeight || surfaceShader == null) {
            shaderWidth = width;
            shaderHeight = height;
            surfaceShader = new LinearGradient(0, top, 0, bottom,
                    new int[]{0x24FFFFFF, 0x0BFFFFFF, 0x19D9EEFF},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP);
        }
        rect.set(left, top, right, bottom);
        float radius = Math.min(AndroidUtilities.dp(18), height * 0.28f);
        int save = canvas.save();
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint());
        surface.setShader(surfaceShader);
        canvas.drawRoundRect(rect, radius, radius, surface);
        surface.setShader(null);

        rim.setColor(0x36FFFFFF);
        canvas.drawRoundRect(rect, radius, radius, rim);
        shine.setColor(0x25FFFFFF);
        float shineY = top + AndroidUtilities.dp(1);
        canvas.drawLine(left + radius, shineY, right - radius, shineY, shine);

        // A low-contrast travelling reflection is intentionally subtle; it is sampled
        // only when RecyclerView redraws the row, avoiding a timer per conversation.
        float phase = (SystemClock.uptimeMillis() % 7000L) / 7000f;
        float x = left + width * phase;
        shine.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 5f));
        shine.setColor(0x08FFFFFF);
        canvas.drawLine(x, top + radius, Math.min(right - radius, x + width * 0.12f), bottom - radius, shine);
        canvas.restoreToCount(save);
    }

    private static Paint backgroundPaint() {
        surface.setShader(null);
        surface.setColor(0x08000000);
        return surface;
    }
}
