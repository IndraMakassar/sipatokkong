#!/usr/bin/env python3
"""Stop hook: refuse to end the turn while project_status.md is stale.

"Stale" means a watched source file has a newer mtime than project_status.md.
The check is self-clearing: once the status file is rewritten it becomes the
newest file, so the condition simply stops holding — no sentinel to manage.
"""
import json
import os
import sys

STATUS_FILE = "project_status.md"
WATCHED_DIRS = ("app/src", "supabase", "gradle")
WATCHED_ROOT_FILES = ("build.gradle.kts", "settings.gradle.kts")
WATCHED_EXTS = (".kt", ".kts", ".sql", ".xml", ".toml")
SKIP_DIRS = {"build", ".gradle", ".kotlin", ".idea"}


def newest_source():
    """Return (path, mtime) of the most recently modified watched file, or None."""
    newest = None

    for base in WATCHED_DIRS:
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for name in filenames:
                if not name.endswith(WATCHED_EXTS):
                    continue
                path = os.path.join(dirpath, name)
                try:
                    mtime = os.path.getmtime(path)
                except OSError:
                    continue
                if newest is None or mtime > newest[1]:
                    newest = (path, mtime)

    for name in WATCHED_ROOT_FILES:
        if not os.path.isfile(name):
            continue
        try:
            mtime = os.path.getmtime(name)
        except OSError:
            continue
        if newest is None or mtime > newest[1]:
            newest = (name, mtime)

    return newest


def read_payload():
    if sys.stdin.isatty():
        return {}
    raw = sys.stdin.read()
    if not raw.strip():
        return {}
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def main() -> int:
    payload = read_payload()

    # Anti-loop guard: block at most once per stop chain. Without this, a turn
    # that never updates the status file would be blocked forever.
    if payload.get("stop_hook_active"):
        return 0

    newest = newest_source()
    if newest is None:
        return 0

    status_mtime = os.path.getmtime(STATUS_FILE) if os.path.isfile(STATUS_FILE) else 0.0
    if newest[1] <= status_mtime:
        return 0

    out = {
        "decision": "block",
        "reason": (
            f"`{newest[0]}` is newer than `{STATUS_FILE}`, so the status document "
            "does not yet reflect the latest change.\n\n"
            f"Update `{STATUS_FILE}` before ending the turn:\n"
            "1. Add a new entry at the top of the **Change Log**.\n"
            "2. Refresh the **Status Summary** sections if the change moves an item "
            "between Built / In Progress / Not Built.\n"
            "3. State honestly what is **verified** (build ran, tests passed, tried on a "
            "device) versus what is **not yet verified**."
        ),
    }
    json.dump(out, sys.stdout)
    return 0


if __name__ == "__main__":
    sys.exit(main())
