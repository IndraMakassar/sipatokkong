#!/usr/bin/env python3
"""SessionStart hook: inject project_status.md into the session context.

Stdlib only on purpose — this repo's machines have neither `jq` nor PowerShell 7
available to hooks, so the usual one-liner recipes do not work here.
"""
import json
import os
import sys

STATUS_FILE = "project_status.md"
MAX_CHARS = 20000  # bound the injection so a long change log can't flood context


def main() -> int:
    if not os.path.isfile(STATUS_FILE):
        return 0

    try:
        with open(STATUS_FILE, encoding="utf-8") as handle:
            content = handle.read()
    except OSError:
        return 0

    if not content.strip():
        return 0

    if len(content) > MAX_CHARS:
        content = content[:MAX_CHARS] + f"\n\n[... truncated, read {STATUS_FILE} directly ...]"

    payload = {
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": (
                f"Current {STATUS_FILE} (the project's living status document). "
                "Read this before starting work, and update it after changing code:\n\n"
                + content
            ),
        }
    }
    json.dump(payload, sys.stdout)
    return 0


if __name__ == "__main__":
    sys.exit(main())
