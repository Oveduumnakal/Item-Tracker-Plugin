#!/usr/bin/env python3
"""Regenerate the bundled item examine dataset.

Downloads the community OSRS item dump, extracts an ``itemId -> examine`` map,
strips leftover wiki markup, and writes the gzipped JSON the plugin loads at
runtime (``src/main/resources/com/oveduumnakal/examines.json.gz``).

Examine text effectively never changes for existing items, so this only needs
re-running occasionally to pick up items added to the game since the last dump.

Usage:
    python3 tools/generate-examines.py
"""

import gzip
import json
import os
import re
import sys
import urllib.request

SOURCE_URL = "https://raw.githubusercontent.com/0xNeffarion/osrsreboxed-db/master/docs/items-complete.json"
OUTPUT = os.path.join(
    os.path.dirname(__file__),
    "..",
    "src",
    "main",
    "resources",
    "com",
    "oveduumnakal",
    "examines.json.gz",
)

# Wiki markup that occasionally leaks into the dump's examine field.
COMMENT = re.compile(r"<!--.*?-->", re.S)
TEMPLATE = re.compile(r"\{\{.*?\}\}", re.S)
WHITESPACE = re.compile(r"\s+")


def clean(text):
    text = COMMENT.sub("", text)
    text = TEMPLATE.sub("", text)
    return WHITESPACE.sub(" ", text).strip()


def main():
    print(f"Downloading {SOURCE_URL} ...")
    with urllib.request.urlopen(SOURCE_URL, timeout=300) as resp:
        items = json.load(resp)
    print(f"Loaded {len(items)} item entries.")

    examines = {}
    for entry in items.values():
        examine = entry.get("examine")
        item_id = entry.get("id")
        if examine is None or item_id is None:
            continue
        cleaned = clean(examine)
        if cleaned:
            examines[str(item_id)] = cleaned

    print(f"Extracted {len(examines)} examines.")

    # Compact, sorted keys so regenerated files diff cleanly.
    raw = json.dumps(
        examines, separators=(",", ":"), ensure_ascii=False, sort_keys=True
    ).encode("utf-8")

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with gzip.open(OUTPUT, "wb", compresslevel=9) as f:
        f.write(raw)

    size_mb = os.path.getsize(OUTPUT) / 1024 / 1024
    print(f"Wrote {OUTPUT} ({size_mb:.2f} MB gzipped).")


if __name__ == "__main__":
    sys.exit(main())
