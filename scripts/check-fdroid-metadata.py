#!/usr/bin/env python3
"""Validate the fastlane store listing against the rules fdroidserver actually applies.

Everything under fastlane/metadata/android/en-US/ is read by F-Droid **from the tagged
commit**, so a mistake here is frozen at release time and cannot be repaired without
re-tagging -- which would change the APK and break the reproducible-build match that
`Binaries:` + `AllowedAPKSigningKeys` depend on. That is why these checks run in CI, on the
commit that introduces the mistake, rather than only at dispatch.

Nothing else catches any of this:

  * fdroidserver truncates over-limit text with `text[:limit]` and logs nothing
    (update.py `_set_localized_text_entry`). An 81-character summary simply loses its last
    character in the published index.
  * `fdroid lint` checks the *recipe's* `Summary:`/`Description:` keys, which we do not set
    because we use fastlane instead -- so it has nothing to look at.
  * A changelog whose filename is not all digits is dropped inside a bare
    `except ValueError: pass`. `2026100.txt` is not an error, it is silence.
  * A graphic in a directory fdroidserver does not recognise is never copied, and a
    transparent icon gets composited over F-Droid's own theme colour, so a dark mark
    disappears on a dark theme.

The limits are fdroidserver's defaults (common.py `char_limits`) and are counted over the
raw file contents **including the trailing newline**, because that is what `text[:limit]`
sees. Python `len()` is used rather than `wc -m` so the count matches fdroidserver's own.

    scripts/check-fdroid-metadata.py                       check the listing as it stands
    scripts/check-fdroid-metadata.py --release --version-code 20261001
                                                           additionally require a usable
                                                           changelog for that versionCode
"""

import argparse
import os
import re
import struct
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCALE_DIR = os.path.join(REPO, "fastlane", "metadata", "android", "en-US")
CHANGELOGS = os.path.join(LOCALE_DIR, "changelogs")
IMAGES = os.path.join(LOCALE_DIR, "images")
VERSION_PROPS = os.path.join(REPO, "version.properties")

# fdroidserver common.py char_limits. 'name' covers title.txt/name.txt.
LIMITS = {"name": 50, "summary": 80, "description": 4000, "video": 256, "whatsNew": 500}

# update.py: the file -> field mapping. Either spelling of each is honoured.
TEXT_FILES = {
    "short_description.txt": "summary",
    "summary.txt": "summary",
    "full_description.txt": "description",
    "description.txt": "description",
    "title.txt": "name",
    "name.txt": "name",
    "video.txt": "video",
}

# fdroidserver only strips *leading and trailing* newlines for these keys and the UI renders
# them on one line, so an interior newline silently mangles the field.
SINGLE_LINE = ("name", "summary", "video")

# update.py GRAPHIC_NAMES / SCREENSHOT_DIRS / ALLOWED_EXTENSIONS. Anything else under
# images/ is not copied into the repo and never appears in the listing.
GRAPHIC_NAMES = ("featureGraphic", "icon", "promoGraphic", "tvBanner")
SCREENSHOT_DIRS = (
    "phoneScreenshots",
    "sevenInchScreenshots",
    "tenInchScreenshots",
    "tvScreenshots",
    "wearScreenshots",
)
ALLOWED_EXTENSIONS = ("png", "jpg", "jpeg")

# Must stay a prefix of MARKER in scripts/draft-changelog.sh. fdroidserver strips nothing,
# so a draft left in place ships verbatim.
DRAFT_MARKER = "# DRAFT"

# The version sits mid-sentence, so match the token and drop the sentence's full stop. The
# `0.8.7+dev.9f8e7d6c` partial-sync form contains dots of its own, which is why this cannot
# simply stop at the first one.
COMPAT_RE = re.compile(r"Built against LibreChat v([0-9][0-9A-Za-z.+-]*)")

RED, GREEN, YELLOW, DIM, BOLD, OFF = (
    "\033[31m", "\033[32m", "\033[33m", "\033[2m", "\033[1m", "\033[0m",
) if sys.stdout.isatty() else ("", "", "", "", "", "")


def rel(path):
    return os.path.relpath(path, REPO)


def read_text(path):
    """Read as fdroidserver does: UTF-8, replacing anything undecodable."""
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def backend_target_version():
    if not os.path.exists(VERSION_PROPS):
        return None
    for line in read_text(VERSION_PROPS).splitlines():
        if line.startswith("backendTargetVersion="):
            return line.split("=", 1)[1].strip()
    return None


