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

    private final Random random = new Random();
    private final Paint pixelPaint = new Paint();
    private final Paint softPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private float blizzard;
    private float windTilt;
    private float windDir = 1f;
    private float shakeLevel;
    private volatile float shakePeak;
    private volatile float tiltX;
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
        linePaint.setStrokeCap(Paint.Cap.ROUND);
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
                shakePeak = 0;
                shakeLevel = 0;
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
            return;
        }
        final float a = 0.88f;
        for (int i = 0; i < 3; i++) {
            gravity[i] = a * gravity[i] + (1f - a) * e.values[i];
        }
        float lx = e.values[0] - gravity[0];
        float ly = e.values[1] - gravity[1];
        float lz = e.values[2] - gravity[2];
        float mag = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (mag > shakePeak) {
            shakePeak = mag;
        }
        tiltX = gravity[0];
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
            blizzard = 0;
            windTilt = 0;
            reseed();
            if (mode == TelegaSettings.SNOW_REAL) {
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
        } else {
            drawReal(canvas, dt);
        }
        postInvalidateOnAnimation();
    }

    private void drawMinecraft(Canvas canvas, float dt) {
        float d = AndroidUtilities.density;
        int w = getWidth();
        int h = getHeight();
        boolean dark = Theme.isCurrentThemeDark();
        int dpArea = (int) ((w / d) * (h / d));
        int count = Math.max(60, Math.min(190, dpArea / 2400));

        for (int i = 0; i < count; i++) {
            float z = fz[i];
            int texel = Math.max(2, Math.round((1.6f + 2.2f * z) * d));
            float vy = (38f + 52f * z) * d * fv[i];
            float vx = (5f + 8f * (float) Math.sin(time * 0.9f + fp[i])) * d;
            fy[i] += vy * dt;
            fx[i] += vx * dt;
            if (fy[i] > h + texel * 3) {
                fy[i] = -texel * 3;
                fx[i] = random.nextFloat() * w;
            }
            if (fx[i] > w + texel * 3) {
                fx[i] = -texel * 3;
            } else if (fx[i] < -texel * 3) {
                fx[i] = w + texel * 3;
            }
            int px = (int) fx[i];
            int py = (int) fy[i];
            int pw = texel;
            int ph = texel;
            if (ft[i] == 1) {
                ph = texel * 2;
            } else if (ft[i] == 2) {
                pw = texel * 2;
                ph = texel * 2;
            }
            int alpha = (int) (170 + 85 * z);
            if (!dark) {
                pixelPaint.setColor(Color.argb(Math.min(255, alpha / 2), 60, 80, 110));
                canvas.drawRect(px + 1, py + 1, px + pw + 1, py + ph + 1, pixelPaint);
            }
            pixelPaint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawRect(px, py, px + pw, py + ph, pixelPaint);
        }
    }

    private Bitmap flakeBitmap() {
        if (flake == null) {
            int s = 48;
            flake = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(flake);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            // faint cool rim keeps the flakes visible on light wallpapers
            p.setColor(Color.argb(44, 90, 110, 140));
            c.drawCircle(s / 2f, s / 2f, s / 2f - 1, p);
            p.setShader(new RadialGradient(s / 2f, s / 2f, s / 2f - 4,
                    new int[]{0xFFFFFFFF, 0xF2FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(s / 2f, s / 2f, s / 2f - 4, p);
        }
        return flake;
    }

    private void drawReal(Canvas canvas, float dt) {
        float d = AndroidUtilities.density;
        int w = getWidth();
        int h = getHeight();

        // shake -> blizzard
        float level = Math.max(shakeLevel - dt * 4f, shakePeak);
        shakePeak = 0;
        shakeLevel = level;
        float target = Math.max(0f, Math.min(1f, (level - 4.5f) / 10f));
        if (target > blizzard) {
            blizzard += (target - blizzard) * Math.min(1f, dt * 5f);
        } else {
            blizzard = Math.max(0f, blizzard - dt * 0.2f);
        }

        // tilt -> wind (tilt the phone to the right and the snow slides to the right)
        float tiltTarget = Math.max(-1f, Math.min(1f, -tiltX / 9.81f)) * 70f * d;
        windTilt += (tiltTarget - windTilt) * Math.min(1f, dt * 3f);
        if (Math.abs(windTilt) > 8f * d) {
            windDir = windTilt > 0 ? 1f : -1f;
        }

        int dpArea = (int) ((w / d) * (h / d));
        int base = Math.max(70, Math.min(210, dpArea / 2200));
        int active = (int) (base + (MAX_FLAKES - base) * blizzard);

        float gust = 0.6f + 0.4f * (float) Math.sin(time * 1.7f) + 0.2f * (float) Math.sin(time * 3.1f);
        float slant = 12f * d + windTilt;
        float blow = blizzard * 330f * d * gust * windDir;
        float streak = 0.012f + 0.04f * blizzard;

        if (blizzard > 0.05f) {
            canvas.drawColor(Color.argb((int) (34 * blizzard), 235, 242, 250));
        }

        Bitmap bmp = flakeBitmap();
        srcRect.set(0, 0, bmp.getWidth(), bmp.getHeight());

        for (int i = 0; i < active; i++) {
            float z = fz[i];
            float r = (1.0f + 3.6f * z) * d;
            float vy = (24f + 62f * z) * d * fv[i] * (1f + 1.3f * blizzard);
            float wobble = (float) Math.cos(time * (1.0f + fv[i] * 0.8f) + fp[i]) * (7f + 11f * z) * d * (1f + 1.5f * blizzard);
            float vx = slant * (0.4f + 0.6f * z) + blow * (0.35f + 0.65f * z) + wobble;

            fx[i] += vx * dt;
            fy[i] += vy * dt;

            if (fy[i] > h + r * 2) {
                fy[i] = -r * 2;
                fx[i] = random.nextFloat() * w;
            }
            if (fx[i] > w + r * 2) {
                fx[i] = -r * 2;
            } else if (fx[i] < -r * 2) {
                fx[i] = w + r * 2;
            }

            int alpha = (int) (255 * (0.35f + 0.65f * z));
            if (blizzard > 0.3f) {
                linePaint.setStrokeWidth(r * 1.3f);
                linePaint.setColor(Color.argb(alpha, 255, 255, 255));
                canvas.drawLine(fx[i], fy[i], fx[i] - vx * streak, fy[i] - vy * streak, linePaint);
            } else {
                softPaint.setAlpha(alpha);
                dstRect.set(fx[i] - r, fy[i] - r, fx[i] + r, fy[i] + r);
                canvas.drawBitmap(bmp, srcRect, dstRect, softPaint);
            }
        }
    }
}
