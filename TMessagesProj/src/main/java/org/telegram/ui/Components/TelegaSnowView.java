package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.View;
import android.view.Surface;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.Theme;

import java.util.Random;

/**
 * Snow layer of the chat background (below the message bubbles).
 *
 *  mode 1 - Minecraft snow: hard-edged square "texels" (1x1, 1x2, 2x2), no anti-aliasing, three depth
 *           layers with different texel size and fall speed, slow sideways drift.
 *  mode 2 - real snow: soft round flakes with depth, the whole snowfall is slanted. The accelerometer
 *           bends the wind when the phone is tilted and a shake starts a blizzard (more flakes, strong
 *           gusty wind, streaks, light fog) that calms down by itself in a few seconds.
 */
public class TelegaSnowView extends View implements SensorEventListener {

    private static final int MAX_FLAKES = 420;
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final String[] PIXEL_SNOW_SMALL = {"010", "111", "010"};
    private static final String[] PIXEL_SNOW_DIAGONAL = {"101", "010", "101"};
    private static final String[] PIXEL_SNOW_LARGE = {"00100", "01110", "11111", "01110", "00100"};

    private final Random random = new Random();
    private final Paint pixelPaint = new Paint();
    private final Paint softPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private final float[] fx = new float[MAX_FLAKES];
    private final float[] fy = new float[MAX_FLAKES];
    private final float[] fz = new float[MAX_FLAKES];
    private final float[] fp = new float[MAX_FLAKES];
    private final float[] fv = new float[MAX_FLAKES];
    private final int[] ft = new int[MAX_FLAKES];

