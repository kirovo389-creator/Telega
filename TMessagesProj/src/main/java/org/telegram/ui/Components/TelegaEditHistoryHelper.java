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
 * Previous versions drawn ABOVE current message text.
 * 1 previous: always show on top.
 * 2+: collapsed bar; expands UPWARD with animation.
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
    private int expandedExtraHeight;
    private float expandProgress;
    private ValueAnimator animator;
    private boolean touchDownInHit;

    private long boundDialogId;
    private int boundMessageId;

    public TelegaEditHistoryHelper(View parent) {
        this.parent = parent;
        textPaint.setTextSize(AndroidUtilities.dp(13));
        labelPaint.setTextSize(AndroidUtilities.dp(12));
        chevronPaint.setStyle(Paint.Style.STROKE);
        chevronPaint.setStrokeWidth(AndroidUtilities.dp(1.6f));
        chevronPaint.setStrokeCap(Paint.Cap.ROUND);
        chevronPaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
    }

    public void bind(MessageObject messageObject, int maxWidth) {
        layouts.clear();
        labelLayout = null;
        versions = new ArrayList<>();
        measuredWidth = Math.max(AndroidUtilities.dp(80), maxWidth);
        collapsedHeight = 0;
        expandedExtraHeight = 0;

        if (messageObject == null || !TelegaSettings.isShowEditedMessages()) {
            boundDialogId = 0;
            boundMessageId = 0;
            return;
        }

        boundDialogId = messageObject.getDialogId();
        boundMessageId = messageObject.getId();

        List<String> history = TelegaEditedMessages.getHistory(messageObject.currentAccount, boundDialogId, boundMessageId);
        if (history == null || history.isEmpty()) {
            return;
        }
        versions.addAll(history);

        int color = Theme.getColor(Theme.key_chat_messageTextIn);
        textPaint.setColor(Theme.multAlpha(color, 0.5f));
        labelPaint.setColor(Theme.multAlpha(color, 0.65f));
        chevronPaint.setColor(Theme.multAlpha(color, 0.7f));
        linePaint.setColor(Theme.multAlpha(color, 0.28f));

        int contentW = measuredWidth - AndroidUtilities.dp(28);

        if (versions.size() == 1) {
            StaticLayout sl = buildLayout(versions.get(0), contentW);
            layouts.add(sl);
            collapsedHeight = sl.getHeight() + AndroidUtilities.dp(10);
            expandProgress = 1f;
        } else {
            String label = versions.size() + " " + pluralVersions(versions.size());
            labelLayout = new StaticLayout(label, labelPaint, contentW, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            for (String v : versions) {
                layouts.add(buildLayout(v, contentW));
            }
            collapsedHeight = Math.max(AndroidUtilities.dp(18), labelLayout.getHeight()) + AndroidUtilities.dp(8);
            int totalList = 0;
            for (StaticLayout sl : layouts) {
                totalList += sl.getHeight() + AndroidUtilities.dp(6);
            }
            expandedExtraHeight = totalList + AndroidUtilities.dp(4);
            expandProgress = TelegaEditedMessages.isExpanded(boundDialogId, boundMessageId) ? 1f : 0f;
        }
    }

    private StaticLayout buildLayout(String text, int width) {
        CharSequence ellipsized = TextUtils.ellipsize(text.replace('\n', ' '), textPaint, width * 4f, TextUtils.TruncateAt.END);
        return new StaticLayout(ellipsized, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false);
    }

    private static String pluralVersions(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "версия";
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return "версии";
        return "версий";
    }

    public int getHeight() {
        if (versions.isEmpty()) return 0;
        if (versions.size() == 1) return collapsedHeight;
        return collapsedHeight + Math.round(expandedExtraHeight * expandProgress);
    }

    public boolean hasContent() {
        return !versions.isEmpty();
    }

    public int draw(Canvas canvas, float left, float top) {
        if (versions.isEmpty()) return 0;

        int h = getHeight();
        float x = left + AndroidUtilities.dp(4);
        float bottom = top + h;

        canvas.drawLine(x + AndroidUtilities.dp(3), top + AndroidUtilities.dp(2),
                x + AndroidUtilities.dp(3), bottom - AndroidUtilities.dp(2), linePaint);

        float contentX = x + AndroidUtilities.dp(14);

        if (versions.size() == 1) {
            canvas.save();
            canvas.translate(contentX, top + AndroidUtilities.dp(2));
            layouts.get(0).draw(canvas);
            canvas.restore();
            hitRect.setEmpty();
            return h;
        }

        // bar near bottom of reserved area (just above current message)
        float barTop = bottom - collapsedHeight;
        drawChevron(canvas, x + AndroidUtilities.dp(3), barTop + AndroidUtilities.dp(10), expandProgress);

        if (labelLayout != null) {
            canvas.save();
            canvas.translate(contentX, barTop + AndroidUtilities.dp(2));
            labelLayout.draw(canvas);
            canvas.restore();
        }

        hitRect.set(left, barTop, left + measuredWidth, bottom);

        // list expands UPWARD above the bar
        if (expandProgress > 0.01f && !layouts.isEmpty()) {
            float listHeight = expandedExtraHeight * expandProgress;
            float listTop = barTop - listHeight;

            canvas.save();
            canvas.clipRect(left, Math.max(top, listTop), left + measuredWidth, barTop);
            int alpha = (int) (255 * expandProgress);
            canvas.saveLayerAlpha(left, listTop, left + measuredWidth, barTop, alpha, Canvas.ALL_SAVE_FLAG);

            float yy = barTop - AndroidUtilities.dp(4);
            for (int i = layouts.size() - 1; i >= 0; i--) {
                StaticLayout sl = layouts.get(i);
                yy -= sl.getHeight();
                canvas.save();
                canvas.translate(contentX + AndroidUtilities.dp(4), yy);
                sl.draw(canvas);
                canvas.restore();
                yy -= AndroidUtilities.dp(6);
            }
            canvas.restore();
            canvas.restore();
        }

        return h;
    }

    private void drawChevron(Canvas canvas, float cx, float cy, float progress) {
        // 0 = right >, 1 = up ^
        float size = AndroidUtilities.dp(5);
        double angle = Math.toRadians(-90 - 90 * progress);
        float x1 = -size, y1 = -size * 0.55f;
        float x2 = 0, y2 = size * 0.55f;
        float x3 = size, y3 = -size * 0.55f;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        chevronPath.reset();
        chevronPath.moveTo(cx + x1 * cos - y1 * sin, cy + x1 * sin + y1 * cos);
        chevronPath.lineTo(cx + x2 * cos - y2 * sin, cy + x2 * sin + y2 * cos);
        chevronPath.lineTo(cx + x3 * cos - y3 * sin, cy + x3 * sin + y3 * cos);
        canvas.drawPath(chevronPath, chevronPaint);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (versions.size() <= 1) return false;
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
        if (animator != null) animator.cancel();
        float from = expandProgress;
        float to = expand ? 1f : 0f;
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(240);
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
}
