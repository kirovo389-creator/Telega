#!/usr/bin/env python3
"""Apply the requested Telega local-edit, archive, and glass UI fixes after upstream sync."""
from pathlib import Path
import re

ROOT = Path("TMessagesProj/src/main/java/org/telegram")
def patch_file(relative, transform):
    path = ROOT / relative
    if not path.exists():
        raise RuntimeError("ERROR: required source file missing: " + str(path))
    old = path.read_text(encoding="utf-8")
    new = transform(old)
    if new != old:
        path.write_text(new, encoding="utf-8")
        print("Patched " + str(path))
    else:
        print("Already patched: " + str(path))

def patch_enter_view(text):
    field_anchor = "    private MessageObject editingMessageObject;\n    private boolean editingCaption;"
    fields = ("    private MessageObject editingMessageObject;\n"
              "    private boolean telegaLocalEditMode;\n"
              "    private Runnable telegaLocalEditSaved;\n"
              "    private boolean editingCaption;")
    if "private boolean telegaLocalEditMode;" not in text:
        if field_anchor not in text:
            raise RuntimeError("ERROR: ChatActivityEnterView editing fields anchor not found")
        text = text.replace(field_anchor, fields, 1)

    method_anchor = "    public void setEditingMessageObject(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean caption) {"
    if "public void setTelegaLocalEditMessage(" not in text:
        method = (
            "    /** Enters Telegram's normal composer UI, but saves an incoming-message override locally. */\n"
            "    public void setTelegaLocalEditMessage(MessageObject target, Runnable onSaved) {\n"
            "        if (target == null || target.messageOwner == null || target.getId() <= 0) return;\n"
            "        telegaLocalEditMode = true;\n"
            "        telegaLocalEditSaved = onSaved;\n"
            "        if (editingMessageObject == target) {\n"
            "            String override = org.telegram.messenger.TelegaLocalMessageEdits.getOverride(currentAccount, target.getDialogId(), target.getId());\n"
            "            CharSequence initial = override != null ? override : target.messageText;\n"
            "            if (initial == null) initial = target.messageOwner.message;\n"
            "            setFieldText(initial == null ? \"\" : initial);\n"
            "            if (messageEditText != null) messageEditText.requestFocus();\n"
            "            openKeyboard();\n"
            "            return;\n"
            "        }\n"
            "        setEditingMessageObject(target, null, false);\n"
            "    }\n\n"
        )
        if method_anchor not in text:
            raise RuntimeError("ERROR: setEditingMessageObject anchor not found")
        text = text.replace(method_anchor, method + method_anchor, 1)

    old_start = (
        "    public void setEditingMessageObject(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean caption) {\n"
        "        if (audioToSend != null || videoToSendMessageObject != null || editingMessageObject == messageObject) {"
    )
    new_start = (
        "    public void setEditingMessageObject(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean caption) {\n"
        "        if (messageObject == null && telegaLocalEditMode) {\n"
        "            telegaLocalEditMode = false;\n"
        "            telegaLocalEditSaved = null;\n"
        "        }\n"
        "        if (audioToSend != null || videoToSendMessageObject != null || editingMessageObject == messageObject) {"
    )
    if "if (messageObject == null && telegaLocalEditMode)" not in text:
        if old_start not in text:
            raise RuntimeError("ERROR: edit mode start anchor not found")
        text = text.replace(old_start, new_start, 1)

    old_text = "                editingText = editingMessageObject.messageText;"
    new_text = (
        "                String telegaOverride = telegaLocalEditMode\n"
        "                        ? org.telegram.messenger.TelegaLocalMessageEdits.getOverride(currentAccount, editingMessageObject.getDialogId(), editingMessageObject.getId())\n"
        "                        : null;\n"
        "                editingText = telegaOverride != null ? telegaOverride : editingMessageObject.messageText;"
    )
    if "String telegaOverride = telegaLocalEditMode" not in text:
        if old_text not in text:
            raise RuntimeError("ERROR: editing text source anchor not found")
        text = text.replace(old_text, new_text, 1)

    done_anchor = (
        "    public void doneEditingMessage() {\n"
        "        if (editingMessageObject == null) {\n"
        "            return;\n"
        "        }\n"
    )
    local_save = (
        "    public void doneEditingMessage() {\n"
        "        if (editingMessageObject == null) {\n"
        "            return;\n"
        "        }\n"
        "        if (telegaLocalEditMode) {\n"
        "            String localText = messageEditText == null ? \"\" : messageEditText.getTextToUse().toString();\n"
        "            if (android.text.TextUtils.isEmpty(localText.trim())) {\n"
        "                AndroidUtilities.shakeViewSpring(messageEditText, -3);\n"
        "                return;\n"
        "            }\n"
        "            MessageObject target = editingMessageObject;\n"
        "            Runnable callback = telegaLocalEditSaved;\n"
        "            org.telegram.messenger.TelegaLocalMessageEdits.saveOverride(currentAccount, target.getDialogId(), target.getId(), localText);\n"
        "            telegaLocalEditMode = false;\n"
        "            telegaLocalEditSaved = null;\n"
        "            setEditingMessageObject(null, null, false);\n"
        "            if (callback != null) callback.run();\n"
        "            return;\n"
        "        }\n"
    )
    if "if (telegaLocalEditMode) {\n            String localText" not in text:
        if done_anchor not in text:
            raise RuntimeError("ERROR: doneEditingMessage anchor not found")
        text = text.replace(done_anchor, local_save, 1)
    return text

