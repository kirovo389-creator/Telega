#!/usr/bin/env python3
"""Apply Telega soft-delete + settings + edit-history patches."""
from pathlib import Path

def patch_chat_message_cell():
    path = Path("TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java")
    text = path.read_text(encoding="utf-8")

    old_time = (
        "        if (TelegaDeletedMessages.isDeleted(messageObject.currentAccount, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner == null || messageObject.messageOwner.peer_id == null || messageObject.messageOwner.peer_id.channel_id == 0)) {\n"
        "            currentTimeString = TextUtils.concat(\"    \", currentTimeString);\n"
        "        }"
    )

    new_time = (
        "        if (org.telegram.messenger.TelegaSettings.isShowDeletedMessages() && TelegaDeletedMessages.isDeleted(messageObject.currentAccount, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner == null || messageObject.messageOwner.peer_id == null || messageObject.messageOwner.peer_id.channel_id == 0)) {\n"
        "            // Soft-deleted: red label instead of time + mild transparency\n"
        "            currentTimeString = \"    \u0423\u0434\u0430\u043b\u0435\u043d\u043e\";\n"
        "            if (Math.abs(getAlpha() - 0.85f) > 0.01f) {\n"
        "                setAlpha(0.85f);\n"
        "            }\n"
        "        } else if (Math.abs(getAlpha() - 0.85f) < 0.01f) {\n"
        "            setAlpha(1f);\n"
        "        }"
    )

    if old_time in text:
        text = text.replace(old_time, new_time, 1)
        print("Patched timeString + alpha 0.85")
    else:
        print("WARNING: timeString block not found (may already be patched)")

    old_icon = (
        "        if (currentMessageObject != null\n"
        "                && TelegaDeletedMessages.isDeleted(\n"
        "                        currentMessageObject.currentAccount,\n"
        "                        currentMessageObject.getDialogId(),\n"
        "                        currentMessageObject.getId(),\n"
        "                        currentMessageObject.messageOwner == null\n"
        "                                || currentMessageObject.messageOwner.peer_id == null\n"
        "                                || currentMessageObject.messageOwner.peer_id.channel_id == 0)) {\n"
        "            android.graphics.drawable.Drawable deletedIcon = getResources().getDrawable(R.drawable.msg_delete).mutate();\n"
        "            deletedIcon.setColorFilter(android.graphics.Color.rgb(226, 58, 65), android.graphics.PorterDuff.Mode.SRC_IN);\n"
        "            int iconSize = dp(11);\n"
        "            int iconLeft = (int) (drawTimeX + dp(1));\n"
        "            int iconTop = (int) (drawTimeY + Math.max(0, (timeLayout.getHeight() - iconSize) / 2f));\n"
        "            deletedIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);\n"
        "            deletedIcon.draw(canvas);\n"
        "            deletedIcon.clearColorFilter();\n"
        "        }"
    )

    new_icon = (
        "        if (currentMessageObject != null\n"
        "                && org.telegram.messenger.TelegaSettings.isShowDeletedMessages()\n"
        "                && TelegaDeletedMessages.isDeleted(\n"
        "                        currentMessageObject.currentAccount,\n"
        "                        currentMessageObject.getDialogId(),\n"
        "                        currentMessageObject.getId(),\n"
        "                        currentMessageObject.messageOwner == null\n"
        "                                || currentMessageObject.messageOwner.peer_id == null\n"
        "                                || currentMessageObject.messageOwner.peer_id.channel_id == 0)) {\n"
        "            int oldColor = Theme.chat_timePaint.getColor();\n"
        "            Theme.chat_timePaint.setColor(android.graphics.Color.rgb(226, 58, 65));\n"
        "            if (timeLayout != null) {\n"
        "                canvas.save();\n"
        "                canvas.translate(drawTimeX, drawTimeY);\n"
        "                SpoilerEffect.layoutDrawMaybe(timeLayout, canvas);\n"
        "                canvas.restore();\n"
        "            }\n"
        "            Theme.chat_timePaint.setColor(oldColor);\n"
        "\n"
        "            android.graphics.drawable.Drawable deletedIcon = getResources().getDrawable(R.drawable.msg_delete).mutate();\n"
        "            deletedIcon.setColorFilter(android.graphics.Color.rgb(226, 58, 65), android.graphics.PorterDuff.Mode.SRC_IN);\n"
        "            int iconSize = dp(11);\n"
        "            int iconLeft = (int) (drawTimeX + dp(1));\n"
        "            int layoutH = timeLayout != null ? timeLayout.getHeight() : dp(12);\n"
        "            int iconTop = (int) (drawTimeY + Math.max(0, (layoutH - iconSize) / 2f));\n"
        "            deletedIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);\n"
        "            deletedIcon.draw(canvas);\n"
        "            deletedIcon.clearColorFilter();\n"
        "        }"
    )

    if old_icon in text:
        text = text.replace(old_icon, new_icon, 1)
        print("Patched red icon + label redraw")
    else:
        print("WARNING: icon block not found (may already be patched)")

    path.write_text(text, encoding="utf-8")
    print(f"ChatMessageCell size after patch: {path.stat().st_size}")


