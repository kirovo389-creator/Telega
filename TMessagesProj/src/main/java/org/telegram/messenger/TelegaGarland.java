package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Christmas lights on the top edge of a message bubble.
 *
 * The wire goes from the top-left corner to the top-right corner and sags in the middle
 * (catenary curve), it sways a little and every bulb swings on its own like a pendulum.
 * Bulbs glow and fade slowly (about 3.4 s per cycle), every bulb has its own phase.
 */
public final class TelegaGarland {

    private static final int[] COLORS = {
            0xFFFF4D4D, 0xFFFFC83D, 0xFF3DDC84, 0xFF4DA3FF, 0xFFFF6FB5
    };
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float CATENARY_K = 2.8f;
    private static final int SEGMENTS = 22;

    private static final Paint wirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path wirePath = new Path();
    private static final RectF oval = new RectF();

    static {
        wirePaint.setStyle(Paint.Style.STROKE);
        wirePaint.setStrokeCap(Paint.Cap.ROUND);
        wirePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    private TelegaGarland() {
    }

    private static float shape(float u) {
        double edge = Math.cosh(CATENARY_K * 0.5);
        return (float) ((edge - Math.cosh(CATENARY_K * (u - 0.5))) / (edge - 1.0));
    }

    private static int withAlpha(int color, int alpha) {
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** @param left,top,right bounds of the bubble in the coordinates of the canvas */
    public static void draw(Canvas canvas, View view, int left, int top, int right) {
        float d = AndroidUtilities.density;
        float span = (right - left) - 14f * d;
        if (span < 40f * d) {
            return;
        }
        float sec = (SystemClock.uptimeMillis() % 3600000L) / 1000f;
        int idHash = System.identityHashCode(view);
        float seed = (idHash & 0xFF) / 255f * TWO_PI;
        int colorShift = (idHash >> 8) & 0x7;

        float x0 = left + 7f * d;
        float y0 = top + 4.5f * d;

        float sagBase = Math.max(5f * d, Math.min(15f * d, span * 0.09f));
        float sag = sagBase * (1f + 0.07f * (float) Math.sin(sec * 1.1f + seed));
        float sway = (float) Math.sin(sec * 0.75f + seed * 0.5f) * 1.4f * d;

        // wire
        wirePaint.setStrokeWidth(1.2f * d);
        wirePaint.setColor(0xCC27332B);
        wirePath.reset();
        for (int i = 0; i <= SEGMENTS; i++) {
            float u = i / (float) SEGMENTS;
            float px = x0 + span * u + sway * (float) Math.sin(Math.PI * u);
            float py = y0 + sag * shape(u);
            if (i == 0) {
                wirePath.moveTo(px, py);
            } else {
                wirePath.lineTo(px, py);
            }
        }
        canvas.drawPath(wirePath, wirePaint);

        // wire ends
        fillPaint.setColor(0xFF27332B);
        canvas.drawCircle(x0, y0, 1.9f * d, fillPaint);
        canvas.drawCircle(x0 + span, y0, 1.9f * d, fillPaint);

        // bulbs
        int n = Math.max(3, Math.round(span / (30f * d)));
        for (int i = 0; i < n; i++) {
            float u = (i + 0.5f) / n;
            float px = x0 + span * u + sway * (float) Math.sin(Math.PI * u);
            float py = y0 + sag * shape(u);

            float du = 0.01f;
            float u2 = u + du;
            float px2 = x0 + span * u2 + sway * (float) Math.sin(Math.PI * u2);
            float py2 = y0 + sag * shape(u2);
            float tangent = (float) Math.atan2(py2 - py, px2 - px);

            float swing = (float) Math.sin(sec * 1.6f + seed + i * 1.3f) * 0.16f;
            float rotation = (float) Math.toDegrees(tangent * 0.6f + swing);

            float blink = 0.5f + 0.5f * (float) Math.sin(sec * TWO_PI / 3.4f + i * 0.95f + seed);
            float bright = 0.40f + 0.60f * blink;
            int color = COLORS[(i + colorShift) % COLORS.length];

            canvas.save();
            canvas.translate(px, py);
            canvas.rotate(rotation);

            fillPaint.setColor(0xFF2A2F2C);
            oval.set(-1.4f * d, -0.2f * d, 1.4f * d, 2.4f * d);
            canvas.drawRoundRect(oval, 0.6f * d, 0.6f * d, fillPaint);

            fillPaint.setColor(withAlpha(color, (int) (58 * bright)));
            canvas.drawCircle(0, 5.4f * d, 7.6f * d, fillPaint);
            fillPaint.setColor(withAlpha(color, (int) (96 * bright)));
            canvas.drawCircle(0, 5.4f * d, 4.8f * d, fillPaint);

            fillPaint.setColor(withAlpha(color, (int) (255 * (0.55f + 0.45f * bright))));
            oval.set(-2.4f * d, 2.2f * d, 2.4f * d, 8.6f * d);
            canvas.drawOval(oval, fillPaint);

            fillPaint.setColor(Color.argb((int) (175 * bright), 255, 255, 255));
            canvas.drawCircle(-0.8f * d, 4.2f * d, 0.8f * d, fillPaint);

            canvas.restore();
        }

        // slow animation: ~20 fps is plenty for a 3.4 s blink and a gentle sway
        view.postInvalidateDelayed(48, left, top, right, top + (int) (30f * d));
    }
}
