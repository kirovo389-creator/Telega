package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TelegaVisualEditor;

/** Screen selection for the local Telega editor. */
public class TelegaEditorActivity extends BaseFragment {
    @Override public android.view.View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setAllowOverlayTitle(true);actionBar.setTitle("Редактор");
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick(){
            @Override public void onItemClick(int id){if(id==-1)finishFragment();}
        });
        FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(18),AndroidUtilities.dp(18),AndroidUtilities.dp(18),AndroidUtilities.dp(18));
        content.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),Theme.getColor(Theme.key_windowBackgroundWhite)));
        content.addView(text(context,"Что редактируем?",20,true));
        content.addView(text(context,"Удерживай текст или кнопку, чтобы выбрать. Одним пальцем перемещай, двумя — меняй размер и наклон. Фото и изменения сохраняются только в Telega на этом устройстве.",14,false),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,LayoutHelper.WRAP_CONTENT,0,8,0,12));
        option(context,content,"Профиль","Твой профиль и его элементы",this::openProfile);
        option(context,content,"Чаты","Список чатов и открытые переписки",()->openTarget("chat"));
        option(context,content,"Главная","Главная страница, поиск и список диалогов",()->openTarget("main"));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        lp.setMargins(AndroidUtilities.dp(16),AndroidUtilities.dp(16),AndroidUtilities.dp(16),AndroidUtilities.dp(16));
        root.addView(content,lp);fragmentView=root;return root;
    }
    private void openProfile(){long id=UserConfig.getInstance(currentAccount).getClientUserId();if(id==0){Toast.makeText(getParentActivity(),"Сначала войди в аккаунт",Toast.LENGTH_SHORT).show();return;}
        TelegaVisualEditor.setTarget("profile");presentFragment(ProfileActivity.of(id));}
    private void openTarget(String target){TelegaVisualEditor.setTarget(target);presentFragment(new DialogsActivity(null));}
    private static TextView text(Context c,String s,int size,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setLineSpacing(AndroidUtilities.dp(2),1f);return v;}
    private void option(Context c,LinearLayout parent,String title,String detail,Runnable action){
        LinearLayout item=new LinearLayout(c);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(AndroidUtilities.dp(14),AndroidUtilities.dp(13),AndroidUtilities.dp(14),AndroidUtilities.dp(13));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));bg.setCornerRadius(AndroidUtilities.dp(11));item.setBackground(bg);
        item.addView(text(c,title,16,true));item.addView(text(c,detail,13,false),LayoutHelper.createLinear(-1,-2,0,4,0,0));item.setOnClickListener(v->action.run());
        parent.addView(item,LayoutHelper.createLinear(-1,-2,0,0,0,8));
    }
}