package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Subtle glass overlay. Per-view gradients are cached, and a single shared tick
 * invalidates visible message cells instead of each bubble running its own timer.
 */
public final class TelegaGlassMessages {
    private static final long FRAME_INTERVAL_MS = 112L;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Layer> LAYERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Layer> ANIMATING = new WeakHashMap<>();
    private static final Rect VISIBLE_RECT = new Rect();
    private static boolean tickScheduled;

    private static final int[] SURFACE_COLORS = {
            0x2AFFFFFF, 0x0EFFFFFF, 0x18FFFFFF
    };
    private static final float[] SURFACE_POSITIONS = {0f, 0.52f, 1f};
    private static final int[] SHIMMER_COLORS = {
            0x00FFFFFF, 0x15FFFFFF, 0x1CFFFFFF, 0x15FFFFFF, 0x00FFFFFF
    };
    private static final float[] SHIMMER_POSITIONS = {0f, 0.34f, 0.5f, 0.66f, 1f};

    private static final Runnable ANIMATION_TICK = new Runnable() {
        @Override
        public void run() {
            synchronized (LOCK) {
                tickScheduled = false;
                Iterator<Map.Entry<View, Layer>> iterator = ANIMATING.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<View, Layer> entry = iterator.next();
                    View view = entry.getKey();
                    Layer layer = entry.getValue();
                    if (view == null || layer == null || !view.isShown() || !view.isAttachedToWindow()
                            || !view.getGlobalVisibleRect(VISIBLE_RECT)) {
                        iterator.remove();
                        continue;
                    }
                    Rect bounds = layer.bounds;
                    view.invalidate(bounds.left, bounds.top, bounds.right, bounds.bottom);
                }
                if (!ANIMATING.isEmpty() && TelegaSettings.isGlassMessagesEnabled()
                        && TelegaSettings.isGlassMessagesAnimationEnabled()) {
                    scheduleTickLocked();
                }
            }
        }
    };

    private TelegaGlassMessages() {
    }

    public static int adjustAlpha(int originalAlpha) {
        if (!TelegaSettings.isGlassMessagesEnabled()) {
            return originalAlpha;
        }
        // Keep bubble colours readable; the glass shader supplies the sheen.
        return Math.max(0, Math.min(255, Math.round(originalAlpha * 0.82f)));
    }

    public static void refreshAnimationState() {
        synchronized (LOCK) {
            for (View view : LAYERS.keySet()) {
                if (view != null) {
                    view.postInvalidate();
                }
            }
            ANIMATING.clear();
            MAIN.removeCallbacks(ANIMATION_TICK);
            tickScheduled = false;
        }
    }

    public static void draw(Canvas canvas, View view, Rect bounds, boolean outgoing) {
        if (canvas == null || view == null || bounds == null
                || !TelegaSettings.isGlassMessagesEnabled()
                || bounds.width() < AndroidUtilities.dp(24)
                || bounds.height() < AndroidUtilities.dp(14)) {
            unregister(view);
            return;
        }

        final Layer layer;
        synchronized (LOCK) {
            Layer existing = LAYERS.get(view);
            if (existing == null) {
                existing = new Layer();
                LAYERS.put(view, existing);
            }
            existing.update(bounds);
            layer = existing;
            if (TelegaSettings.isGlassMessagesAnimationEnabled() && view.isShown()
                    && view.isAttachedToWindow()) {
                ANIMATING.put(view, layer);
                scheduleTickLocked();
            } else {
                ANIMATING.remove(view);
            }
        }
        layer.draw(canvas, outgoing, TelegaSettings.isGlassMessagesAnimationEnabled());
    }

    private static void unregister(View view) {
        if (view == null) {
            return;
        }
        synchronized (LOCK) {
            ANIMATING.remove(view);
            if (ANIMATING.isEmpty() && tickScheduled) {
                MAIN.removeCallbacks(ANIMATION_TICK);
                tickScheduled = false;
            }
        }
    }

    private static void scheduleTickLocked() {
        if (!tickScheduled) {
            tickScheduled = true;
            MAIN.postDelayed(ANIMATION_TICK, FRAME_INTERVAL_MS);
        }
    }

    private static final class Layer {
        private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topShinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect bounds = new Rect();
        private final RectF rect = new RectF();
        private final RectF topShine = new RectF();
        private final Path clip = new Path();
        private final Matrix shimmerMatrix = new Matrix();

        private LinearGradient surfaceShader;
        private LinearGradient topShader;
        private LinearGradient shimmerShader;
        private float radius;
        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private int cachedLeft = Integer.MIN_VALUE;
        private int cachedTop = Integer.MIN_VALUE;
        private int cachedRight = Integer.MIN_VALUE;
        private int cachedBottom = Integer.MIN_VALUE;

        Layer() {
            surfacePaint.setStyle(Paint.Style.FILL);
            topShinePaint.setStyle(Paint.Style.FILL);
            rimPaint.setStyle(Paint.Style.STROKE);
            bottomRimPaint.setStyle(Paint.Style.STROKE);
            shimmerPaint.setStyle(Paint.Style.FILL);
            rimPaint.setStrokeWidth(Math.max(0.75f, AndroidUtilities.density * 0.8f));
            bottomRimPaint.setStrokeWidth(Math.max(0.7f, AndroidUtilities.density * 0.65f));
        }

        void update(Rect source) {
            if (source.left == cachedLeft && source.top == cachedTop
                    && source.right == cachedRight && source.bottom == cachedBottom) {
                return;
            }
            bounds.set(source);
            rect.set(source.left, source.top, source.right, source.bottom);
            radius = Math.min(AndroidUtilities.dp(17), rect.height() * 0.48f);
            float inset = AndroidUtilities.dp(2);
            topShine.set(rect.left + inset, rect.top + inset,
                    rect.right - inset, rect.top + AndroidUtilities.dp(9));
            clip.reset();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);

            surfaceShader = new LinearGradient(0f, rect.top, 0f, rect.bottom,
                    SURFACE_COLORS, SURFACE_POSITIONS, Shader.TileMode.CLAMP);
            surfacePaint.setShader(surfaceShader);
            topShader = new LinearGradient(0f, rect.top, 0f, topShine.bottom,
                    new int[]{0x1AFFFFFF, 0x03FFFFFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            topShinePaint.setShader(topShader);
            shimmerShader = new LinearGradient(0f, 0f, 1f, 0f,
                    SHIMMER_COLORS, SHIMMER_POSITIONS, Shader.TileMode.CLAMP);
            shimmerPaint.setShader(shimmerShader);

            cachedLeft = source.left;
            cachedTop = source.top;
            cachedRight = source.right;
            cachedBottom = source.bottom;
            cachedWidth = source.width();
            cachedHeight = source.height();
        }

        void draw(Canvas canvas, boolean outgoing, boolean animate) {
            int save = canvas.save();
            canvas.clipPath(clip);

            canvas.drawRoundRect(rect, radius, radius, surfacePaint);
            canvas.drawRoundRect(topShine, Math.max(1f, radius * 0.2f), Math.max(1f, radius * 0.2f), topShinePaint);

            rimPaint.setColor(outgoing ? 0x4AFFFFFF : 0x2FFFFFFF);
            canvas.drawRoundRect(rect, radius, radius, rimPaint);
            bottomRimPaint.setColor(outgoing ? 0x22000000 : 0x10000000);
            canvas.drawLine(rect.left + radius, rect.bottom - AndroidUtilities.dp(1),
                    rect.right - radius, rect.bottom - AndroidUtilities.dp(1), bottomRimPaint);

            if (animate) {
                float stripeWidth = Math.max(AndroidUtilities.dp(26), rect.width() * 0.14f);
                float phase = (SystemClock.uptimeMillis() % 3600L) / 3600f;
                float stripeLeft = rect.left - stripeWidth + phase * (rect.width() + stripeWidth);
                shimmerMatrix.reset();
                shimmerMatrix.setScale(stripeWidth, 1f);
                shimmerMatrix.postTranslate(stripeLeft, 0f);
                shimmerShader.setLocalMatrix(shimmerMatrix);
                canvas.drawRoundRect(rect, radius, radius, shimmerPaint);
            }

            canvas.restoreToCount(save);
        }
    }
}
