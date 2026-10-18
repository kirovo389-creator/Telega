package org.telegram.messenger;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TelegaSnowView;

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
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof TelegaSnowView) {
                return;
            }
        }
        final TelegaSnowView snow = new TelegaSnowView(group.getContext());
        // right before the message list: above the wallpaper, below the bubbles
        group.addView(snow, findInsertIndex(group),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // custom chat layouts may skip unknown children: make sure the layer always has the full size
        group.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int orr, int ob) {
                int w = r - l;
                int h = b - t;
                if (w > 0 && h > 0 && (snow.getWidth() != w || snow.getHeight() != h)) {
                    snow.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                    snow.layout(0, 0, w, h);
                }
            }
        });
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
