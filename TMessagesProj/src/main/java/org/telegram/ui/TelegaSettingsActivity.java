package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
 * Telega settings hub — iOS Settings style:
 * Gray canvas, inset grouped rows, restrained typography, vector leading icons.
 */
public class TelegaSettingsActivity extends BaseFragment {

    private int headerRow;
    private int messagesRow;
    private int otherRow;
    private int proxyRow;
    private int profileRow;
    private int customRow;
    private int footerRow;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        rowCount = 0;
        headerRow = rowCount++;
        messagesRow = rowCount++;
        otherRow = rowCount++;
        proxyRow = rowCount++;
        profileRow = rowCount++;
        customRow = rowCount++;
        footerRow = rowCount++;
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Telega");
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

        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter(context));
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(32));
        listView.setSelectorDrawableColor(0);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == messagesRow) {
                presentFragment(new TelegaMessagesSettingsActivity());
            } else if (position == otherRow) {
                presentFragment(new TelegaOtherSettingsActivity());
            } else if (position == proxyRow) {
                presentFragment(new TelegaLocalBypassSettingsActivity());
            } else if (position == customRow) {
                presentFragment(new TelegaCustomizationActivity());
            } else if (position == profileRow) {
                presentFragment(new TelegaProfileSettingsActivity());
            }
        });

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context ctx;

        ListAdapter(Context c) { ctx = c; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int t = holder.getItemViewType();
            return t == 1;
        }

        @Override
        public int getItemCount() { return rowCount; }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return 0;
            if (position == footerRow) return 2;
            return 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            if (viewType == 0) {
                TextView tv = new TextView(ctx);
                tv.setText("НАСТРОЙКИ");
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                tv.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(18), AndroidUtilities.dp(32), AndroidUtilities.dp(8));
                v = tv;
            } else if (viewType == 2) {
                TextView tv = new TextView(ctx);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                tv.setGravity(Gravity.CENTER);
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                tv.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(16), AndroidUtilities.dp(32), AndroidUtilities.dp(8));
                tv.setText("Дополнительные возможности Telega");
                v = tv;
            } else {
                v = new CategoryCell(ctx);
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() != 1) return;
            CategoryCell cell = (CategoryCell) holder.itemView;
            boolean first = position == messagesRow;
            boolean last = position == customRow;
            if (position == messagesRow) {
                cell.setData(R.drawable.telega_ic_chat, "Сообщения", "Удалённые и изменённые", first, last);
            } else if (position == otherRow) {
                cell.setData(R.drawable.telega_ic_robot, "Другое", "ИИ-агент", first, last);
            } else if (position == proxyRow) {
                cell.setData(R.drawable.telega_ic_wind, "Без VPN", "Обход блокировок Telegram", first, last);
            } else if (position == profileRow) {
                cell.setData(R.drawable.telega_ic_user, "Профиль", "Истории и настройки профиля", first, last);
            } else if (position == customRow) {
                cell.setData(R.drawable.telega_ic_snow, "Кастомизация", "Снег и гирлянда в чатах", first, last);
            }
        }
    }

    private static class CategoryCell extends FrameLayout {
        private final ImageView iconView;
        private final TextView title;
        private final TextView subtitle;
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
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(11), AndroidUtilities.dp(14), AndroidUtilities.dp(11));
            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            texts.addView(title);

            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(ColorUtils.setAlphaComponent(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), (int) (0.55f * 255)));
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

            TextView chevron = new TextView(context);
            chevron.setText("›");
            chevron.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            chevron.setTextColor(ColorUtils.setAlphaComponent(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), (int) (0.3f * 255)));
            row.addView(chevron, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        void setData(int icon, String t, String s, boolean isFirst, boolean isLast) {
            iconView.setImageResource(icon);
            title.setText(t);
            subtitle.setText(s);
            first = isFirst;
            last = isLast;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = AndroidUtilities.dp(12);
            float inset = AndroidUtilities.dp(16);
            rect.set(inset, 0, getWidth() - inset, getHeight());
            float tl = first ? r : 0, tr = first ? r : 0, br = last ? r : 0, bl = last ? r : 0;
            Path path = new Path();
            path.addRoundRect(rect, new float[]{tl, tl, tr, tr, br, br, bl, bl}, Path.Direction.CW);
            canvas.drawPath(path, bgPaint);
            if (!last) {
                Paint divider = new Paint();
                divider.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 80));
                float left = inset + AndroidUtilities.dp(50);
                canvas.drawRect(left, getHeight() - AndroidUtilities.dpf2(0.33f), getWidth() - inset, getHeight(), divider);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                    Math.max(getMeasuredHeight(), AndroidUtilities.dp(52)));
        }
    }
}
