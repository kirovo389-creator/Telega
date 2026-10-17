package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.TelegaEditedMessages;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws previous versions of an edited message above the current text.
 * 1 version  -> show original text above
 * 2+ versions -> collapsed with chevron (right when closed, down when open),
 *               expands with animation to show full list.
 */
public class TelegaEditHistoryHelper {

    private final View parent;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevronPath = new Path();
    private final RectF hitRect = new RectF();

    private List<String> versions = new ArrayList<>();
    private final ArrayList<StaticLayout> layouts = new ArrayList<>();
    private StaticLayout labelLayout;

    private int measuredWidth;
    private int collapsedHeight;
    private int expandedHeight;
    private float expandProgress; // 0 collapsed, 1 expanded
    private ValueAnimator animator;
    private boolean touchDownInHit;

    private long boundDialogId;
    private int boundMessageId;
    private int account;

    public TelegaEditHistoryHelper(View parent) {
        this.parent = parent;
        textPaint.setTextSize(AndroidUtilities.dp(13));
        labelPaint.setTextSize(AndroidUtilities.dp(12));
        chevronPaint.setStyle(Paint.Style.STROKE);
        chevronPaint.setStrokeWidth(AndroidUtilities.dp(1.6f));
        chevronPaint.setStrokeCap(Paint.Cap.ROUND);
        chevronPaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
    }

