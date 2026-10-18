#!/usr/bin/env python3
"""Apply Telega soft-delete + settings + edit-history UI patches."""
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
        "            currentTimeString = org.telegram.messenger.TelegaDeletedLabel.label();\n"
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
        print("WARNING: timeString block not found")

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
    new_icon = ""
    if old_icon in text:
        text = text.replace(old_icon, new_icon, 1)
        print("Removed duplicate overlay; red trash label is rendered in the time layout")
    else:
        print("WARNING: icon block not found")

    if "telegaEditHistoryHelper" not in text:
        anchor_field = "    public int totalHeight;"
        if anchor_field in text:
            text = text.replace(
                anchor_field,
                anchor_field + "\n    private org.telegram.ui.Components.TelegaEditHistoryHelper telegaEditHistoryHelper;\n    private int telegaEditHistoryTop;",
                1,
            )
            print("Added telegaEditHistoryHelper field")

    measure_anchor = "                    totalHeight = messageObject.textHeight() + dp(19.5f) + namesOffset;"
    measure_insert = (
        measure_anchor + "\n"
        "                    if (telegaEditHistoryHelper == null) {\n"
        "                        telegaEditHistoryHelper = new org.telegram.ui.Components.TelegaEditHistoryHelper(this);\n"
        "                    }\n"
        "                    telegaEditHistoryHelper.bind(messageObject, Math.max(dp(100), getMaxNameWidth()));\n"
        "                    if (telegaEditHistoryHelper.hasContent()) {\n"
        "                        totalHeight += telegaEditHistoryHelper.getHeight() + dp(6);\n"
        "                    }"
    )
    if measure_anchor in text and "telegaEditHistoryHelper.bind" not in text:
        text = text.replace(measure_anchor, measure_insert, 1)
        print("Patched measure for edit history")
    else:
        print("measure edit history already patched or anchor missing")

    draw_anchor = "        drawContent(canvas, false);"
    draw_insert = (
        draw_anchor + "\n"
        "        if (telegaEditHistoryHelper != null && telegaEditHistoryHelper.hasContent()) {\n"
        "            int bgLeft = currentBackgroundDrawable != null ? currentBackgroundDrawable.getBounds().left + dp(10) : dp(10);\n"
        "            int bgTop = currentBackgroundDrawable != null ? currentBackgroundDrawable.getBounds().top + dp(8) + namesOffset : dp(8);\n"
        "            telegaEditHistoryTop = bgTop;\n"
        "            telegaEditHistoryHelper.draw(canvas, bgLeft, bgTop);\n"
        "        }\n"
        "        if (org.telegram.messenger.TelegaSettings.isGarlandEnabled() && currentBackgroundDrawable != null) {\n"
        "            android.graphics.Rect telegaGarlandBounds = currentBackgroundDrawable.getBounds();\n"
        "            org.telegram.messenger.TelegaGarland.draw(canvas, this, telegaGarlandBounds.left, telegaGarlandBounds.top, telegaGarlandBounds.right);\n"
        }"
    )
    if draw_anchor in text and "telegaEditHistoryHelper.draw" not in text:
        text = text.replace(draw_anchor, draw_insert, 1)
        print("Patched draw for edit history")
    else:
        print("draw edit history already patched or anchor missing")

    touch_anchor = "        if (checkTextSelection(event)) {\n            return true;\n        }"
    touch_insert = (
        "        if (telegaEditHistoryHelper != null && telegaEditHistoryHelper.hasContent()) {\n"
        "            if (telegaEditHistoryHelper.onTouchEvent(event)) {\n"
        "                return true;\n"
        "            }\n"
        "        }\n"
        + touch_anchor
    )
    if touch_anchor in text and "telegaEditHistoryHelper.onTouchEvent" not in text:
        text = text.replace(touch_anchor, touch_insert, 1)
        print("Patched touch for edit history")
    else:
        print("touch edit history already patched or anchor missing")

    path.write_text(text, encoding="utf-8")
    print(f"ChatMessageCell size after patch: {path.stat().st_size}")


def patch_chat_activity():
    path = Path("TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")
    if not path.exists():
        raise RuntimeError("ERROR: ChatActivity.java not found; chat effects cannot be attached")
    text = path.read_text(encoding="utf-8")
    marker = "org.telegram.messenger.TelegaChatEffects.attach(fragmentView);"
    if marker in text:
        print("Chat effects already attached")
        return

    method_start = text.find("public View createView(Context context)")
    if method_start < 0:
        raise RuntimeError("ERROR: ChatActivity.createView(Context) anchor not found")
    return_at = text.find("return fragmentView;", method_start)
    if return_at < 0:
        raise RuntimeError("ERROR: ChatActivity.createView return anchor not found")
    line_start = text.rfind("\n", 0, return_at) + 1
    indent = text[line_start:return_at]
    text = text[:line_start] + indent + marker + "\n" + text[line_start:]
    path.write_text(text, encoding="utf-8")
    print("Attached chat-wide snowfall layer")


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
        "                        MessageObject _telegaOld = dialogMessagesByIds.get(message.id);\n"
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

    # Remove old Telega row if present mid-list, always insert FIRST
    import re
    text = re.sub(
        r'\s*items\.add\(SettingCell\.Factory\.of\(90,[^\n]+\n',
        '\n',
        text,
        count=1,
    )

    # Insert BEFORE Account (id 1) — first in list
    account_anchor = (
        '        items.add(SettingCell.Factory.of(1, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.settings_account, getString(R.string.SettingsAccount), getString(R.string.SettingsAccountInfo)));\n'
    )
    telega_row = (
        '        items.add(SettingCell.Factory.of(90, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.settings_chat, "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 Telega", "\u0421\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u044f, \u043f\u0440\u043e\u0444\u0438\u043b\u044c, \u043a\u0430\u0441\u0442\u043e\u043c\u0438\u0437\u0430\u0446\u0438\u044f"));\n'
        '        items.add(UItem.asShadow(null));\n'
    )
    if account_anchor in text:
        text = text.replace(account_anchor, telega_row + account_anchor, 1)
        print("Added Telega settings row FIRST")
    else:
        print("WARNING: account row anchor not found")

    if "case 90" not in text:
        case_anchor = "            case 24: {\n                presentFragment(new RoundVideoSettingsActivity());\n                break;\n            }"
        case_new = (
            case_anchor + "\n"
            + "            case 90: {\n"
            + "                presentFragment(new TelegaSettingsActivity());\n"
            + "                break;\n"
            + "            }"
        )
        if case_anchor in text:
            text = text.replace(case_anchor, case_new, 1)
            print("Added Telega settings click handler")
        else:
            print("WARNING: case 24 anchor not found")
    else:
        print("case 90 already present")

    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_chat_message_cell()
    patch_chat_activity()
    patch_messages_controller()
    patch_settings_activity()
    print("All Telega patches done")
