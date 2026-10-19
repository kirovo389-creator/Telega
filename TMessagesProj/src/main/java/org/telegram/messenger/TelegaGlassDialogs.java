package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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
    private static final RectF innerRect = new RectF();
    private static int shaderWidth = -1;
    private static int shaderHeight = -1;
    private static LinearGradient surfaceShader;
    private static final Matrix shaderMatrix = new Matrix();

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
            surfaceShader = new LinearGradient(0, 0, 0, height,
                    new int[]{0x36FFFFFF, 0x18D9EEFF, 0x0D8AADEB, 0x24FFFFFF},
                    new float[]{0f, 0.34f, 0.76f, 1f}, Shader.TileMode.CLAMP);
        }
        rect.set(left, top, right, bottom);
        float radius = Math.min(AndroidUtilities.dp(18), height * 0.28f);
        int save = canvas.save();
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint());
        shaderMatrix.setTranslate(left, top);
        surfaceShader.setLocalMatrix(shaderMatrix);
        surface.setShader(surfaceShader);
        canvas.drawRoundRect(rect, radius, radius, surface);
        surface.setShader(null);

        // A restrained inner rim and top highlight create depth without expensive blur.
        rim.setColor(0x48FFFFFF);
        canvas.drawRoundRect(rect, radius, radius, rim);
        innerRect.set(left + AndroidUtilities.dp(1), top + AndroidUtilities.dp(1),
                right - AndroidUtilities.dp(1), bottom - AndroidUtilities.dp(1));
        rim.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 0.45f));
        rim.setColor(0x16FFFFFF);
        canvas.drawRoundRect(innerRect, Math.max(0, radius - AndroidUtilities.dp(1)), Math.max(0, radius - AndroidUtilities.dp(1)), rim);
        rim.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 0.7f));

        shine.setStrokeWidth(Math.max(1f, AndroidUtilities.density));
        shine.setColor(0x32FFFFFF);
        float shineY = top + AndroidUtilities.dp(1);
        canvas.drawLine(left + radius, shineY, right - radius, shineY, shine);

        // A single low-opacity reflection; no per-row timer or animator is allocated.
        float phase = (SystemClock.uptimeMillis() % 9000L) / 9000f;
        float x = left + width * phase;
        shine.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 4f));
        shine.setColor(0x0CFFFFFF);
        canvas.drawLine(Math.max(left + radius, x), top + radius,
                Math.min(right - radius, x + width * 0.10f), bottom - radius, shine);
        canvas.restoreToCount(save);
    }

    private static Paint backgroundPaint() {
        surface.setShader(null);
        surface.setColor(0x08000000);
        return surface;
    }
}
