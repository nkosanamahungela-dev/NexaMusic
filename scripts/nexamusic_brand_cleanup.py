#!/usr/bin/env python3
"""Rename app-owned Convx identifiers and paths to NexaMusic.

The Room schema history is moved and its package metadata is updated together;
schema versions and entity/table definitions are not modified.
LICENSE and NOTICE contents are deliberately preserved for attribution.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path.cwd()
SKIP_CONTENT = {"LICENSE", "NOTICE"}
PATTERN = re.compile(r"convx", re.IGNORECASE)

def brand(match: re.Match) -> str:
    value = match.group(0)
    if value.isupper():
        return "NEXAMUSIC"
    if value.islower():
        return "nexamusic"
    return "NexaMusic"

def renamed_path(path: str) -> str:
    return PATTERN.sub(brand, path)

tracked = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
tracked = [p for p in tracked if p]

# Rewrite UTF-8 text contents. Binary assets are left byte-for-byte unchanged.
changed_contents = 0
for relative in tracked:
    path = ROOT / relative
    if not path.is_file() or path.name in SKIP_CONTENT or relative == "scripts/nexamusic_brand_cleanup.py":
        continue
    try:
        original = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    updated = PATTERN.sub(brand, original)
    if updated != original:
        path.write_text(updated, encoding="utf-8", newline="")
        changed_contents += 1

# Rename files and directories bottom-up so nested package trees move safely.
for relative in sorted(tracked, key=lambda p: (p.count("/"), len(p)), reverse=True):
    old = ROOT / relative
    if not old.exists():
        continue
    new_relative = renamed_path(relative)
    if new_relative == relative:
        continue
    new = ROOT / new_relative
    new.parent.mkdir(parents=True, exist_ok=True)
    if new.exists():
        raise RuntimeError(f"Refusing to overwrite existing path: {new_relative}")
    old.rename(new)

# Report remaining tracked text references, excluding preserved legal attribution.
remaining = []
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts or path.name in SKIP_CONTENT or path.relative_to(ROOT).as_posix() == "scripts/nexamusic_brand_cleanup.py":
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if PATTERN.search(text) or PATTERN.search(str(path.relative_to(ROOT))):
        remaining.append(str(path.relative_to(ROOT)))

print(f"Updated text files: {changed_contents}")
print(f"Remaining non-legal Convx references: {len(remaining)}")
for item in remaining[:100]:
    print(item)
if remaining:
    raise SystemExit("Brand cleanup incomplete; review remaining references.")
