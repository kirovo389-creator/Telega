package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TelegaSettings;

import java.util.Map;
import java.util.WeakHashMap;

/** Pixel-fragment avatar frame drawn behind the profile photo at 1.3x its size. */
public final class TelegaAvatarFrameView extends View {
    private static final Object LOCK = new Object();
    private static final WeakHashMap<TelegaAvatarFrameView, Boolean> INSTANCES = new WeakHashMap<>();
    private static final float[][] PIXELS = {
            {0.06f, 0.28f, 0.050f, 0}, {0.13f, 0.12f, 0.058f, 2},
            {0.22f, 0.04f, 0.052f, 3}, {0.34f, 0.10f, 0.046f, 1},
            {0.45f, 0.01f, 0.055f, 0}, {0.60f, 0.07f, 0.050f, 2},
            {0.77f, 0.10f, 0.057f, 0}, {0.91f, 0.21f, 0.050f, 2},
            {0.97f, 0.37f, 0.045f, 0}, {0.94f, 0.56f, 0.054f, 3},
            {0.87f, 0.75f, 0.052f, 2}, {0.74f, 0.91f, 0.055f, 3},
            {0.57f, 0.97f, 0.047f, 0}, {0.39f, 0.94f, 0.054f, 2},
            {0.21f, 0.86f, 0.055f, 3}, {0.07f, 0.74f, 0.053f, 0},
            {0.02f, 0.56f, 0.046f, 2}, {0.05f, 0.40f, 0.050f, 3},
            {0.28f, 0.22f, 0.034f, 2}, {0.69f, 0.21f, 0.039f, 3},
            {0.80f, 0.39f, 0.032f, 2}, {0.18f, 0.62f, 0.036f, 2},
            {0.35f, 0.79f, 0.041f, 3}, {0.67f, 0.79f, 0.034f, 2}
    };
    private static final int[] COLORS = {0xFF050505, 0xFF242424, 0xFFBEBEBE, 0xFFECECEC};

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private boolean ownProfile;

    public TelegaAvatarFrameView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bodyPaint.setColor(0xFF070707);
        bodyPaint.setStyle(Paint.Style.FILL);
        pixelPaint.setStyle(Paint.Style.FILL);
        edgePaint.setColor(0x44202020);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(AndroidUtilities.dp(0.75f));
    }

    public void setForOwnProfile(boolean value) {
        ownProfile = value;
        invalidate();
    }

    public static void refreshAll() {
        synchronized (LOCK) {
            for (Map.Entry<TelegaAvatarFrameView, Boolean> entry : INSTANCES.entrySet()) {
                TelegaAvatarFrameView view = entry.getKey();
                if (view != null) {
                    view.postInvalidate();
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        synchronized (LOCK) {
            INSTANCES.put(this, Boolean.TRUE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        synchronized (LOCK) {
            INSTANCES.remove(this);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!ownProfile || !TelegaSettings.isAvatarFrameEnabled()) {
            return;
        }
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }
        float scale = size / 130f;
        float d = AndroidUtilities.density;
        float cx = size * 0.5f;
        canvas.save();
        canvas.translate((getWidth() - size) * 0.5f, (getHeight() - size) * 0.5f);

        // Irregular black silhouette; the circular avatar drawn above covers the centre.
        shape.reset();
        shape.moveTo(cx * 0.98f, size * 0.025f);
        shape.lineTo(cx * 1.18f, size * 0.13f);
        shape.lineTo(cx * 1.31f, size * 0.075f);
        shape.lineTo(size * 0.90f, size * 0.23f);
        shape.lineTo(size * 0.84f, size * 0.36f);
        shape.lineTo(size * 0.98f, size * 0.50f);
        shape.lineTo(size * 0.86f, size * 0.63f);
        shape.lineTo(size * 0.94f, size * 0.75f);
        shape.lineTo(size * 0.72f, size * 0.90f);
        shape.lineTo(size * 0.59f, size * 0.84f);
        shape.lineTo(size * 0.49f, size * 0.985f);
        shape.lineTo(size * 0.36f, size * 0.85f);
        shape.lineTo(size * 0.22f, size * 0.91f);
        shape.lineTo(size * 0.075f, size * 0.75f);
        shape.lineTo(size * 0.16f, size * 0.61f);
        shape.lineTo(size * 0.025f, size * 0.51f);
        shape.lineTo(size * 0.16f, size * 0.37f);
        shape.lineTo(size * 0.075f, size * 0.24f);
        shape.lineTo(size * 0.27f, size * 0.085f);
        shape.lineTo(size * 0.39f, size * 0.16f);
        shape.close();
        canvas.drawPath(shape, bodyPaint);
        canvas.drawPath(shape, edgePaint);

        for (float[] pixel : PIXELS) {
            float x = pixel[0] * size;
            float y = pixel[1] * size;
            float square = Math.max(AndroidUtilities.dp(2.5f), pixel[2] * size);
            pixelPaint.setColor(COLORS[(int) pixel[3]]);
            canvas.drawRect(x, y, x + square, y + square, pixelPaint);
        }

        // Small bevels catch the light without adding a heavy glow or animation cost.
        pixelPaint.setColor(0x44808080);
        canvas.drawRect(size * 0.23f, size * 0.19f, size * 0.28f, size * 0.24f, pixelPaint);
        canvas.drawRect(size * 0.72f, size * 0.72f, size * 0.76f, size * 0.76f, pixelPaint);
        canvas.restore();
    }
}
