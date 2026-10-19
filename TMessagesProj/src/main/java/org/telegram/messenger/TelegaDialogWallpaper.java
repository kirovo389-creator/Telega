package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.io.File;

/**
 * Efficient background renderer for the main dialogs list.
 * The image remains in app-private storage and is never uploaded.
 */
public final class TelegaDialogWallpaper {
    private static final int MAX_BITMAP_WIDTH = 1280;
    private static final int MAX_BITMAP_HEIGHT = 2400;
    private static String cachedPath;
    private static Bitmap cachedBitmap;

    private TelegaDialogWallpaper() {
    }

    public static Drawable createDrawable(Context context, int fallbackColor) {
        String path = TelegaSettings.getDialogsWallpaperPath();
        if (context == null || TextUtils.isEmpty(path)) {
            return new ColorDrawable(fallbackColor);
        }
        Bitmap bitmap = getCachedBitmap(path);
        if (bitmap == null || bitmap.isRecycled()) {
            return new ColorDrawable(fallbackColor);
        }
        return new WallpaperDrawable(bitmap, fallbackColor);
    }

    public static Bitmap decodePreview(String path, int maxWidth, int maxHeight) {
        return decodeSampled(path, Math.max(1, maxWidth), Math.max(1, maxHeight));
    }

    public static synchronized void invalidateCache() {
        cachedPath = null;
        // Do not recycle the old bitmap: a view may still be drawing its previous Drawable.
        cachedBitmap = null;
    }

    private static synchronized Bitmap getCachedBitmap(String path) {
        if (path.equals(cachedPath) && cachedBitmap != null && !cachedBitmap.isRecycled()) {
            return cachedBitmap;
        }
        Bitmap decoded = decodeSampled(path, MAX_BITMAP_WIDTH, MAX_BITMAP_HEIGHT);
        cachedPath = path;
        cachedBitmap = decoded;
        return decoded;
    }

    private static Bitmap decodeSampled(String path, int maxWidth, int maxHeight) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || (long) bounds.outWidth * (long) bounds.outHeight > 80000000L) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > maxWidth * 2
                || bounds.outHeight / options.inSampleSize > maxHeight * 2) {
            options.inSampleSize *= 2;
        }
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inDither = true;
        try {
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError error) {
            FileLog.e(error);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                return BitmapFactory.decodeFile(path, options);
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    /** Deletes only files owned by this feature, never arbitrary paths from preferences. */
    public static void deleteManagedFile(Context context, String path) {
        if (context == null || TextUtils.isEmpty(path)) {
            return;
        }
        try {
            File directory = new File(context.getFilesDir(), "telega_dialog_wallpapers").getCanonicalFile();
            File candidate = new File(path).getCanonicalFile();
            String prefix = directory.getPath() + File.separator;
            if (candidate.getPath().startsWith(prefix) && candidate.isFile()) {
                candidate.delete();
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    private static final class WallpaperDrawable extends Drawable {
        private final Bitmap bitmap;
        private final int fallbackColor;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect source = new Rect();
        private final RectF destination = new RectF();
        private int alpha = 255;

        WallpaperDrawable(Bitmap bitmap, int fallbackColor) {
            this.bitmap = bitmap;
            this.fallbackColor = fallbackColor;
            shadePaint.setColor(Color.argb(54, 0, 0, 0));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawColor(fallbackColor);
            if (bitmap == null || bitmap.isRecycled() || bounds.width() <= 0 || bounds.height() <= 0) {
                return;
            }
            float scale = Math.max(bounds.width() / (float) bitmap.getWidth(),
                    bounds.height() / (float) bitmap.getHeight());
            int srcWidth = Math.min(bitmap.getWidth(), Math.round(bounds.width() / scale));
            int srcHeight = Math.min(bitmap.getHeight(), Math.round(bounds.height() / scale));
            int left = Math.max(0, (bitmap.getWidth() - srcWidth) / 2);
            int top = Math.max(0, (bitmap.getHeight() - srcHeight) / 2);
            source.set(left, top, left + srcWidth, top + srcHeight);
            destination.set(bounds);
            bitmapPaint.setAlpha(alpha);
            canvas.drawBitmap(bitmap, source, destination, bitmapPaint);
            shadePaint.setAlpha(Math.round(54 * (alpha / 255f)));
            canvas.drawRect(bounds, shadePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            bitmapPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
