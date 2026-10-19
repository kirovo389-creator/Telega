package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
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

/** Local filter for hiding bot dialogs and silencing their notifications. */
public class TelegaAntiBotSettingsActivity extends BaseFragment {
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Анти-бот и архив");
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
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public int getItemCount() {
                return 4;
            }

            @Override
            public int getItemViewType(int position) {
                return position <= 1 ? 0 : 1;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == 0;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view;
                if (viewType == 0) {
                    TextCheckCell cell = new TextCheckCell(context);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = cell;
                } else {
                    TextView text = new TextView(context);
                    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                    text.setLineSpacing(AndroidUtilities.dp(2), 1f);
                    text.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(12), AndroidUtilities.dp(28), AndroidUtilities.dp(10));
                    view = text;
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                if (position == 0) {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck("Скрывать ботов и заглушать уведомления", TelegaSettings.isAntiBotEnabled(), true);
                    cell.setColorfullIcon(0xFF367CC0, R.drawable.telega_ic_robot);
                } else if (position == 1) {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck("Скрыть архив полностью", TelegaSettings.isHideArchiveEnabled(), true);
                    cell.setColorfullIcon(0xFF52798B, R.drawable.telega_ic_chat);
                } else if (position == 2) {
                    ((TextView) holder.itemView).setText(
                            "Боты исчезают из списков чатов и не создают уведомления. " +
                            "Переписки не удаляются: отключи фильтр, чтобы снова увидеть их.");
                } else {
                    ((TextView) holder.itemView).setText(
                            "Скрывает архив целиком: личные чаты, группы, каналы, ботов и Избранное. " +
                            "Уведомления из архивных диалогов не показываются. Данные не удаляются и вернутся после выключения функции.");
                }
            }
        });
        listView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(24));
        listView.setClipToPadding(false);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position == 0) {
                boolean enabled = !TelegaSettings.isAntiBotEnabled();
                TelegaSettings.setAntiBotEnabled(enabled);
                if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(enabled);
            } else if (position == 1) {
                boolean enabled = !TelegaSettings.isHideArchiveEnabled();
                TelegaSettings.setHideArchiveEnabled(enabled);
                if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(enabled);
            }
        });
        return fragmentView;
    }
}