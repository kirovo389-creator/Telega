#!/usr/bin/env python3
"""Capture previous text when the user edits their own message."""
from pathlib import Path

p = Path("TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java")
if not p.exists():
    print("SendMessagesHelper missing")
    raise SystemExit(0)

t = p.read_text(encoding="utf-8")
if "TelegaEditedMessages.addPreviousVersion" in t:
    print("already patched")
    raise SystemExit(0)

anchor = (
    "    public int editMessage(MessageObject messageObject, String message, boolean searchLinks, "
    "final BaseFragment fragment, ArrayList<TLRPC.MessageEntity> entities, int scheduleDate, int scheduleRepeatPeriod) {"
)
block = (
    anchor
    + "\n"
    + "        // Telega: remember previous text before local edit is sent\n"
    + "        try {\n"
    + "            if (messageObject != null && messageObject.messageOwner != null\n"
    + "                    && messageObject.messageOwner.message != null && message != null\n"
    + "                    && !messageObject.messageOwner.message.equals(message)) {\n"
    + "                org.telegram.messenger.TelegaEditedMessages.addPreviousVersion(\n"
    + "                        currentAccount, messageObject.getDialogId(), messageObject.getId(),\n"
    + "                        messageObject.messageOwner.message);\n"
    + "            }\n"
    + "        } catch (Throwable ignore) {}\n"
)
if anchor in t:
    t = t.replace(anchor, block, 1)
    p.write_text(t, encoding="utf-8")
    print("Patched SendMessagesHelper for own edits")
else:
    print("WARNING: editMessage(String) anchor not found")