def png_header(path):
    """Return (width, height, colour_type), or None if this is not a PNG we can read.

    Only the IHDR is parsed; it is always the first chunk, at a fixed offset.
    """
    with open(path, "rb") as f:
        head = f.read(26)
    if len(head) < 26 or head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        return None
    width, height = struct.unpack(">II", head[16:24])
    return width, height, head[25]


def check_text_fields(problems):
    for name, key in sorted(TEXT_FILES.items()):
        path = os.path.join(LOCALE_DIR, name)
        if not os.path.exists(path):
            continue
        text = read_text(path)
        limit = LIMITS[key]
        if len(text) > limit:
            problems.append(
                "%s: %d characters, limit %d for '%s' -- fdroidserver truncates silently, "
                "losing the last %d" % (rel(path), len(text), limit, key, len(text) - limit)
            )
        if key in SINGLE_LINE and "\n" in text.strip("\n"):
            problems.append(
                "%s: '%s' is rendered as a single line; remove the interior newline(s)"
                % (rel(path), key)
            )


def check_compat_line(problems):
    """The listing's 'Built against' claim must match backendTargetVersion."""
    path = os.path.join(LOCALE_DIR, "full_description.txt")
    if not os.path.exists(path):
        return
    target = backend_target_version()
    if target is None:
        problems.append("version.properties: no backendTargetVersion= line to check against")
        return
    found = COMPAT_RE.search(read_text(path))
    if found is None:
        problems.append(
            "%s: no 'Built against LibreChat v<version>' line -- this check keeps the "
            "listing honest about the backend, so keep the sentence or delete this check"
            % rel(path)
        )
        return
    claimed = found.group(1).rstrip(".")
    if claimed != target:
        problems.append(
            "%s: says 'Built against LibreChat v%s' but version.properties has "
            "backendTargetVersion=%s" % (rel(path), claimed, target)
        )


def check_changelogs(problems):
    if not os.path.isdir(CHANGELOGS):
        return
    for name in sorted(os.listdir(CHANGELOGS)):
        path = os.path.join(CHANGELOGS, name)
        if not os.path.isfile(path):
            problems.append("%s: not a file" % rel(path))
            continue
        base, ext = os.path.splitext(name)
        if ext != ".txt":
            problems.append("%s: only .txt changelogs are read" % rel(path))
            continue
        if base != "next" and not base.isdigit():
            problems.append(
                "%s: a changelog filename must be a versionCode or 'next' -- a non-numeric "
                "name is swallowed by a bare `except ValueError: pass` and never appears"
                % rel(path)
            )
        text = read_text(path)
        if len(text) > LIMITS["whatsNew"]:
            problems.append(
                "%s: %d characters, limit %d -- the overflow is truncated silently"
                % (rel(path), len(text), LIMITS["whatsNew"])
            )


def check_images(problems):
    if not os.path.isdir(IMAGES):
        problems.append("%s: missing; the App-inclusion checklist asks for a listing icon"
                        % rel(IMAGES))
        return

    for entry in sorted(os.listdir(IMAGES)):
        path = os.path.join(IMAGES, entry)
        if os.path.isdir(path):
            if entry not in SCREENSHOT_DIRS:
                problems.append(
                    "%s/: not one of %s -- its contents are never copied into the listing"
                    % (rel(path), ", ".join(SCREENSHOT_DIRS))
                )
                continue
            names = sorted(os.listdir(path))
            if not names:
                problems.append("%s/: empty" % rel(path))
            for shot in names:
                ext = os.path.splitext(shot)[1].lstrip(".").lower()
                if ext not in ALLOWED_EXTENSIONS:
                    problems.append(
                        "%s: %s is not an allowed extension (%s)"
                        % (rel(os.path.join(path, shot)), ext or "(none)",
                           ", ".join(ALLOWED_EXTENSIONS))
                    )
            continue

        base, ext = os.path.splitext(entry)
        ext = ext.lstrip(".").lower()
        if base not in GRAPHIC_NAMES:
            problems.append(
                "%s: not one of %s, so it is ignored"
                % (rel(path), ", ".join(GRAPHIC_NAMES))
            )
            continue
        if ext not in ALLOWED_EXTENSIONS:
            problems.append(
                "%s: %s is not an allowed extension (%s)"
                % (rel(path), ext or "(none)", ", ".join(ALLOWED_EXTENSIONS))
            )

    # Colour type 2 is truecolour without alpha; 6 is truecolour with. A type-6 icon is
    # composited over F-Droid's own theme colour, so a dark mark vanishes on a dark theme.
    icon = os.path.join(IMAGES, "icon.png")
    if not os.path.exists(icon):
        problems.append("%s: missing (regenerate with scripts/make-fdroid-icon.py)" % rel(icon))
        return
    header = png_header(icon)
    if header is None:
        problems.append("%s: not a PNG this script can read" % rel(icon))
        return
    width, height, colour = header
    if (width, height) != (512, 512):
        problems.append("%s: %dx%d, expected 512x512" % (rel(icon), width, height))
    if colour == 6:
        problems.append(
            "%s: PNG colour type 6 (has an alpha channel) -- the mark was not flattened onto "
            "an opaque background, so F-Droid will composite it over its own theme colour. "
            "Regenerate with scripts/make-fdroid-icon.py." % rel(icon)
        )
    elif colour != 2:
        problems.append("%s: PNG colour type %d, expected 2 (truecolour, no alpha)"
                        % (rel(icon), colour))