def patch_messages_controller():
    path = Path("TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java")
    if not path.exists():
        print("MessagesController not found, skip")
        return
    text = path.read_text(encoding="utf-8")
    changed = False

    old_edit = "                    getSendMessagesHelper().onMessageEdited(message);"
    new_edit = (
        "                    // Telega: store previous version before edit is applied\n"
        "                    try {\n"
        "                        long _telegaDid = MessageObject.getDialogId(message);\n"
        "                        MessageObject _telegaOld = dialogMessage.get(_telegaDid);\n"
        "                        if (_telegaOld == null || _telegaOld.getId() != message.id) {\n"
        "                            java.util.concurrent.ConcurrentHashMap<Integer, MessageObject> _map = dialogMessagesByIds.get(_telegaDid);\n"
        "                            if (_map != null) {\n"
        "                                _telegaOld = _map.get(message.id);\n"
        "                            }\n"
        "                        }\n"
        "                        if (_telegaOld != null && _telegaOld.messageOwner != null && !android.text.TextUtils.isEmpty(_telegaOld.messageOwner.message)) {\n"
        "                            if (message.message == null || !message.message.equals(_telegaOld.messageOwner.message)) {\n"
        "                                org.telegram.messenger.TelegaEditedMessages.addPreviousVersion(currentAccount, _telegaDid, message.id, _telegaOld.messageOwner.message);\n"
        "                            }\n"
        "                        }\n"
        "                    } catch (Throwable ignore) {}\n"
        "                    getSendMessagesHelper().onMessageEdited(message);"
    )
    if old_edit in text and "Telega: store previous version" not in text:
        text = text.replace(old_edit, new_edit, 1)
        changed = True
        print("Patched edit-history capture")
    else:
        print("edit-history capture already present or anchor missing")

    old1 = (
        "                    } else {\n"
        "                        // The private-chat deletion update does not carry a dialog id.\n"
        "                        // Store a global tombstone so the message is still marked after reload.\n"
        "                        TelegaDeletedMessages.markDeleted(currentAccount, 0, messageId);\n"
        "                        interfaceUpdateMask |= UPDATE_MASK_MESSAGE_TEXT;\n"
        "                    }"
    )
    new1 = (
        "                    } else {\n"
        "                        // The private-chat deletion update does not carry a dialog id.\n"
        "                        // Store a global tombstone so the message is still marked after reload.\n"
        "                        TelegaDeletedMessages.markDeleted(currentAccount, 0, messageId);\n"
        "                        if (messageObject != null) {\n"
        "                            messageObject.deleted = true;\n"
        "                        }\n"
        "                        interfaceUpdateMask |= UPDATE_MASK_MESSAGE_TEXT;\n"
        "                    }"
    )
    if old1 in text:
        text = text.replace(old1, new1, 1)
        changed = True
        print("Patched private soft-delete")

    old2 = (
        "                    } else {\n"
        "                        // Channel message IDs are only unique within that channel.\n"
        "                        // Keep the tombstone scoped to this dialog to avoid ID collisions.\n"
        "                        TelegaDeletedMessages.markDeleted(currentAccount, dialogId, messageId);\n"
        "                        interfaceUpdateMask |= UPDATE_MASK_MESSAGE_TEXT;\n"
        "                    }"
    )
    new2 = (
        "                    } else {\n"
        "                        // Channel message IDs are only unique within that channel.\n"
        "                        // Keep the tombstone scoped to this dialog to avoid ID collisions.\n"
        "                        TelegaDeletedMessages.markDeleted(currentAccount, dialogId, messageId);\n"
        "                        if (messageObject != null) {\n"
        "                            messageObject.deleted = true;\n"
        "                        }\n"
        "                        interfaceUpdateMask |= UPDATE_MASK_MESSAGE_TEXT;\n"
        "                    }"
    )
    if old2 in text:
        text = text.replace(old2, new2, 1)
        changed = True
        print("Patched channel soft-delete")

    if changed:
        path.write_text(text, encoding="utf-8")
        print("MessagesController updated")
    else:
        print("MessagesController already patched or blocks not found")


def patch_settings_activity():
    path = Path("TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java")
    if not path.exists():
        print("SettingsActivity not found")
        return
    text = path.read_text(encoding="utf-8")

    if "TelegaSettingsActivity" in text:
        print("SettingsActivity already has Telega entry")
        return

    anchor = (
        '        items.add(SettingCell.Factory.of(10, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.settings_language, getString(R.string.SettingsLanguage), LocaleController.getCurrentLanguageName()));\n'
    )
    insert = (
        anchor
        + '        items.add(UItem.asShadow(null));\n'
        + '        items.add(SettingCell.Factory.of(90, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.settings_features, "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 Telega", "\u0423\u0434\u0430\u043b\u0451\u043d\u043d\u044b\u0435 \u0438 \u0438\u0437\u043c\u0435\u043d\u0451\u043d\u043d\u044b\u0435 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u044f"));\n'
    )
    if anchor in text:
        text = text.replace(anchor, insert, 1)
        print("Added Telega settings row")
    else:
        print("WARNING: language row anchor not found")

    case_anchor = "            case 24: {\n                presentFragment(new RoundVideoSettingsActivity());\n                break;\n            }"
    case_new = (
        case_anchor + "\n"
        + "            case 90: {\n"
        + "                presentFragment(new TelegaSettingsActivity());\n"
        + "                break;\n"
        + "            }"
    )
    if case_anchor in text and "case 90" not in text:
        text = text.replace(case_anchor, case_new, 1)
        print("Added Telega settings click handler")
    else:
        print("WARNING: case 24 anchor not found or case 90 exists")

    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_chat_message_cell()
    patch_messages_controller()
    patch_settings_activity()
    print("All Telega patches done")
