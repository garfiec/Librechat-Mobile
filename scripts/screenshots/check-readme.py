#!/usr/bin/env python3
"""Check the README's screenshots against the files that hold them.

    scripts/screenshots/check-readme.py

Fails when an <img src> in README.md names a file that doesn't exist, or when a file under
docs/screenshots/ isn't referenced: that directory holds only what the README shows, so an
unreferenced file there is an orphan left behind by a refresh. Store screenshots under
fastlane/ may be store-only (the F-Droid listing shows them all), so those are listed, not
failed.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DOCS = "docs/screenshots"
STORE = ("fastlane/metadata/android/en-US/images/phoneScreenshots",
         "fastlane/metadata/android/en-US/images/tenInchScreenshots")


def main() -> int:
    refs = set(re.findall(r'<img[^>]*\ssrc="([^"]+)"', (REPO / "README.md").read_text()))
    local = {r for r in refs if not re.match(r"https?://", r)}
    problems = [f"README.md names {r}, which doesn't exist" for r in sorted(local)
                if not (REPO / r).is_file()]
    problems += [f"{DOCS}/{f.name} isn't in README.md; delete it or show it"
                 for f in sorted((REPO / DOCS).iterdir())
                 if f.is_file() and f"{DOCS}/{f.name}" not in local]
    for d in STORE:
        for f in sorted((REPO / d).iterdir()):
            if f"{d}/{f.name}" not in local:
                print(f"store-only: {d}/{f.name}")
    for p in problems:
        print(f"FAIL {p}")
    print(f"{len(local)} README images, {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
