#!/usr/bin/env python3
"""Render the edit history as part of the message text.

Wraps MessageObject.generateLayout(TLRPC.User): the layout is built from
"previous content + new content" text, then messageText is restored.
Fails loudly (exit 1) if the anchor is not found.
"""
import re
import sys
from pathlib import Path

PATH = Path("TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java")


def main():
    text = PATH.read_text(encoding="utf-8")

    if "generateLayoutTelegaOriginal" in text:
        print("edit text hook already present")
        return 0

    pattern = re.compile(
        r"(?:(?:public|private|protected)\s+)?void\s+generateLayout\(\s*(?:final\s+)?TLRPC\.User\s+(\w+)\s*\)\s*\{"
    )
    matches = list(pattern.finditer(text))
    if not matches:
        print("ERROR: generateLayout(TLRPC.User) not found. Candidate lines:")
        shown = 0
        for line in text.splitlines():
            if "generateLayout" in line and shown < 40:
                print("   ", line.strip()[:220])
                shown += 1
        return 1

    m = matches[0]
    var = m.group(1)
    wrapper = (
        "public void generateLayout(TLRPC.User " + var + ") {\n"
        "        // Telega: previous / new content of edited messages\n"
        "        CharSequence telegaSaved = messageText;\n"
        "        CharSequence telegaWrapped = org.telegram.messenger.TelegaEditedText.wrap(this, telegaSaved);\n"
        "        int telegaShift = (telegaWrapped != null && telegaSaved != null) ? telegaWrapped.length() - telegaSaved.length() : 0;\n"
        "        if (telegaShift > 0) {\n"
        "            messageText = telegaWrapped;\n"
        "            org.telegram.messenger.TelegaEditedText.shiftEntities(messageOwner, telegaShift);\n"
        "        }\n"
        "        try {\n"
        "            generateLayoutTelegaOriginal(" + var + ");\n"
        "        } finally {\n"
        "            if (telegaShift > 0) {\n"
        "                org.telegram.messenger.TelegaEditedText.shiftEntities(messageOwner, -telegaShift);\n"
        "                if (messageText == telegaWrapped) {\n"
        "                    messageText = telegaSaved;\n"
        "                }\n"
        "            }\n"
        "        }\n"
        "    }\n"
        "\n"
        "    private void generateLayoutTelegaOriginal(TLRPC.User " + var + ") {"
    )
    text = text[: m.start()] + wrapper + text[m.end():]
    PATH.write_text(text, encoding="utf-8")
    print(f"Patched generateLayout(TLRPC.User {var}) ({len(matches)} match(es))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
