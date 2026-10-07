#!/usr/bin/env bash
#
# Write the F-Droid "What's New" for a release from the commit log. release.yml runs this on
# every dispatch; nobody writes a changelog by hand.
#
# Lists feat/fix/perf subjects since the last stable tag, minus scopes no F-Droid user sees,
# features first. Whole items are dropped from the end to stay under F-Droid's 500-character
# cap, which fdroidserver otherwise truncates silently mid-sentence. Measuring from the last
# *stable* tag means an rc train's prepatch, rc and finalize runs each regenerate the full list.
#
# Usage:
#   scripts/generate-changelog.sh --out <file> [--range <git-range>]
#
#   --out     destination file (overwritten)
#   --range   override the default "<last stable tag>..HEAD"
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(dirname "$SCRIPT_DIR")"
LIMIT=500

range=""
out=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --range) range="${2:-}"; shift 2 ;;
    --out) out="${2:-}"; shift 2 ;;
    # Keep the end of this range on the last line of the header block above, or --help
    # silently stops printing partway through it.
    -h|--help) sed -n '3,15p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$out" ]]; then
  echo "--out <file> is required." >&2
  exit 1
fi

if [[ -z "$range" ]]; then
  if ! last_tag="$(git -C "$REPO" describe --tags --abbrev=0 --exclude '*-rc*' 2>/dev/null)"; then
    echo "No stable tag reachable from HEAD to measure from. Pass --range <from>..<to>." >&2
    exit 1
  fi
  range="${last_tag}..HEAD"
fi

# Features before fixes, so the cap drops fixes first. Characters, not bytes, as in
# scripts/check-fdroid-metadata.py. The subjects travel by environment: the heredoc is
# python's stdin.
SUBJECTS="$(git -C "$REPO" log --format='%s' "$range")" python3 - "$out" "$LIMIT" "$range" <<'PY'
import os, re, sys
out, limit, rng = sys.argv[1], int(sys.argv[2]), sys.argv[3]
pattern = re.compile(r"^(feat|fix|perf)(\([^)]*\))?!?: (.+?)( \(#\d+\))*$")
order = {"feat": 0, "perf": 1, "fix": 2}
# Scopes no F-Droid user sees.
hidden = {"release", "fdroid", "skill", "ci", "build", "ios"}
found = []
for subject in os.environ["SUBJECTS"].splitlines():
    m = pattern.match(subject)
    if m and (m.group(2) or "()")[1:-1] not in hidden:
        text = m.group(3)
        found.append((order[m.group(1)], len(found), "- " + text[:1].upper() + text[1:]))
items = [line for _, _, line in sorted(found)]
more = "- And %d more; see the release notes on GitHub."
kept = []
for i, item in enumerate(items):
    rest = len(items) - i - 1
    candidate = kept + [item] + ([more % rest] if rest else [])
    if len("\n".join(candidate)) + 1 > limit:
        break
    kept.append(item)
lines = kept + ([more % (len(items) - len(kept))] if len(kept) < len(items) else [])
text = "\n".join(lines or ["Maintenance release with internal improvements only."]) + "\n"
with open(out, "w", encoding="utf-8") as f:
    f.write(text)
print("Wrote %s from %s: %d of %d items, %d characters" % (out, rng, len(kept), len(items), len(text)))
PY
