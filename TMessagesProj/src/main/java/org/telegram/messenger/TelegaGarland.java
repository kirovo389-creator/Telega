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
    private static final float CATENARY_K = 2.6f;
    private static final int SEGMENTS = 28;

    private static final Paint wirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path wirePath = new Path();
    private static final Path bubbleClipPath = new Path();
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

    private static int blendColors(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    private static int withAlpha(int color, int alpha) {
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** @param left,top,right bounds of the bubble in the coordinates of the canvas */
    public static void draw(Canvas canvas, View view, int left, int top, int right) {
        float d = AndroidUtilities.density;
        float span = (right - left) - 18f * d;
        if (span < 40f * d) {
            return;
        }
        float sec = (SystemClock.uptimeMillis() % 3600000L) / 1000f;
        int idHash = System.identityHashCode(view);
        float seed = (idHash & 0xFF) / 255f * TWO_PI;
        int colorShift = (idHash >> 8) & 0x7;

        float x0 = left + 9f * d;
        // Anchors sit near the top edge; the middle of the wire hangs down (sag).
        float y0 = top + 1.6f * d;

        // Visible catenary sag: ~8–16 dp depending on bubble width.
        float sagBase = Math.max(4f * d, Math.min(7f * d, span * 0.043f));
        float sag = sagBase * (1f + 0.06f * (float) Math.sin(sec * 1.1f + seed));
        float sway = (float) Math.sin(sec * 0.75f + seed * 0.5f) * 0.6f * d;

        // Clip to the rounded top edge of the bubble so bulbs cannot leak over its corners.
        float clipBottom = y0 + sagBase + 7f * d;
        float cornerRadius = Math.min(17f * d, (right - left) * 0.14f);
        bubbleClipPath.reset();
        bubbleClipPath.moveTo(left + cornerRadius, top);
        bubbleClipPath.lineTo(right - cornerRadius, top);
        bubbleClipPath.quadTo(right, top, right, top + cornerRadius);
        bubbleClipPath.lineTo(right, clipBottom);
        bubbleClipPath.lineTo(left, clipBottom);
        bubbleClipPath.lineTo(left, top + cornerRadius);
        bubbleClipPath.quadTo(left, top, left + cornerRadius, top);
        bubbleClipPath.close();
        int canvasSave = canvas.save();
        canvas.clipPath(bubbleClipPath);

        // wire
        wirePaint.setStrokeWidth(1.35f * d);
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
        canvas.drawCircle(x0, y0, 0.9f * d, fillPaint);
        canvas.drawCircle(x0 + span, y0, 0.9f * d, fillPaint);

        // bulbs hang slightly below the wire along the catenary
        int n = Math.max(4, Math.round(span / (26f * d)));
        for (int i = 0; i < n; i++) {
            float u = (i + 0.5f) / n;
            float px = x0 + span * u + sway * (float) Math.sin(Math.PI * u);
            float py = y0 + sag * shape(u);

            float du = 0.01f;
            float u2 = u + du;
            float px2 = x0 + span * u2 + sway * (float) Math.sin(Math.PI * u2);
            float py2 = y0 + sag * shape(u2);
            float tangent = (float) Math.atan2(py2 - py, px2 - px);

            float swing = (float) Math.sin(sec * 1.6f + seed + i * 1.3f) * 0.08f;
            float rotation = (float) Math.toDegrees(tangent * 0.7f + swing);

            float colorPhase = sec * 0.62f + i * 0.66f + colorShift * 0.19f;
            float colorWrapped = colorPhase % COLORS.length;
            if (colorWrapped < 0f) {
                colorWrapped += COLORS.length;
            }
            int colorIndex = (int) colorWrapped;
            float colorBlend = colorWrapped - colorIndex;
            colorBlend = colorBlend * colorBlend * (3f - 2f * colorBlend);
            int color = blendColors(COLORS[colorIndex], COLORS[(colorIndex + 1) % COLORS.length], colorBlend);
            float blink = 0.5f + 0.5f * (float) Math.sin(sec * TWO_PI / 3.4f + i * 0.95f + seed);
            float bright = 0.40f + 0.60f * blink;

            canvas.save();
            canvas.translate(px, py);
            canvas.rotate(rotation);

            fillPaint.setColor(0xFF2A2F2C);
            oval.set(-0.55f * d, -0.1f * d, 0.55f * d, 1.25f * d);
            canvas.drawRoundRect(oval, 0.35f * d, 0.35f * d, fillPaint);

            // Brighter, broader halos make the tiny bulbs visibly glow without washing out their colours.
            fillPaint.setColor(withAlpha(color, (int) (88 * bright)));
            canvas.drawCircle(0, 1.9f * d, 3.5f * d, fillPaint);
            fillPaint.setColor(withAlpha(color, (int) (154 * bright)));
            canvas.drawCircle(0, 1.9f * d, 2.1f * d, fillPaint);

            fillPaint.setColor(withAlpha(color, (int) (255 * (0.68f + 0.32f * bright))));
            oval.set(-0.95f * d, 0.85f * d, 0.95f * d, 3.15f * d);
            canvas.drawOval(oval, fillPaint);

            fillPaint.setColor(Color.argb((int) (170 * bright), 255, 255, 255));
            canvas.drawCircle(-0.25f * d, 1.55f * d, 0.3f * d, fillPaint);

            canvas.restore();
        }

        // Keep the animated redraw tightly bounded to the garland's visible area.
        int invalidateBottom = (int) Math.ceil(clipBottom + 2f * d);
        canvas.restoreToCount(canvasSave);
        view.postInvalidateDelayed(72, left, top, right, invalidateBottom);
    }
}
