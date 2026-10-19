package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.TelegaLocalMessageEdits;
import org.telegram.messenger.TelegaSettings;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** On-device editor for Telega screens. It never changes server-side data. */
public final class TelegaVisualEditor {
    private static final String PREFS = "telega_visual_editor";
    private static final int PHOTO_REQUEST_CODE = 0x5447;
    private static final Map<BaseFragment, Controller> ACTIVE = new WeakHashMap<>();
    private TelegaVisualEditor() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext == null ? null :
            ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }
    public static String getTarget() {
        SharedPreferences p=prefs(); return p==null?"":p.getString("target","");
    }
    public static void setTarget(String target) {
        SharedPreferences p=prefs(); if(p!=null)p.edit().putString("target",target==null?"":target).apply();
    }
    public static boolean isTarget(BaseFragment f,String target) {
        if(f==null||target==null)return false;
        if("profile".equals(target))return f instanceof ProfileActivity;
        if("chat".equals(target))return f instanceof ChatActivity;
        return "main".equals(target)&&f instanceof DialogsActivity;
    }
    public static void attach(BaseFragment fragment,View view) {
        String target=getTarget();
        if(target.isEmpty()||!isTarget(fragment,target)||!(view instanceof FrameLayout))return;
        detach(fragment,true);
        Controller c=new Controller(fragment,(FrameLayout)view,target);
        synchronized(ACTIVE){ACTIVE.put(fragment,c);}
        c.attach();
    }
    public static void detach(BaseFragment fragment){detach(fragment,true);}
    private static void detach(BaseFragment fragment,boolean revert) {
        Controller c; synchronized(ACTIVE){c=ACTIVE.remove(fragment);}
        if(c!=null)c.detach(revert);
    }
    public static void onActivityResult(BaseFragment fragment,int requestCode,int resultCode,Intent data) {
        if(requestCode!=PHOTO_REQUEST_CODE)return;
        Controller c; synchronized(ACTIVE){c=ACTIVE.get(fragment);}
        if(c!=null)c.onPhotoPicked(resultCode,data);
    }

    private static final class Controller {
        private final BaseFragment fragment;
        private final FrameLayout root;
        private final String target;
        private final SharedPreferences preferences;
        private final Map<View,float[]> originals=new WeakHashMap<>();
        private final Map<TextView,CharSequence> originalTexts=new WeakHashMap<>();
        private final Map<View,Boolean> originalLongClickable=new WeakHashMap<>();
        private final Map<View,Boolean> editorLongClickInstalled=new WeakHashMap<>();
        private final Map<String,View> views=new HashMap<>();
        private final Map<String,String> draftTexts=new HashMap<>();
        private LinearLayout toolbar;
        private TextView label,editButton;
        private View selected;
        private String selectedPath;
        private ImageView photo;
        private Uri photoUri;
        private boolean detached;

        Controller(BaseFragment f,FrameLayout r,String t){fragment=f;root=r;target=t;preferences=prefs();}
        void attach(){if(preferences==null)return;register(root,"r");loadPhoto();makeToolbar();}

        private void register(View v,String path){
            views.put(path,v);
            originals.put(v,new float[]{v.getTranslationX(),v.getTranslationY(),v.getScaleX(),v.getScaleY(),v.getRotation()});
            applyTransform(v,path);
            boolean wasLongClickable=v.isLongClickable();
            originalLongClickable.put(v,wasLongClickable);
            // Keep built-in long-press behaviour such as message menus intact.
            if(!wasLongClickable){
                v.setOnLongClickListener(w->{select(w);return true;});
                v.setLongClickable(true);
                editorLongClickInstalled.put(v,true);
            }else{
                editorLongClickInstalled.put(v,false);
            }
            if(v instanceof TextView){
                TextView t=(TextView)v;originalTexts.put(t,t.getText());
                String saved=preferences.getString(textKey(path),null);if(saved!=null)t.setText(saved);
            }
            if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)register(g.getChildAt(i),path+"."+i);}
        }
        private String textKey(String p){return "text_"+target+"_"+p;}
        private String transformKey(String p,String f){return "transform_"+target+"_"+p+"_"+f;}
        private void applyTransform(View v,String p){
            v.setTranslationX(preferences.getFloat(transformKey(p,"x"),v.getTranslationX()));
            v.setTranslationY(preferences.getFloat(transformKey(p,"y"),v.getTranslationY()));
            v.setScaleX(preferences.getFloat(transformKey(p,"sx"),v.getScaleX()));
            v.setScaleY(preferences.getFloat(transformKey(p,"sy"),v.getScaleY()));
            v.setRotation(preferences.getFloat(transformKey(p,"r"),v.getRotation()));
        }
        private void makeToolbar(){
            toolbar=new LinearLayout(root.getContext());toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(AndroidUtilities.dp(5),AndroidUtilities.dp(4),AndroidUtilities.dp(5),AndroidUtilities.dp(4));
            toolbar.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),Theme.getColor(Theme.key_windowBackgroundWhite)));
            toolbar.setElevation(AndroidUtilities.dp(8));
            button("+ фото",this::pickPhoto);
            label=new TextView(root.getContext());label.setText("Редактор");label.setTextSize(11);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));label.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.addView(label,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            editButton=button("✎",this::editText);editButton.setVisibility(View.GONE);
            button("Сохранить",this::save);button("Сбросить",this::reset);button("Выйти",this::exit);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            lp.setMargins(AndroidUtilities.dp(5),AndroidUtilities.dp(3),AndroidUtilities.dp(5),AndroidUtilities.dp(6));
            root.addView(toolbar,lp);toolbar.bringToFront();
        }
        private TextView button(String title,Runnable action){
            TextView b=new TextView(root.getContext());b.setText(title);b.setTextSize(11);
            b.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
            b.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));b.setGravity(Gravity.CENTER);
            b.setPadding(AndroidUtilities.dp(6),AndroidUtilities.dp(8),AndroidUtilities.dp(6),AndroidUtilities.dp(8));
            b.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8),Theme.getColor(Theme.key_featuredStickers_addButton)));
            b.setOnClickListener(v->action.run());toolbar.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));return b;
        }
        private void select(View view){
            if(selected!=null)selected.setOnTouchListener(null);selected=view;selectedPath=null;
            for(Map.Entry<String,View> e:views.entrySet())if(e.getValue()==view){selectedPath=e.getKey();break;}
            if(selectedPath==null)return;view.setOnTouchListener(transformTouch());
            if(editButton!=null)editButton.setVisibility(
                    view instanceof TextView || findMessageObject(view) != null ? View.VISIBLE : View.GONE);
            if(label!=null){String s=view instanceof TextView?String.valueOf(((TextView)view).getText()):view.getClass().getSimpleName();if(s.length()>15)s=s.substring(0,15)+"…";label.setText(s);}
        }
        private void editText(){
            if (selected == null || fragment.getParentActivity() == null) return;
            MessageObject message = findMessageObject(selected);
            if (message != null && message.messageOwner != null && message.getId() > 0) {
                // This existing Telega feature changes only the local message layout, not the sent message.
                TelegaSettings.setLocalEditMessagesEnabled(true);
                TelegaLocalMessageEdits.showEditor(root.getContext(), message.currentAccount,
                        message.getDialogId(), message.getId(), message.messageOwner.message, () -> {
                            message.resetLayout();
                            root.requestLayout();
                            root.invalidate();
                            NotificationCenter.getInstance(message.currentAccount).postNotificationName(
                                    NotificationCenter.updateInterfaces, 0);
                            if (label != null) label.setText("Изменение сообщения сохранено локально");
                        });
                return;
            }
            if (!(selected instanceof TextView) || selectedPath == null) return;
            EditText input = new EditText(fragment.getParentActivity());
            input.setText(((TextView) selected).getText());
            input.setSelection(input.length());
            new AlertDialog.Builder(fragment.getParentActivity()).setTitle("Изменить текст").setView(input)
                    .setPositiveButton("Применить", (d, w) -> {
                        String s = input.getText() == null ? "" : input.getText().toString();
                        ((TextView) selected).setText(s);
                        draftTexts.put(selectedPath, s);
                        label.setText("Текст изменён");
                    })
                    .setNegativeButton("Отмена", null).show();
        }

        private MessageObject findMessageObject(View view) {
            if (view == null) return null;
            Class<?> type = view.getClass();
            while (type != null && type != Object.class) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (!MessageObject.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        Object value = field.get(view);
                        if (value instanceof MessageObject) return (MessageObject) value;
                    } catch (Throwable ignored) {
                    }
                }
                for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                    if (method.getParameterTypes().length != 0
                            || !MessageObject.class.isAssignableFrom(method.getReturnType())) continue;
                    try {
                        method.setAccessible(true);
                        Object value = method.invoke(view);
                        if (value instanceof MessageObject) return (MessageObject) value;
                    } catch (Throwable ignored) {
                    }
                }
                type = type.getSuperclass();
            }
            return null;
        }
        private View.OnTouchListener transformTouch(){
            return new View.OnTouchListener(){
                float downX,downY,tx,ty,sx,sy,rot,span,angle;
                public boolean onTouch(View v,MotionEvent e){
                    switch(e.getActionMasked()){
                        case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();tx=v.getTranslationX();ty=v.getTranslationY();sx=v.getScaleX();sy=v.getScaleY();rot=v.getRotation();return true;
                        case MotionEvent.ACTION_POINTER_DOWN:if(e.getPointerCount()>=2){span=span(e);angle=angle(e);sx=v.getScaleX();sy=v.getScaleY();rot=v.getRotation();}return true;
                        case MotionEvent.ACTION_MOVE:
                            if(e.getPointerCount()>=2&&span>0){float r=Math.max(.25f,Math.min(4f,span(e)/span));v.setScaleX(Math.max(.25f,Math.min(4f,sx*r)));v.setScaleY(Math.max(.25f,Math.min(4f,sy*r)));v.setRotation(rot+angleDelta(angle,angle(e)));}
                            else{v.setTranslationX(tx+e.getRawX()-downX);v.setTranslationY(ty+e.getRawY()-downY);}return true;
                        case MotionEvent.ACTION_POINTER_UP:span=0;return true;
                        case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:span=0;return true;
                    }return true;
                }
            };
        }
        private float span(MotionEvent e){if(e.getPointerCount()<2)return 0;float x=e.getX(0)-e.getX(1),y=e.getY(0)-e.getY(1);return(float)Math.sqrt(x*x+y*y);}
        private float angle(MotionEvent e){return e.getPointerCount()<2?0:(float)Math.toDegrees(Math.atan2(e.getY(1)-e.getY(0),e.getX(1)-e.getX(0)));}
        private float angleDelta(float a,float b){float d=b-a;while(d>180)d-=360;while(d< -180)d+=360;return d;}
        private void pickPhoto(){
            if(fragment.getParentActivity()==null)return;
            try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);fragment.startActivityForResult(i,PHOTO_REQUEST_CODE);
            }catch(Throwable e){if(label!=null)label.setText("Не удалось открыть фото");}
        }
        void onPhotoPicked(int code,Intent data){
            if(code!=Activity.RESULT_OK||data==null||data.getData()==null)return;photoUri=data.getData();
            if(Build.VERSION.SDK_INT>=19&&fragment.getParentActivity()!=null)try{fragment.getParentActivity().getContentResolver()
                .takePersistableUriPermission(photoUri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
            showPhoto(photoUri);
        }
        private void loadPhoto(){String raw=preferences.getString("photo_"+target,null);if(raw!=null&&!raw.isEmpty())try{photoUri=Uri.parse(raw);showPhoto(photoUri);}catch(Throwable ignored){photoUri=null;}}
        private void showPhoto(Uri uri){
            if(photo!=null)root.removeView(photo);photo=new ImageView(root.getContext());photo.setScaleType(ImageView.ScaleType.FIT_CENTER);photo.setImageURI(uri);
            photo.setContentDescription("Фото редактора Telega");photo.setOnTouchListener(transformTouch());
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(AndroidUtilities.dp(140),AndroidUtilities.dp(140),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            lp.topMargin=AndroidUtilities.dp(90);root.addView(photo,lp);
            photo.setTranslationX(preferences.getFloat("photo_"+target+"_x",0));photo.setTranslationY(preferences.getFloat("photo_"+target+"_y",0));
            float s=preferences.getFloat("photo_"+target+"_scale",1f);photo.setScaleX(s);photo.setScaleY(s);photo.setRotation(preferences.getFloat("photo_"+target+"_rotation",0));
            if(toolbar!=null)toolbar.bringToFront();
        }
        private void save(){
            SharedPreferences.Editor ed=preferences.edit();for(Map.Entry<String,String> e:draftTexts.entrySet())ed.putString(textKey(e.getKey()),e.getValue());
            for(Map.Entry<String,View> e:views.entrySet()){View v=e.getValue();String p=e.getKey();
                ed.putFloat(transformKey(p,"x"),v.getTranslationX()).putFloat(transformKey(p,"y"),v.getTranslationY()).putFloat(transformKey(p,"sx"),v.getScaleX()).putFloat(transformKey(p,"sy"),v.getScaleY()).putFloat(transformKey(p,"r"),v.getRotation());}
            if(photo!=null&&photoUri!=null)ed.putString("photo_"+target,photoUri.toString()).putFloat("photo_"+target+"_x",photo.getTranslationX()).putFloat("photo_"+target+"_y",photo.getTranslationY()).putFloat("photo_"+target+"_scale",photo.getScaleX()).putFloat("photo_"+target+"_rotation",photo.getRotation());
            ed.apply();if(label!=null)label.setText("Сохранено");
        }
        private void reset(){
            SharedPreferences.Editor ed=preferences.edit();for(String k:preferences.getAll().keySet())if(k.startsWith("text_"+target+"_")||k.startsWith("transform_"+target+"_")||k.startsWith("photo_"+target))ed.remove(k);ed.apply();
            restoreOriginals();draftTexts.clear();if(selected!=null)selected.setOnTouchListener(null);selected=null;selectedPath=null;if(photo!=null)root.removeView(photo);photo=null;photoUri=null;
            if(label!=null)label.setText("Изменения сброшены");if(editButton!=null)editButton.setVisibility(View.GONE);
        }
        private void restoreOriginals(){
            for(Map.Entry<TextView,CharSequence> e:originalTexts.entrySet())if(e.getKey()!=null)e.getKey().setText(e.getValue());
            for(Map.Entry<View,float[]> e:originals.entrySet()){View v=e.getKey();float[] a=e.getValue();if(v!=null){v.setTranslationX(a[0]);v.setTranslationY(a[1]);v.setScaleX(a[2]);v.setScaleY(a[3]);v.setRotation(a[4]);}}
        }
        private void exit(){setTarget("");detach(false);synchronized(ACTIVE){ACTIVE.remove(fragment);}}
        void detach(boolean revert){
            if(detached)return;detached=true;if(revert)restoreOriginals();
            for(View v:originalLongClickable.keySet())if(v!=null){
                if(Boolean.TRUE.equals(editorLongClickInstalled.get(v)))v.setOnLongClickListener(null);
                Boolean old=originalLongClickable.get(v);
                if(old!=null)v.setLongClickable(old);
            }
            if(selected!=null)selected.setOnTouchListener(null);
            if(photo!=null){photo.setOnTouchListener(null);if(revert)root.removeView(photo);}
            if(toolbar!=null)root.removeView(toolbar);
        }
    }
}
