package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class TelegaMessagesSettingsActivity extends BaseFragment {

    private int deletedRow;
    private int editedRow;
    private int localEditRow;
    private int infoRow;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        rowCount = 0;
        deletedRow = rowCount++;
        editedRow = rowCount++;
        localEditRow = rowCount++;
        infoRow = rowCount++;
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Сообщения");
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
        listView.setAdapter(new Adapter(context));
        listView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(24));
        listView.setClipToPadding(false);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == deletedRow) {
                boolean n = !TelegaSettings.isShowDeletedMessages();
                TelegaSettings.setShowDeletedMessages(n);
                if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(n);
            } else if (position == editedRow) {
                boolean n = !TelegaSettings.isShowEditedMessages();
                TelegaSettings.setShowEditedMessages(n);
                if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(n);
            } else if (position == localEditRow) {
                boolean n = !TelegaSettings.isLocalEditMessagesEnabled();
                TelegaSettings.setLocalEditMessagesEnabled(n);
                if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(n);
            }
        });

        return fragmentView;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context ctx;

        Adapter(Context c) { ctx = c; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder h) {
            return h.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() { return rowCount; }

        @Override
        public int getItemViewType(int position) {
            return position == infoRow ? 1 : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            if (viewType == 1) {
                TextView tv = new TextView(ctx);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                tv.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(10), AndroidUtilities.dp(28), AndroidUtilities.dp(8));
                tv.setLineSpacing(AndroidUtilities.dp(2), 1f);
                v = tv;
            } else {
                TextCheckCell cell = new TextCheckCell(ctx);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                v = cell;
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                ((TextView) holder.itemView).setText(
                        "Удалённые сообщения остаются в чате с пометкой «Удалено».\n\n" +
                        "Изменённые: сверху — прошлый текст, снизу — текущий. " +
                        "При 2+ правках история сворачивается и раскрывается вверх по нажатию.\n\n" +
                        "«Изменение сообщений» позволяет заменить отображаемый текст входящего сообщения только у вас. Исходное сообщение у собеседника не меняется.");
                return;
            }
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            if (position == deletedRow) {
                cell.setTextAndCheck("Удалённые сообщения", TelegaSettings.isShowDeletedMessages(), true);
                cell.setColorfullIcon(0xFFE23A41, R.drawable.telega_ic_trash);
            } else if (position == editedRow) {
                cell.setTextAndCheck("Изменённые сообщения", TelegaSettings.isShowEditedMessages(), false);
                cell.setColorfullIcon(0xFF52798B, R.drawable.telega_ic_edit);
            } else if (position == localEditRow) {
                cell.setTextAndCheck("Изменение сообщений", TelegaSettings.isLocalEditMessagesEnabled(), false);
                cell.setColorfullIcon(0xFF367CC0, R.drawable.telega_ic_edit);
            }
        }
    }
}
