package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.TelegaSettings;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Silent, looping, local video background for chat screens. */
public final class TelegaVideoWallpaperView extends FrameLayout implements TextureView.SurfaceTextureListener {
    private static final String TAG = "TelegaVideoWallpaper";
    private static final ArrayList<WeakReference<TelegaVideoWallpaperView>> INSTANCES = new ArrayList<>();

    private final TextureView textureView;
    private Surface surface;
    private MediaPlayer player;
    private String activeUri = "";
    private int videoWidth;
    private int videoHeight;

    public TelegaVideoWallpaperView(Context context) {
        this(context, null);
    }

    public TelegaVideoWallpaperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(true);
        setClipToPadding(true);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAlpha(1f);

        textureView = new TextureView(context);
        textureView.setOpaque(true);
        textureView.setSurfaceTextureListener(this);
        addView(textureView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setVisibility(GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        synchronized (INSTANCES) {
            boolean found = false;
            for (WeakReference<TelegaVideoWallpaperView> reference : INSTANCES) {
                if (reference.get() == this) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                INSTANCES.add(new WeakReference<>(this));
            }
        }
        reloadFromSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        synchronized (INSTANCES) {
            for (int i = INSTANCES.size() - 1; i >= 0; i--) {
                TelegaVideoWallpaperView view = INSTANCES.get(i).get();
                if (view == null || view == this) {
                    INSTANCES.remove(i);
                }
            }
        }
        releasePlayer();
        super.onDetachedFromWindow();
        releaseSurface();
    }

    public static void refreshAll() {
        ArrayList<TelegaVideoWallpaperView> views = new ArrayList<>();
        synchronized (INSTANCES) {
            for (int i = INSTANCES.size() - 1; i >= 0; i--) {
                TelegaVideoWallpaperView view = INSTANCES.get(i).get();
                if (view == null) {
                    INSTANCES.remove(i);
                } else {
                    views.add(view);
                }
            }
        }
        for (TelegaVideoWallpaperView view : views) {
            view.post(view::reloadFromSettings);
        }
    }

    public void reloadFromSettings() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            post(this::reloadFromSettings);
            return;
        }
        String requestedUri = TelegaSettings.getChatVideoWallpaperUri();
        if (requestedUri == null) {
            requestedUri = "";
        }
        if (!requestedUri.equals(activeUri)) {
            activeUri = requestedUri;
            releasePlayer();
        }
        if (TextUtils.isEmpty(activeUri)) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        if (surface != null && player == null) {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (surface == null || TextUtils.isEmpty(activeUri) || player != null) {
            return;
        }
        MediaPlayer candidate = new MediaPlayer();
        player = candidate;
        try {
            candidate.setDataSource(getContext(), Uri.parse(activeUri));
            candidate.setSurface(surface);
            candidate.setVolume(0f, 0f);
            candidate.setLooping(true);
            candidate.setOnVideoSizeChangedListener((mp, width, height) -> {
                videoWidth = width;
                videoHeight = height;
                updateVideoTransform();
            });
            candidate.setOnPreparedListener(mp -> {
                if (player != candidate) {
                    return;
                }
                setVisibility(VISIBLE);
                updateVideoTransform();
                try {
                    mp.start();
                } catch (IllegalStateException error) {
                    Log.w(TAG, "Unable to start selected wallpaper video", error);
                }
            });
            candidate.setOnErrorListener((mp, what, extra) -> {
                if (player == candidate) {
                    Log.w(TAG, "Selected wallpaper video failed to decode (" + what + ", " + extra + ")");
                    releasePlayer();
                    setVisibility(GONE);
                }
                return true;
            });
            candidate.prepareAsync();
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Unable to open selected wallpaper video", error);
            releasePlayer();
            setVisibility(GONE);
        }
    }

    private void updateVideoTransform() {
        if (videoWidth <= 0 || videoHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float scale = Math.max(getWidth() / (float) videoWidth, getHeight() / (float) videoHeight);
        float dx = (getWidth() - videoWidth * scale) * 0.5f;
        float dy = (getHeight() - videoHeight * scale) * 0.5f;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        textureView.setTransform(matrix);
    }

    private void releasePlayer() {
        MediaPlayer previous = player;
        player = null;
        videoWidth = 0;
        videoHeight = 0;
        if (previous != null) {
            try {
                previous.setOnPreparedListener(null);
                previous.setOnVideoSizeChangedListener(null);
                previous.setOnErrorListener(null);
                previous.release();
            } catch (RuntimeException error) {
                Log.w(TAG, "Error releasing wallpaper player", error);
            }
        }
    }

    private void releaseSurface() {
        Surface previous = surface;
        surface = null;
        if (previous != null) {
            previous.release();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        updateVideoTransform();
        reloadFromSettings();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        updateVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        releasePlayer();
        releaseSurface();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateVideoTransform();
    }
}