    private int mode = -1;
    private long lastTime;
    private float time;
    private float windTilt;
    private volatile float tiltX;
    private volatile float tiltY;
    private final float[] gravity = new float[3];
    private boolean gravityReady;
    private boolean sensorOn;
    private boolean checkPosted;
    private Bitmap flake;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkPosted = false;
            invalidate();
        }
    };

    public TelegaSnowView(Context context) {
        super(context);
        setWillNotDraw(false);
        pixelPaint.setAntiAlias(false);
        pixelPaint.setFilterBitmap(false);
        pixelPaint.setStyle(Paint.Style.FILL);
        rainPaint.setStyle(Paint.Style.STROKE);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);
        rainGlowPaint.setStyle(Paint.Style.STROKE);
        rainGlowPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        reseed();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mode = -1;
        lastTime = 0;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterSensor();
        removeCallbacks(checkRunnable);
        checkPosted = false;
        mode = -1;
    }

    private void reseed() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        for (int i = 0; i < MAX_FLAKES; i++) {
            fx[i] = random.nextFloat() * w;
            fy[i] = random.nextFloat() * h;
            fz[i] = random.nextFloat();
            fp[i] = random.nextFloat() * TWO_PI;
            fv[i] = 0.75f + random.nextFloat() * 0.5f;
            float r = random.nextFloat();
            ft[i] = r < 0.5f ? 0 : (r < 0.82f ? 1 : 2);
        }
    }

    // ------------------------------------------------------------------ sensor

    private void registerSensor() {
        if (sensorOn) {
            return;
        }
        try {
            SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            Sensor s = sm != null ? sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
            if (s != null) {
                gravityReady = false;
                sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
                sensorOn = true;
            }
        } catch (Throwable ignore) {
        }
    }

    private void unregisterSensor() {
        if (!sensorOn) {
            return;
        }
        try {
            SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) {
                sm.unregisterListener(this);
            }
        } catch (Throwable ignore) {
        }
        sensorOn = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        if (!gravityReady) {
            gravity[0] = e.values[0];
            gravity[1] = e.values[1];
            gravity[2] = e.values[2];
            gravityReady = true;
        } else {
            // Low-pass filter avoids abrupt changes from small hand movements.
            final float a = 0.88f;
            for (int i = 0; i < 3; i++) {
                gravity[i] = a * gravity[i] + (1f - a) * e.values[i];
            }
        }
        tiltX = gravity[0];
        tiltY = gravity[1];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // -------------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas canvas) {
        int m = TelegaSettings.getSnowMode();
        if (m != mode) {
            mode = m;
            lastTime = 0;
            windTilt = 0;
            reseed();
            if (mode == TelegaSettings.SNOW_REAL || mode == TelegaSettings.SNOW_RAIN) {
                registerSensor();
            } else {
                unregisterSensor();
            }
        }
        if (mode == TelegaSettings.SNOW_OFF || getWidth() <= 0 || getHeight() <= 0) {
            if (!checkPosted) {
                checkPosted = true;
                postDelayed(checkRunnable, 600);
            }
            return;
        }

        long now = SystemClock.uptimeMillis();
        float dt = lastTime == 0 ? 0.016f : Math.min(0.05f, (now - lastTime) / 1000f);
        lastTime = now;
        time += dt;

        if (mode == TelegaSettings.SNOW_MINECRAFT) {
            drawMinecraft(canvas, dt);
        } else if (mode == TelegaSettings.SNOW_RAIN) {
            drawRain(canvas, dt);
        } else {
            drawReal(canvas, dt);
        }
        postInvalidateOnAnimation();
    }

    private void drawMinecraft(Canvas canvas, float dt) {
        float d = AndroidUtilities.density;
        int w = getWidth();
        int h = getHeight();
        int dpArea = (int) ((w / d) * (h / d));
        int count = Math.max(55, Math.min(130, dpArea / 2600));

        for (int i = 0; i < count; i++) {
            float z = fz[i];
            float cell = (1.15f + 0.65f * z) * d;
            float vy = (34f + 48f * z) * d * fv[i];
            float vx = (float) Math.sin(time * 0.65f + fp[i]) * (5f + 7f * z) * d;
            fy[i] += vy * dt;
            fx[i] += vx * dt;

            String[] pattern = ft[i] == 1 ? PIXEL_SNOW_DIAGONAL
                    : (ft[i] == 2 ? PIXEL_SNOW_LARGE : PIXEL_SNOW_SMALL);
            float size = pattern.length * cell;
            if (fy[i] > h + size) {
                fy[i] = -size;
                fx[i] = random.nextFloat() * w;
            }
            if (fx[i] > w + size) {
                fx[i] = -size;
            } else if (fx[i] < -size) {
                fx[i] = w + size;
            }

            int alpha = Math.min(255, (int) (232 + 23 * z));
            pixelPaint.setColor(Color.argb(alpha,
                    ft[i] == 2 ? 238 : 255,
                    ft[i] == 2 ? 248 : 255,
                    255));
            float px = fx[i];
            float py = fy[i];
            for (int row = 0; row < pattern.length; row++) {
                String line = pattern[row];
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '1') {
                        canvas.drawRect(px + col * cell, py + row * cell,
                                px + (col + 1) * cell, py + (row + 1) * cell, pixelPaint);
                    }
                }
            }
        }
    }

    /** Natural rain streaks with depth-based speed and wind drift. */
    private void drawRain(Canvas canvas, float dt) {
        float d = AndroidUtilities.density;
        int w = getWidth();
        int h = getHeight();
        int dpArea = (int) ((w / d) * (h / d));
        int active = Math.max(120, Math.min(300, dpArea / 1450));

        float targetWind = Math.max(-1f, Math.min(1f, -tiltX / 9.81f)) * 24f * d;
        windTilt += (targetWind - windTilt) * Math.min(1f, dt * 1.8f);
        float baseDrift = 6f * d + windTilt;

        for (int i = 0; i < active; i++) {
            float z = fz[i];
            float length = (8f + 17f * z) * d;
            float speed = (600f + 900f * z) * d * fv[i];
            float drift = baseDrift * (0.4f + 0.7f * z);
            float wobble = (float) Math.sin(time * 0.7f + fp[i]) * (1.5f + 2f * z) * d;
            fx[i] += (drift + wobble) * dt;
            fy[i] += speed * dt;
            if (fy[i] > h + length) {
                fy[i] = -length;
                fx[i] = random.nextFloat() * w;
            }
            if (fx[i] > w + length) fx[i] = -length;
            else if (fx[i] < -length) fx[i] = w + length;

            float lean = length * (0.12f + 0.14f * z) + windTilt * 0.055f;
            float x = fx[i];
            float y = fy[i];
            int alpha = (int) (70f + 120f * z);
            rainGlowPaint.setStrokeWidth((1.8f + 1.7f * z) * d);
            rainGlowPaint.setColor(Color.argb(alpha / 4, 150, 190, 220));
            canvas.drawLine(x, y, x - lean, y - length, rainGlowPaint);
            rainPaint.setStrokeWidth((0.55f + 0.6f * z) * d);
            rainPaint.setColor(Color.argb(alpha, 205, 224, 242));
            canvas.drawLine(x, y, x - lean, y - length, rainPaint);
        }
    }

    private Bitmap flakeBitmap() {
        if (flake == null) {
            int size = 48;
            flake = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(flake);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            // A white center and a soft transparent edge; no gray outline or tint.
            p.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f - 2,
                    new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(size / 2f, size / 2f, size / 2f - 2, p);
        }
        return flake;
    }

    private void drawReal(Canvas canvas, float dt) {
        float d = AndroidUtilities.density;
        int w = getWidth();
        int h = getHeight();

        // Convert gravity to screen axes, including landscape and reverse-landscape rotation.
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm != null && wm.getDefaultDisplay() != null
                ? wm.getDefaultDisplay().getRotation() : Surface.ROTATION_0;
        float screenTiltX;
        switch (rotation) {
            case Surface.ROTATION_90:
                screenTiltX = -tiltY;
                break;
            case Surface.ROTATION_180:
                screenTiltX = -tiltX;
                break;
            case Surface.ROTATION_270:
                screenTiltX = tiltY;
                break;
            case Surface.ROTATION_0:
            default:
                screenTiltX = tiltX;
                break;
        }
        float tiltTarget = Math.max(-1f, Math.min(1f, -screenTiltX / 9.81f)) * 42f * d;
        windTilt += (tiltTarget - windTilt) * Math.min(1f, dt * 1.7f);
        float slant = 4f * d + windTilt;

        int dpArea = (int) ((w / d) * (h / d));
        int active = Math.max(75, Math.min(190, dpArea / 2350));
        Bitmap bmp = flakeBitmap();
        srcRect.set(0, 0, bmp.getWidth(), bmp.getHeight());

        for (int i = 0; i < active; i++) {
            float z = fz[i];
            float r = (1.15f + 3.25f * z) * d;
            float vy = (26f + 54f * z) * d * fv[i];
            float wobble = (float) Math.cos(time * (0.85f + fv[i] * 0.65f) + fp[i])
                    * (5f + 8f * z) * d;
            float vx = slant * (0.25f + 0.75f * z) + wobble;

            fx[i] += vx * dt;
            fy[i] += vy * dt;
            if (fy[i] > h + r * 2f) {
                fy[i] = -r * 2f;
                fx[i] = random.nextFloat() * w;
            }
            if (fx[i] > w + r * 2f) {
                fx[i] = -r * 2f;
            } else if (fx[i] < -r * 2f) {
                fx[i] = w + r * 2f;
            }

            softPaint.setAlpha(255);
            dstRect.set(fx[i] - r, fy[i] - r, fx[i] + r, fy[i] + r);
            canvas.drawBitmap(bmp, srcRect, dstRect, softPaint);
        }
    }
}