def patch_chat_activity(text):
    old_label = 'items.add("Изменить (только у меня)");'
    if old_label in text:
        text = text.replace(old_label, 'items.add("Изменить");', 1)
    pattern = re.compile(r'            case OPTION_TELEGA_LOCAL_EDIT:\s*\{.*?\n            case OPTION_SUGGESTION_EDIT_MESSAGE:', re.S)
    match = pattern.search(text)
    if not match:
        if "setTelegaLocalEditMessage(target" in text:
            return text
        raise RuntimeError("ERROR: local-edit menu handler block not found in ChatActivity")
    replacement = """            case OPTION_TELEGA_LOCAL_EDIT: {
                final MessageObject target = selectedObject;
                if (target != null && target.messageOwner != null && !target.isOutOwner()
                        && chatActivityEnterView != null) {
                    if (searchItem != null && actionBar.isSearchFieldVisible()) {
                        actionBar.closeSearchField();
                        chatActivityEnterView.setFieldFocused();
                    }
                    if (mentionContainer != null) {
                        mentionContainer.getAdapter().setNeedBotContext(false);
                    }
                    chatActivityEnterView.setVisibility(View.VISIBLE);
                    showFieldPanelForEdit(true, target);
                    updateBottomOverlay();
                    chatActivityEnterView.setAllowStickersAndGifs(true, false, false, true);
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                    chatActivityEnterView.setTelegaLocalEditMessage(target, () -> {
                        target.generateLayout(null);
                        if (chatListView != null && chatListView.getAdapter() != null) {
                            chatListView.getAdapter().notifyDataSetChanged();
                        }
                        updateVisibleRows();
                    });
                }
                selectedObject = null;
                selectedObjectGroup = null;
                selectedObjectToEditCaption = null;
                break;
            }
            case OPTION_SUGGESTION_EDIT_MESSAGE:"""
    text = text[:match.start()] + replacement + text[match.end():]
    return text

def patch_dialogs(text):
    # A broader archive filter is already committed directly in DialogsActivity.
    # Do not try to wrap the original upstream method a second time during CI.
    if "getDialogsArrayInternal" in text:
        print("Archive filter already integrated; preserving existing implementation")
        return text
    old = """    @NonNull
    public ArrayList<TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        if (frozen && frozenDialogsList != null) {
            return frozenDialogsList;
        }
        MessagesController messagesController = AccountInstance.getInstance(currentAccount).getMessagesController();
        if (dialogsType == DIALOGS_TYPE_DEFAULT) {
            return messagesController.getDialogs(folderId);
        } else if (dialogsType == DIALOGS_TYPE_WIDGET || dialogsType == DIALOGS_TYPE_IMPORT_HISTORY) {"""
    new = """    @NonNull
    public ArrayList<TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        MessagesController messagesController = AccountInstance.getInstance(currentAccount).getMessagesController();
        if (org.telegram.messenger.TelegaSettings.isArchiveHidden() && dialogsType == DIALOGS_TYPE_DEFAULT) {
            if (folderId == 1) {
                return new ArrayList<>();
            }
            ArrayList<TLRPC.Dialog> source = frozen && frozenDialogsList != null
                    ? frozenDialogsList : messagesController.getDialogs(folderId);
            ArrayList<TLRPC.Dialog> visible = new ArrayList<>(source);
            for (int i = visible.size() - 1; i >= 0; i--) {
                TLRPC.Dialog dialog = visible.get(i);
                if (dialog != null && dialog.folder_id == 1) {
                    visible.remove(i);
                }
            }
            return visible;
        }
        if (frozen && frozenDialogsList != null) {
            return frozenDialogsList;
        }
        if (dialogsType == DIALOGS_TYPE_DEFAULT) {
            return messagesController.getDialogs(folderId);
        } else if (dialogsType == DIALOGS_TYPE_WIDGET || dialogsType == DIALOGS_TYPE_IMPORT_HISTORY) {"""
    if "TelegaSettings.isArchiveHidden() && dialogsType == DIALOGS_TYPE_DEFAULT" not in text:
        if old not in text:
            raise RuntimeError("ERROR: getDialogsArray archive filter anchor not found")
        text = text.replace(old, new, 1)
    return text

