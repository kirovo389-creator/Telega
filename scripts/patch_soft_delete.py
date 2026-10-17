#!/usr/bin/env python3
"""Apply soft-delete UI patches to ChatMessageCell and MessagesController."""
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
        "        if (TelegaDeletedMessages.isDeleted(messageObject.currentAccount, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner == null || messageObject.messageOwner.peer_id == null || messageObject.messageOwner.peer_id.channel_id == 0)) {\n"
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
        "                && TelegaDeletedMessages.isDeleted(\n"
        "                        currentMessageObject.currentAccount,\n"
        "                        currentMessageObject.getDialogId(),\n"
        "                        currentMessageObject.getId(),\n"
        "                        currentMessageObject.messageOwner == null\n"
        "                                || currentMessageObject.messageOwner.peer_id == null\n"
        "                                || currentMessageObject.messageOwner.peer_id.channel_id == 0)) {\n"
        "            // Force red color for deleted label\n"
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


if __name__ == "__main__":
    patch_chat_message_cell()
    patch_messages_controller()
    print("Soft-delete patches done")
