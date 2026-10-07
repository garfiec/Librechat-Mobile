#!/usr/bin/env bash
#
# Build the F-Droid "What's New" text from the commit log.
#
# Two modes:
#
#   * Seed (default) writes fastlane/metadata/android/en-US/changelogs/next.txt with a
#     `# DRAFT` first line that `scripts/check-fdroid-metadata.py --release` refuses to release
#     with. Use it to start a hand-written changelog: commit subjects are not prose, and the
#     things most worth saying (what a change broke) are never in them.
#   * --auto writes a release-ready list to --out. release.yml runs it when no hand-written
#     next.txt exists, so a release never stops for want of a changelog. Only feat/fix/perf
#     subjects are listed, minus scopes no user sees (release, fdroid, skill, ci, build, ios),
#     features first; whole items are dropped from the end to stay under
#     F-Droid's 500-character cap, which fdroidserver otherwise truncates silently mid-sentence.
#
# Usage:
#   scripts/draft-changelog.sh [--range <git-range>] [--force]
#   scripts/draft-changelog.sh --auto --out <file> [--range <git-range>]
#
#   --range   override the default "<last tag>..HEAD"
#   --force   overwrite an existing next.txt (refused otherwise -- it may be hand-written)
#   --auto    write a release-ready changelog (no DRAFT line) to --out
#   --out     destination for --auto
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(dirname "$SCRIPT_DIR")"
DEST="$REPO/fastlane/metadata/android/en-US/changelogs/next.txt"
MARKER='# DRAFT — rewrite as prose and delete this line'
LIMIT=500

range=""
force=false
auto=false
out=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --range) range="${2:-}"; shift 2 ;;
    --force) force=true; shift ;;
    --auto) auto=true; shift ;;
    --out) out="${2:-}"; shift 2 ;;
    # Keep the end of this range on the last line of the header block above, or --help
    # silently stops printing partway through it.
    -h|--help) sed -n '3,24p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$range" ]]; then
  if ! last_tag="$(git -C "$REPO" describe --tags --abbrev=0 2>/dev/null)"; then
    echo "No tag reachable from HEAD to measure from. Pass --range <from>..<to>." >&2
    exit 1
  fi
  range="${last_tag}..HEAD"
fi

# feat/fix/perf only: everything else (chore, docs, ci, build, test, refactor) is invisible to
# a user by definition. `!` allows the breaking-change marker. Trailing "(#123)" refs are
# stripped -- there can be more than one when a cherry-pick carries the original number too.
items="$(
  git -C "$REPO" log --format='%s' "$range" \
    | grep -E '^(feat|fix|perf)(\([^)]*\))?!?: ' \
    | sed -E 's/^(feat|fix|perf)(\([^)]*\))?!?: //' \
    | sed -E 's/( \(#[0-9]+\))+$//' \
    | sed -E 's/^/- /' \
    || true
)"

total="$(git -C "$REPO" log --oneline "$range" | wc -l | tr -d ' ')"

if [[ "$auto" == true ]]; then
  if [[ -z "$out" ]]; then
    echo "--auto needs --out <file>." >&2
    exit 1
  fi
  # Features before fixes, so the cap drops fixes first. Characters, not bytes, as in
  # scripts/check-fdroid-metadata.py.
  # The subjects travel by environment: the heredoc is python's stdin.
  SUBJECTS="$(git -C "$REPO" log --format='%s' "$range")" python3 - "$out" "$LIMIT" <<'PY'
import os, re, sys
out, limit = sys.argv[1], int(sys.argv[2])
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
print("Wrote %s: %d of %d user-facing items, %d characters" % (out, len(kept), len(items), len(text)))
PY
  exit 0
fi

if [[ -e "$DEST" && "$force" != true ]]; then
  echo "$DEST already exists; refusing to overwrite it." >&2
  echo "It may be hand-written. Append to it, or pass --force." >&2
  exit 1
fi

{
  printf '%s\n' "$MARKER"
  if [[ -n "$items" ]]; then
    printf '%s\n' "$items"
  else
    printf '%s\n' "- (no feat/fix/perf commits in $range — say what changed, or that nothing user-facing did)"
  fi
} > "$DEST"

count="$(printf '%s' "$items" | grep -c '^- ' || true)"
# Characters, not bytes: F-Droid's cap is on characters and `wc -c` would over-report by 2
# on the marker's em-dash alone. scripts/check-fdroid-metadata.py counts the same way.
chars="$(python3 -c "import sys;print(len(open(sys.argv[1],encoding='utf-8').read()))" "$DEST")"

echo "Seeded $(python3 -c "import os,sys;print(os.path.relpath(sys.argv[1],sys.argv[2]))" "$DEST" "$REPO")"
echo "  range          $range ($total commits, $count user-facing candidates)"
echo "  draft size     $chars characters"
if (( chars > LIMIT )); then
  echo "  F-Droid caps 'What's New' at $LIMIT and truncates the rest silently — this needs cutting, not just editing." >&2
fi
echo
echo "Now rewrite it as prose, drop what no user would notice, and delete the first line."
echo "scripts/check-fdroid-metadata.py --release will refuse to release while that line is there."