def check_release_changelog(problems, version_code):
    """Release mode: the changelog this version will ship must exist and be shippable.

    Length is already covered by the generic pass over every changelog.
    """
    path = os.path.join(CHANGELOGS, "%s.txt" % version_code)
    if not os.path.exists(path):
        problems.append(
            "%s: missing. Write the user-facing changelog to changelogs/next.txt (seed one "
            "with scripts/draft-changelog.sh) -- F-Droid reads it from the tagged commit, so "
            "it cannot be added afterwards." % rel(path)
        )
        return
    text = read_text(path)
    if not text.strip():
        problems.append("%s: empty -- it would ship a blank 'What's New'" % rel(path))
        return
    for line in text.splitlines():
        if line.startswith(DRAFT_MARKER):
            problems.append(
                "%s: still has the '%s' line from scripts/draft-changelog.sh. Rewrite the "
                "generated bullets as prose and delete that line -- nothing strips it, so it "
                "would ship verbatim." % (rel(path), DRAFT_MARKER)
            )
            break


def main():
    parser = argparse.ArgumentParser(
        description=__doc__.splitlines()[0],
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--release", action="store_true",
        help="also require a usable changelog for --version-code (used by release.yml)",
    )
    parser.add_argument(
        "--version-code", metavar="N",
        help="the versionCode being released; required with --release",
    )
    args = parser.parse_args()

    if args.release and not args.version_code:
        parser.error("--release needs --version-code")
    if args.release and not args.version_code.isdigit():
        parser.error("--version-code must be all digits (got %r)" % args.version_code)

    if not os.path.isdir(LOCALE_DIR):
        print("%s%s: missing%s" % (RED, rel(LOCALE_DIR), OFF))
        return 1

    problems = []
    check_text_fields(problems)
    check_compat_line(problems)
    check_changelogs(problems)
    check_images(problems)
    if args.release:
        check_release_changelog(problems, args.version_code)

    for problem in problems:
        print("  %s%s%s" % (RED, problem, OFF))
    if problems:
        print("\n%s%d problem(s)%s in the F-Droid store listing" % (RED, len(problems), OFF))
        return 1

    print("%sok%s  F-Droid store listing" % (GREEN, OFF))
    for name, key in sorted(TEXT_FILES.items()):
        path = os.path.join(LOCALE_DIR, name)
        if os.path.exists(path):
            used = len(read_text(path))
            print("      %-22s %5d/%-5d %s(%d left)%s"
                  % (name, used, LIMITS[key], DIM, LIMITS[key] - used, OFF))
    if os.path.isdir(CHANGELOGS):
        for name in sorted(os.listdir(CHANGELOGS)):
            path = os.path.join(CHANGELOGS, name)
            if os.path.isfile(path):
                used = len(read_text(path))
                print("      changelogs/%-11s %5d/%-5d %s(%d left)%s"
                      % (name, used, LIMITS["whatsNew"], DIM,
                         LIMITS["whatsNew"] - used, OFF))
    return 0


if __name__ == "__main__":
    sys.exit(main())
