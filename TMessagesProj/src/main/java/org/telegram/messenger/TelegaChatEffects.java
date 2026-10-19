package org.telegram.messenger;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TelegaSnowView;
import org.telegram.ui.Components.TelegaVideoWallpaperView;

/** Puts the snow layer into every chat (private chats, groups, channels). */
public final class TelegaChatEffects {

    private TelegaChatEffects() {
    }

    /** @param root the view returned by ChatActivity.createView */
    public static void attach(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        final ViewGroup group = (ViewGroup) root;
        TelegaVideoWallpaperView videoWallpaper = null;
        TelegaSnowView snowView = null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TelegaVideoWallpaperView) {
                videoWallpaper = (TelegaVideoWallpaperView) child;
            } else if (child instanceof TelegaSnowView) {
                snowView = (TelegaSnowView) child;
            }
        }

        if (videoWallpaper == null) {
            videoWallpaper = new TelegaVideoWallpaperView(group.getContext());
            int insertIndex = snowView != null ? group.indexOfChild(snowView) : findInsertIndex(group);
            group.addView(videoWallpaper, insertIndex,
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            final TelegaVideoWallpaperView videoLayer = videoWallpaper;
            group.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View parent, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    sizeOverlayToParent(videoLayer, right - left, bottom - top);
                }
            });
            sizeOverlayToParent(videoLayer, group.getWidth(), group.getHeight());
        } else {
            videoWallpaper.reloadFromSettings();
        }
        if (snowView != null) {
            return;
        }

        final TelegaSnowView snow = new TelegaSnowView(group.getContext());
        group.addView(snow, findInsertIndex(group),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        group.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int orr, int ob) {
                int w = r - l;
                int h = b - t;
                if (w > 0 && h > 0 && (snow.getWidth() != w || snow.getHeight() != h)) {
                    snow.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                    snow.layout(0, 0, w, h);
                }
            }
        });
    }

    private static void sizeOverlayToParent(View child, int width, int height) {
        if (child == null || width <= 0 || height <= 0
                || child.getWidth() == width && child.getHeight() == height
                && child.getLeft() == 0 && child.getTop() == 0) {
            return;
        }
        child.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        child.layout(0, 0, width, height);
    }

    private static int findInsertIndex(ViewGroup group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            if (containsList(group.getChildAt(i), 2)) {
                return i;
            }
        }
        return Math.min(1, count);
    }

    private static boolean containsList(View v, int depth) {
        if (v instanceof RecyclerListView) {
            return true;
        }
        if (depth > 0 && v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (containsList(g.getChildAt(i), depth - 1)) {
                    return true;
                }
            }
        }
        return false;
    }
}