def patch_notifications(text):
    marker = "    public void processNewMessages(ArrayList<MessageObject> messageObjects, boolean isLast, boolean isFcm, CountDownLatch countDownLatch) {"
    if "public void suppressArchivedNotifications()" in text:
        return text
    method = """    /** Removes already queued notifications from archived chats when the local archive is hidden. */
    public void suppressArchivedNotifications() {
        if (!TelegaSettings.isArchiveHidden()) return;
        notificationsQueue.postRunnable(() -> {
            boolean changed = false;
            java.util.HashSet<String> deleted = new java.util.HashSet<>();
            for (int i = pushMessages.size() - 1; i >= 0; i--) {
                MessageObject message = pushMessages.get(i);
                if (isTelegaArchivedMessage(message)) {
                    pushMessages.remove(i);
                    purgeTelegaArchivedPushMessage(message, deleted);
                    changed = true;
                }
            }
            for (int i = delayedPushMessages.size() - 1; i >= 0; i--) {
                MessageObject message = delayedPushMessages.get(i);
                if (isTelegaArchivedMessage(message)) {
                    delayedPushMessages.remove(i);
                    purgeTelegaArchivedPushMessage(message, deleted);
                    changed = true;
                }
            }
            if (changed) showOrUpdateNotification(false);
        });
    }

    private boolean isTelegaArchivedMessage(MessageObject message) {
        if (message == null) return false;
        TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(message.getDialogId());
        return dialog != null && dialog.folder_id == 1;
    }

    private void purgeTelegaArchivedPushMessage(MessageObject message, java.util.HashSet<String> deleted) {
        if (message == null) return;
        long dialogId = message.getDialogId();
        int messageId = message.getId();
        if (!deleted.add(dialogId + ":" + messageId)) return;
        SparseArray<MessageObject> dialogMessages = pushMessagesDict.get(dialogId);
        if (dialogMessages != null) {
            dialogMessages.remove(messageId);
            if (dialogMessages.size() == 0) pushMessagesDict.remove(dialogId);
        }
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(messageId);
        getMessagesStorage().deletePushMessages(dialogId, ids);
    }

"""
    if marker not in text:
        raise RuntimeError("ERROR: processNewMessages anchor not found")
    text = text.replace(marker, method + marker, 1)
    return text

def patch_settings(text):
    old = """                try {
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, 0);
                } catch (Throwable error) {"""
    new = """                try {
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, 0);
                    if (enabled) {
                        NotificationsController.getInstance(account).suppressArchivedNotifications();
                    }
                } catch (Throwable error) {"""
    if "NotificationsController.getInstance(account).suppressArchivedNotifications();" not in text:
        if old not in text:
            raise RuntimeError("ERROR: setArchiveHidden notification refresh anchor not found")
        # Patch only inside setArchiveHidden, not setAntiBotEnabled.
        start = text.find("public static void setArchiveHidden")
        if start < 0:
            raise RuntimeError("ERROR: setArchiveHidden method not found")
        end = text.find("\n    }", start)
        section = text[start:end]
        if old not in section:
            raise RuntimeError("ERROR: setArchiveHidden refresh block not found")
        text = text[:start] + section.replace(old, new, 1) + text[end:]
    return text

def patch_glass(text):
    replacements = [
        ("new int[]{0x24FFFFFF, 0x12B7DDF2, 0x0C7395C6, 0x18FFFFFF}",
         "new int[]{0x35F7FCFF, 0x24A8D9F4, 0x146A8FB8, 0x28FFFFFF}"),
        ("rim.setColor(0x35FFFFFF);", "rim.setColor(0x55DDF5FF);"),
        ("rim.setColor(0x16FFFFFF);", "rim.setColor(0x24A8D9F4);"),
        ("shine.setColor(0x32FFFFFF);", "shine.setColor(0x50FFFFFF);"),
        ("shine.setColor(0x18FFFFFF);", "shine.setColor(0x24C9F0FF);"),
        ("shine.setColor(0x0AFFFFFF);", "shine.setColor(0x12FFFFFF);"),
        ("surface.setColor(0x08000000);", "surface.setColor(0x100B1826);"),
    ]
    for old, new in replacements:
        text = text.replace(old, new)
    return text

patch_file("ui/Components/ChatActivityEnterView.java", patch_enter_view)
patch_file("ui/ChatActivity.java", patch_chat_activity)
patch_file("ui/DialogsActivity.java", patch_dialogs)
patch_file("messenger/NotificationsController.java", patch_notifications)
patch_file("messenger/TelegaSettings.java", patch_settings)
patch_file("messenger/TelegaGlassDialogs.java", patch_glass)
print("Telega requested fixes applied")
