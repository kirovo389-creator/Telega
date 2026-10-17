#!/usr/bin/env python3
"""Hook the Telega AI agent (.ai command) into SendMessagesHelper.sendMessage(SendMessageParams).

Fails loudly (exit 1) if the anchor is not found, so a build never silently ships without the agent.
"""
import re
import sys
from pathlib import Path

PATH = Path("TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java")


def main():
    text = PATH.read_text(encoding="utf-8")

    if "TelegaAIAgent.intercept" in text:
        print("AI hook already present")
        return 0

    pattern = re.compile(
        r"public\s+(?:synchronized\s+)?void\s+sendMessage\(\s*(?:final\s+)?(?:SendMessagesHelper\.)?SendMessageParams\s+(\w+)\s*\)\s*\{"
    )
    matches = list(pattern.finditer(text))
    if not matches:
        print("ERROR: sendMessage(SendMessageParams) not found. Candidate lines:")
        shown = 0
        for line in text.splitlines():
            if "sendMessage(" in line and "SendMessageParams" in line and shown < 60:
                print("   ", line.strip()[:220])
                shown += 1
        return 1

    m = matches[0]
    var = m.group(1)
    hook = (
        "\n        // Telega: .ai command (AI agent)\n"
        "        if (org.telegram.messenger.TelegaAIAgent.intercept(currentAccount, " + var + ")) {\n"
        "            return;\n"
        "        }\n"
    )
    text = text[: m.end()] + hook + text[m.end():]
    PATH.write_text(text, encoding="utf-8")
    print(f"Patched sendMessage(SendMessageParams {var}) with AI hook ({len(matches)} match(es))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
