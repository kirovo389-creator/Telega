package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import java.lang.reflect.Field;
import java.util.ArrayList;

/** Hides last-seen server-side and restores the user's previous privacy rules on disable. */
public final class TelegaGhostMode {
    public interface Callback { void onComplete(boolean success,String error); }
    private TelegaGhostMode(){}

    public static void setEnabled(int account,boolean enabled,Callback callback){
        if(TelegaSettings.isGhostModeEnabled()==enabled){complete(callback,true,null);return;}
        if(enabled)enable(account,callback);else disable(account,callback);
    }
    private static void enable(int account,Callback callback){
        TL_account.getPrivacy get=new TL_account.getPrivacy();get.key=new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        ConnectionsManager.getInstance(account).sendRequest(get,(response,error)->{
            if(error!=null||!(response instanceof TL_account.privacyRules)){complete(callback,false,errorText(error,"Не удалось получить текущую приватность"));return;}
            TL_account.privacyRules current=(TL_account.privacyRules)response;
            MessagesController.getInstance(account).putUsers(current.users,false);MessagesController.getInstance(account).putChats(current.chats,false);
            JSONObject backup=encode(current.rules);if(backup==null){complete(callback,false,"Есть неподдерживаемое правило приватности; ничего не изменено.");return;}
            TelegaSettings.setGhostPrivacyBackup(backup.toString());
            TL_account.setPrivacy set=new TL_account.setPrivacy();set.key=new TLRPC.TL_inputPrivacyKeyStatusTimestamp();set.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
            ConnectionsManager.getInstance(account).sendRequest(set,(r,e)->AndroidUtilities.runOnUIThread(()->{
                if(e!=null||!(r instanceof TL_account.privacyRules)){TelegaSettings.setGhostPrivacyBackup("");complete(callback,false,errorText(e,"Сервер не принял скрытие времени активности"));return;}
                accept(account,(TL_account.privacyRules)r);TelegaSettings.setGhostModeEnabled(true);complete(callback,true,null);
            }));
        });
    }
    private static void disable(int account,Callback callback){
        ArrayList<TLRPC.InputPrivacyRule> rules=decode(account,TelegaSettings.getGhostPrivacyBackup());
        if(rules==null){complete(callback,false,"Не удалось восстановить прежние настройки приватности. Режим оставлен включённым.");return;}
        TL_account.setPrivacy set=new TL_account.setPrivacy();set.key=new TLRPC.TL_inputPrivacyKeyStatusTimestamp();set.rules.addAll(rules);
        ConnectionsManager.getInstance(account).sendRequest(set,(r,e)->AndroidUtilities.runOnUIThread(()->{
            if(e!=null||!(r instanceof TL_account.privacyRules)){complete(callback,false,errorText(e,"Не удалось восстановить прежнюю приватность"));return;}
            accept(account,(TL_account.privacyRules)r);TelegaSettings.setGhostModeEnabled(false);TelegaSettings.setGhostPrivacyBackup("");complete(callback,true,null);
        }));
    }
    private static JSONObject encode(ArrayList<TLRPC.PrivacyRule> source){
        try{JSONObject out=new JSONObject();JSONArray arr=new JSONArray();if(source!=null)for(TLRPC.PrivacyRule rule:source){
            if(rule==null)return null;String type=rule.getClass().getSimpleName().replaceFirst("^TL_privacyValue","");
            if(!supported(type))return null;JSONObject item=new JSONObject();item.put("type",type);
            if("AllowUsers".equals(type)||"DisallowUsers".equals(type))item.put("users",readLongList(rule,"users"));
            if("AllowChatParticipants".equals(type)||"DisallowChatParticipants".equals(type))item.put("chats",readLongList(rule,"chats"));arr.put(item);
        }out.put("rules",arr);return out;}catch(Throwable e){FileLog.e(e);return null;}
    }
    private static boolean supported(String s){return "AllowAll".equals(s)||"DisallowAll".equals(s)||"AllowContacts".equals(s)||"DisallowContacts".equals(s)
        ||"AllowUsers".equals(s)||"DisallowUsers".equals(s)||"AllowChatParticipants".equals(s)||"DisallowChatParticipants".equals(s)
        ||"AllowPremium".equals(s)||"AllowBots".equals(s)||"DisallowBots".equals(s);}
    private static JSONArray readLongList(Object o,String field)throws Exception{JSONArray a=new JSONArray();Field f=o.getClass().getField(field);Object raw=f.get(o);
        if(raw instanceof Iterable)for(Object x:(Iterable<?>)raw)if(x instanceof Number)a.put(((Number)x).longValue());return a;}
    private static ArrayList<TLRPC.InputPrivacyRule> decode(int account,String backup){
        try{if(backup==null||backup.isEmpty())return null;JSONArray arr=new JSONObject(backup).getJSONArray("rules");ArrayList<TLRPC.InputPrivacyRule> out=new ArrayList<>();
            for(int i=0;i<arr.length();i++){JSONObject item=arr.getJSONObject(i);String type=item.getString("type");TLRPC.InputPrivacyRule rule;
                switch(type){
                    case "AllowAll":rule=new TLRPC.TL_inputPrivacyValueAllowAll();break;
                    case "DisallowAll":rule=new TLRPC.TL_inputPrivacyValueDisallowAll();break;
                    case "AllowContacts":rule=new TLRPC.TL_inputPrivacyValueAllowContacts();break;
                    case "DisallowContacts":rule=new TLRPC.TL_inputPrivacyValueDisallowContacts();break;
                    case "AllowPremium":rule=new TLRPC.TL_inputPrivacyValueAllowPremium();break;
                    case "AllowBots":rule=new TLRPC.TL_inputPrivacyValueAllowBots();break;
                    case "DisallowBots":rule=new TLRPC.TL_inputPrivacyValueDisallowBots();break;
                    case "AllowUsers":case "DisallowUsers":{
                        TLRPC.TL_inputPrivacyValueAllowUsers allow=null;TLRPC.TL_inputPrivacyValueDisallowUsers deny=null;
                        if("AllowUsers".equals(type))allow=new TLRPC.TL_inputPrivacyValueAllowUsers();else deny=new TLRPC.TL_inputPrivacyValueDisallowUsers();
                        JSONArray ids=item.getJSONArray("users");for(int n=0;n<ids.length();n++){TLRPC.User u=MessagesController.getInstance(account).getUser(ids.getLong(n));if(u==null)return null;
                            TLRPC.InputUser iu=MessagesController.getInstance(account).getInputUser(u);if(iu==null)return null;if(allow!=null)allow.users.add(iu);else deny.users.add(iu);}
                        rule=allow!=null?allow:deny;break;}
                    case "AllowChatParticipants":case "DisallowChatParticipants":{
                        TLRPC.TL_inputPrivacyValueAllowChatParticipants allow=null;TLRPC.TL_inputPrivacyValueDisallowChatParticipants deny=null;
                        if("AllowChatParticipants".equals(type))allow=new TLRPC.TL_inputPrivacyValueAllowChatParticipants();else deny=new TLRPC.TL_inputPrivacyValueDisallowChatParticipants();
                        JSONArray ids=item.getJSONArray("chats");for(int n=0;n<ids.length();n++){long id=ids.getLong(n);if(allow!=null)allow.chats.add(id);else deny.chats.add(id);}
                        rule=allow!=null?allow:deny;break;}
                    default:return null;
                }out.add(rule);
            }return out;
        }catch(Throwable e){FileLog.e(e);return null;}
    }
    private static void accept(int account,TL_account.privacyRules r){MessagesController.getInstance(account).putUsers(r.users,false);MessagesController.getInstance(account).putChats(r.chats,false);
        ContactsController.getInstance(account).setPrivacyRules(r.rules,ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);}
    private static String errorText(TLRPC.TL_error e,String fallback){return e!=null&&e.text!=null&&!e.text.isEmpty()?e.text:fallback;}
    private static void complete(Callback cb,boolean ok,String err){if(cb!=null)AndroidUtilities.runOnUIThread(()->cb.onComplete(ok,err));}
}
