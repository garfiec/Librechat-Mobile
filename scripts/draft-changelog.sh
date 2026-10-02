#!/usr/bin/env bash
#
# Seed fastlane/metadata/android/en-US/changelogs/next.txt from the commit log.
#
# This exists to kill the blank page, not to write the changelog. v2026.10.0 shipped with no
# "What's New" at all, and the reason was not disagreement about the wording -- it was that
# nobody started the file. A seed that is always there turns writing it into editing.
#
# It is deliberately NOT a generator you can ship unreviewed, and the first line it writes is
# a marker that `scripts/check-fdroid-metadata.py --release` refuses to release with. That
# refusal is the whole design:
#
#   * The output overruns F-Droid's 500-character cap on any busy release. Measured over
#     v2026.08.4..v2026.09.0: 12 items, 624 characters. fdroidserver truncates the overflow
#     silently, so an unreviewed seed ships a sentence cut in half.
#   * Conventional-commit type does not mean user-facing. Two `feat(sync):` commits tracking
#     backend release candidates are invisible to users; a `feat(fdroid):` commit about
#     submission docs is not a feature at all.
#   * It cannot know the things worth saying. The hand-written 20260900.txt warns that React
#     artifacts importing recharts or lucide-react now report the missing package instead of
#     loading it -- a consequence of the change that appears in zero commit messages.
#   * House style is hard-wrapped prose (compare changelogs/20260804.txt), not bullets.
#
# Without the marker this script would make things worse: next.txt would always exist, the
# release guard would never fire again, and a list of internal commit subjects would ship
# every time.
#
# Usage:
#   scripts/draft-changelog.sh [--range <git-range>] [--force]
#
#   --range   override the default "<last tag>..HEAD"
#   --force   overwrite an existing next.txt (refused otherwise -- it may be hand-written)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(dirname "$SCRIPT_DIR")"
DEST="$REPO/fastlane/metadata/android/en-US/changelogs/next.txt"
MARKER='# DRAFT — rewrite as prose and delete this line'
LIMIT=500

range=""
force=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --range) range="${2:-}"; shift 2 ;;
    --force) force=true; shift ;;
    # Keep the end of this range on the last line of the header block above, or --help
    # silently stops printing partway through it.
    -h|--help) sed -n '3,32p' "${BASH_SOURCE[0]}"; exit 0 ;;
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

if [[ -e "$DEST" && "$force" != true ]]; then
  echo "$DEST already exists; refusing to overwrite it." >&2
  echo "It may be hand-written. Append to it, or pass --force." >&2
  exit 1
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