    public void bind(MessageObject messageObject, int maxWidth) {
        layouts.clear();
        labelLayout = null;
        versions = new ArrayList<>();
        measuredWidth = Math.max(AndroidUtilities.dp(80), maxWidth);
        collapsedHeight = 0;
        expandedHeight = 0;

        if (messageObject == null || !TelegaSettings.isShowEditedMessages()) {
            boundDialogId = 0;
            boundMessageId = 0;
            return;
        }

        account = messageObject.currentAccount;
        boundDialogId = messageObject.getDialogId();
        boundMessageId = messageObject.getId();

        List<String> history = TelegaEditedMessages.getHistory(account, boundDialogId, boundMessageId);
        if (history == null || history.isEmpty()) {
            return;
        }
        versions.addAll(history);

        int color = Theme.getColor(Theme.key_chat_messageTextIn);
        // muted previous versions
        textPaint.setColor(Theme.multAlpha(color, 0.55f));
        labelPaint.setColor(Theme.multAlpha(color, 0.7f));
        chevronPaint.setColor(Theme.multAlpha(color, 0.75f));
        linePaint.setColor(Theme.multAlpha(color, 0.25f));

        int contentW = measuredWidth - AndroidUtilities.dp(28);

        if (versions.size() == 1) {
            // Show the single previous version always
            StaticLayout sl = buildLayout(versions.get(0), contentW);
            layouts.add(sl);
            collapsedHeight = sl.getHeight() + AndroidUtilities.dp(8);
            expandedHeight = collapsedHeight;
            expandProgress = 1f;
        } else {
            String label = versions.size() + " " + pluralVersions(versions.size());
            labelLayout = new StaticLayout(label, labelPaint, contentW, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            collapsedHeight = Math.max(AndroidUtilities.dp(18), labelLayout.getHeight()) + AndroidUtilities.dp(6);

            int total = 0;
            for (String v : versions) {
                StaticLayout sl = buildLayout(v, contentW);
                layouts.add(sl);
                total += sl.getHeight() + AndroidUtilities.dp(6);
            }
            expandedHeight = collapsedHeight + total + AndroidUtilities.dp(4);

            expandProgress = TelegaEditedMessages.isExpanded(boundDialogId, boundMessageId) ? 1f : 0f;
        }
    }

    private StaticLayout buildLayout(String text, int width) {
        CharSequence ellipsized = TextUtils.ellipsize(text.replace('\n', ' '), textPaint, width * 3f, TextUtils.TruncateAt.END);
        return new StaticLayout(ellipsized, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false);
    }

    private static String pluralVersions(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return "версия";
        }
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) {
            return "версии";
        }
        return "версий";
    }

    public int getHeight() {
        if (versions.isEmpty()) {
            return 0;
        }
        if (versions.size() == 1) {
            return collapsedHeight;
        }
        return Math.round(collapsedHeight + (expandedHeight - collapsedHeight) * expandProgress);
    }

    public boolean hasContent() {
        return !versions.isEmpty();
    }

    /**
     * Draw at (left, top). Returns height used.
     */
    public int draw(Canvas canvas, float left, float top) {
        if (versions.isEmpty()) {
            return 0;
        }

        int h = getHeight();
        float x = left + AndroidUtilities.dp(4);
        float y = top;

        // vertical accent line
        canvas.drawLine(x + AndroidUtilities.dp(3), y + AndroidUtilities.dp(2),
                x + AndroidUtilities.dp(3), y + h - AndroidUtilities.dp(2), linePaint);

        float contentX = x + AndroidUtilities.dp(14);

        if (versions.size() == 1) {
            canvas.save();
            canvas.translate(contentX, y + AndroidUtilities.dp(2));
            layouts.get(0).draw(canvas);
            canvas.restore();
            hitRect.set(0, 0, 0, 0);
            return h;
        }

        // Chevron + label (always visible)
        float chevronCx = x + AndroidUtilities.dp(3);
        float chevronCy = y + AndroidUtilities.dp(10);
        drawChevron(canvas, chevronCx, chevronCy, expandProgress);

        if (labelLayout != null) {
            canvas.save();
            canvas.translate(contentX, y + AndroidUtilities.dp(2));
            labelLayout.draw(canvas);
            canvas.restore();
        }

        // Hit area for toggle
        hitRect.set(left, top, left + measuredWidth, top + collapsedHeight);

        if (expandProgress > 0.01f && !layouts.isEmpty()) {
            canvas.save();
            float listTop = y + collapsedHeight;
            // clip for animation
            canvas.clipRect(left, listTop, left + measuredWidth, top + h);
            canvas.translate(0, listTop);
            // fade + slight slide
            canvas.saveLayerAlpha(0, 0, measuredWidth, expandedHeight, (int) (255 * expandProgress), Canvas.ALL_SAVE_FLAG);
            float yy = AndroidUtilities.dp(2) * (1f - expandProgress);
            canvas.translate(contentX, yy);
            for (int i = 0; i < layouts.size(); i++) {
                StaticLayout sl = layouts.get(i);
                // version index
                String idx = (i + 1) + ".";
                canvas.drawText(idx, -AndroidUtilities.dp(2), textPaint.getTextSize(), textPaint);
                canvas.translate(AndroidUtilities.dp(16), 0);
                sl.draw(canvas);
                canvas.translate(-AndroidUtilities.dp(16), sl.getHeight() + AndroidUtilities.dp(6));
            }
            canvas.restore();
            canvas.restore();
        }

        return h;
    }

    private void drawChevron(Canvas canvas, float cx, float cy, float progress) {
        // progress 0: points right >, 1: points down v
        float size = AndroidUtilities.dp(5);
        chevronPath.reset();
        // interpolate angle from -90 (right) to 0 (down) ... use two lines of a chevron
        double angle = Math.toRadians(-90 + 90 * progress);
        // base chevron pointing down: left-up, center, right-up; rotate around center
        float x1 = -size, y1 = -size * 0.55f;
        float x2 = 0, y2 = size * 0.55f;
        float x3 = size, y3 = -size * 0.55f;
        // rotate by angle (0 = down, -90 = right)
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float rx1 = x1 * cos - y1 * sin;
        float ry1 = x1 * sin + y1 * cos;
        float rx2 = x2 * cos - y2 * sin;
        float ry2 = x2 * sin + y2 * cos;
        float rx3 = x3 * cos - y3 * sin;
        float ry3 = x3 * sin + y3 * cos;
        chevronPath.moveTo(cx + rx1, cy + ry1);
        chevronPath.lineTo(cx + rx2, cy + ry2);
        chevronPath.lineTo(cx + rx3, cy + ry3);
        canvas.drawPath(chevronPath, chevronPaint);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (versions.size() <= 1) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownInHit = hitRect.contains(x, y);
                return touchDownInHit;
            case MotionEvent.ACTION_UP:
                if (touchDownInHit && hitRect.contains(x, y)) {
                    toggle();
                    touchDownInHit = false;
                    return true;
                }
                touchDownInHit = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                touchDownInHit = false;
                break;
        }
        return touchDownInHit;
    }

    public void toggle() {
        boolean expand = expandProgress < 0.5f;
        TelegaEditedMessages.setExpanded(boundDialogId, boundMessageId, expand);
        if (animator != null) {
            animator.cancel();
        }
        float from = expandProgress;
        float to = expand ? 1f : 0f;
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            expandProgress = (float) a.getAnimatedValue();
            parent.requestLayout();
            parent.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                expandProgress = to;
                parent.requestLayout();
                parent.invalidate();
            }
        });
        animator.start();
    }

    public void setHitOffset(float drawLeft, float drawTop) {
        // hitRect already set during draw relative to view coords if left/top are view-local
    }
}
