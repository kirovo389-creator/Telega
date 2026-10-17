package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/**
 * Root Telega settings — iOS-like grouped list of categories.
 */
public class TelegaSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;

    private int messagesRow;
    private int otherRow;
    private int profileRow;
    private int customRow;
    private int footerRow;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        rowCount = 0;
        messagesRow = rowCount++;
        otherRow = rowCount++;
        profileRow = rowCount++;
        customRow = rowCount++;
        footerRow = rowCount++;
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки Telega");
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        listAdapter = new ListAdapter(context);
        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(24));
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == messagesRow) {
                presentFragment(new TelegaMessagesSettingsActivity());
            } else if (position == otherRow || position == profileRow || position == customRow) {
                // placeholders for future sections
                AndroidUtilities.shakeView(view);
            }
        });

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context ctx;

        ListAdapter(Context c) { ctx = c; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() { return rowCount; }

        @Override
        public int getItemViewType(int position) {
            return position == footerRow ? 1 : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            if (viewType == 1) {
                TextView tv = new TextView(ctx);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                tv.setGravity(Gravity.CENTER);
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                tv.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
                tv.setText("Telega · дополнительные возможности");
                v = tv;
            } else {
                v = new CategoryCell(ctx);
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() != 0) return;
            CategoryCell cell = (CategoryCell) holder.itemView;
            boolean first = position == messagesRow;
            boolean last = position == customRow;
            if (position == messagesRow) {
                cell.setData("💬", "Сообщения", "Удалённые и изменённые", first, last);
            } else if (position == otherRow) {
                cell.setData("✨", "Другое", "Скоро", first, last);
            } else if (position == profileRow) {
                cell.setData("👤", "Профиль", "Скоро", first, last);
            } else if (position == customRow) {
                cell.setData("🎨", "Кастомизация", "Скоро", first, last);
            }
        }
    }

    /** iOS-like rounded grouped row */
    private static class CategoryCell extends FrameLayout {
        private final TextView emoji;
        private final TextView title;
        private final TextView subtitle;
        private final TextView chevron;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean first, last;

        CategoryCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            emoji = new TextView(context);
            emoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            row.addView(emoji, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            texts.addView(title);

            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            chevron = new TextView(context);
            chevron.setText("›");
            chevron.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            chevron.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            row.addView(chevron, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        void setData(String icon, String t, String s, boolean isFirst, boolean isLast) {
            emoji.setText(icon);
            title.setText(t);
            subtitle.setText(s);
            first = isFirst;
            last = isLast;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = AndroidUtilities.dp(14);
            float inset = AndroidUtilities.dp(16);
            rect.set(inset, 0, getWidth() - inset, getHeight());
            float tl = first ? r : 0, tr = first ? r : 0, br = last ? r : 0, bl = last ? r : 0;
            // simple path for top/bottom rounding of group
            PathCompat.drawRoundRect(canvas, rect, tl, tr, br, bl, bgPaint);
            if (!last) {
                Paint divider = new Paint();
                divider.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 90));
                float left = inset + AndroidUtilities.dp(52);
                canvas.drawRect(left, getHeight() - 1, getWidth() - inset, getHeight(), divider);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int h = Math.max(getMeasuredHeight(), AndroidUtilities.dp(58));
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
        }
    }

    /** tiny helper without importing android.graphics.Path for partial rounds */
    private static class PathCompat {
        static void drawRoundRect(Canvas c, RectF r, float tl, float tr, float br, float bl, Paint p) {
            android.graphics.Path path = new android.graphics.Path();
            float[] radii = new float[]{tl, tl, tr, tr, br, br, bl, bl};
            path.addRoundRect(r, radii, android.graphics.Path.Direction.CW);
            c.drawPath(path, p);
        }
    }
}
