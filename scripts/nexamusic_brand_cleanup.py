#!/usr/bin/env python3
"""Rename app-owned legacy identifiers and paths to NexaMusic.

Room schema history is moved and its package metadata is updated together;
schema versions and entity/table definitions are not modified.
LICENSE and NOTICE are preserved for required attribution. The old party
invite URI is retained as a backward-compatibility alias and audited exactly.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path.cwd()
SKIP_CONTENT = {"LICENSE", "NOTICE"}
MANIFEST = "app/src/main/AndroidManifest.xml"
LEGACY_URI = 'android:scheme="convx"'
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

# Rewrite UTF-8 text contents. Keep the manifest's old invite URI as an alias.
changed_contents = 0
for relative in tracked:
    path = ROOT / relative
    if not path.is_file() or path.name in SKIP_CONTENT or relative == "scripts/nexamusic_brand_cleanup.py" or relative == MANIFEST:
        continue
    try:
        original = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    updated = PATTERN.sub(brand, original)
    if updated != original:
        path.write_text(updated, encoding="utf-8", newline="")
        changed_contents += 1

# Rename files and directories bottom-up.
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

# Audit every text file and path. Only legal attribution and the exact legacy
# invite URI are exempt; the scanner itself contains the search pattern.
remaining = []
legacy_uri_count = 0
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts:
        continue
    relative = path.relative_to(ROOT).as_posix()
    if PATTERN.search(relative):
        remaining.append(f"path: {relative}")
    if path.name in SKIP_CONTENT or relative == "scripts/nexamusic_brand_cleanup.py":
        continue
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if relative == MANIFEST:
        legacy_uri_count = content.count(LEGACY_URI)
        if legacy_uri_count != 1:
            remaining.append(f"{relative}: expected exactly one legacy invite URI alias, found {legacy_uri_count}")
        content = content.replace(LEGACY_URI, "")
    if PATTERN.search(content):
        remaining.append(f"content: {relative}")

print(f"Updated text files: {changed_contents}")
print(f"Remaining unexpected legacy-brand references: {len(remaining)}")
print(f"Preserved legacy invite URI aliases: {legacy_uri_count}")
for item in remaining[:100]:
    print(item)
if remaining:
    raise SystemExit("NexaMusic branding audit incomplete; review remaining references.")
if legacy_uri_count != 1:
    raise SystemExit("Backward-compatibility URI audit failed.")
