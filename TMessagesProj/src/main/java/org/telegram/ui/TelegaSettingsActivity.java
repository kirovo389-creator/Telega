package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class TelegaSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int messagesHeaderRow;
    private int deletedMessagesRow;
    private int editedMessagesRow;
    private int messagesInfoRow;
    private int messagesSectionRow;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;
        messagesHeaderRow = rowCount++;
        deletedMessagesRow = rowCount++;
        editedMessagesRow = rowCount++;
        messagesInfoRow = rowCount++;
        messagesSectionRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки Telega");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);
        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == deletedMessagesRow) {
                boolean next = !TelegaSettings.isShowDeletedMessages();
                TelegaSettings.setShowDeletedMessages(next);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(next);
                }
            } else if (position == editedMessagesRow) {
                boolean next = !TelegaSettings.isShowEditedMessages();
                TelegaSettings.setShowEditedMessages(next);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(next);
                }
            }
        });

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 1;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == messagesHeaderRow) {
                        headerCell.setText("Сообщения");
                    }
                    break;
                }
                case 1: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == deletedMessagesRow) {
                        cell.setTextAndCheck("Удалённые сообщения", TelegaSettings.isShowDeletedMessages(), true);
                    } else if (position == editedMessagesRow) {
                        cell.setTextAndCheck("Изменённые сообщения", TelegaSettings.isShowEditedMessages(), false);
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == messagesInfoRow) {
                        cell.setText("Удалённые сообщения остаются в чате с пометкой «Удалено».\n\nИзменённые сообщения сохраняют предыдущие версии: после первого изменения сверху видно исходный текст, при следующих правках история сворачивается и раскрывается по нажатию.");
                        cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 3: {
                    holder.itemView.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == messagesHeaderRow) {
                return 0;
            }
            if (position == deletedMessagesRow || position == editedMessagesRow) {
                return 1;
            }
            if (position == messagesInfoRow) {
                return 2;
            }
            return 3;
        }
    }
}
