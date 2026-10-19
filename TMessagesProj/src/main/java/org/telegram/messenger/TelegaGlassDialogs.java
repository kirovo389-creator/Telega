package org.telegram.messenger;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private static boolean shaderDark;
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
        boolean dark = (view.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (width != shaderWidth || height != shaderHeight || surfaceShader == null || dark != shaderDark) {
            shaderWidth = width;
            shaderHeight = height;
            shaderDark = dark;
            int[] colors = dark
                    ? new int[]{0x2AFFFFFF, 0x1C4D7898, 0x12314F6A, 0x22FFFFFF}
                    : new int[]{0x50FFFFFF, 0x36D8F0FA, 0x24A7D5E8, 0x58FFFFFF};
            surfaceShader = new LinearGradient(0, 0, 0, height, colors,
                    new float[]{0f, 0.32f, 0.72f, 1f}, Shader.TileMode.CLAMP);
        }
        rect.set(left, top, right, bottom);
        float radius = Math.min(AndroidUtilities.dp(18), height * 0.28f);
        int save = canvas.save();

        // Layered translucent tint and a fine rim create a frosted surface without
        // per-row bitmap blur or expensive RenderEffect passes.
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint(dark));
        shaderMatrix.setTranslate(left, top);
        surfaceShader.setLocalMatrix(shaderMatrix);
        surface.setShader(surfaceShader);
        surface.setColor(Color.WHITE);
        canvas.drawRoundRect(rect, radius, radius, surface);
        surface.setShader(null);

        rim.setColor(view.isPressed() ? (dark ? 0x70A8D9FF : 0xD0FFFFFF)
                : (dark ? 0x38FFFFFF : 0xA8FFFFFF));
        canvas.drawRoundRect(rect, radius, radius, rim);
        innerRect.set(left + AndroidUtilities.dp(1), top + AndroidUtilities.dp(1),
                right - AndroidUtilities.dp(1), bottom - AndroidUtilities.dp(1));
        rim.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 0.45f));
        rim.setColor(dark ? 0x1AFFFFFF : 0x55FFFFFF);
        canvas.drawRoundRect(innerRect, Math.max(0, radius - AndroidUtilities.dp(1)),
                Math.max(0, radius - AndroidUtilities.dp(1)), rim);
        rim.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 0.7f));

        shine.setStrokeWidth(Math.max(1f, AndroidUtilities.density));
        shine.setColor(dark ? 0x32FFFFFF : 0xC0FFFFFF);
        float shineY = top + AndroidUtilities.dp(1);
        canvas.drawLine(left + radius, shineY, right - radius, shineY, shine);
        shine.setColor(dark ? 0x18FFFFFF : 0x60FFFFFF);
        canvas.drawLine(left + radius, top + AndroidUtilities.dp(3),
                right - radius, top + AndroidUtilities.dp(3), shine);
        shine.setColor(dark ? 0x10FFFFFF : 0x2AFFFFFF);
        canvas.drawLine(left + radius, bottom - AndroidUtilities.dp(2),
                right - radius, bottom - AndroidUtilities.dp(2), shine);
        canvas.restoreToCount(save);
    }

    private static Paint backgroundPaint(boolean dark) {
        surface.setShader(null);
        surface.setColor(dark ? 0xA4142231 : 0xB5F0F7FA);
        return surface;
    }
}
