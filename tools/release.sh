#!/usr/bin/env bash
#
# Make a release. One command: it sets the version, writes the notes, makes
# the commit and the tag, and pushes both. GitHub Actions then builds the APK
# files and makes the GitHub Release, and the app on the tablet finds it:
# Settings, About, Updates checks when it opens; the refresh icon checks again.
#
# Usage:
#   tools/release.sh <version> ["what changed"]
#
#   version        like 0.1.1. It must be higher than the one in the build file.
#   what changed   one line for the release notes. Leave it out if you have
#                  already written a "## <version>" part in docs/RELEASE_NOTES.md.
#
# Example:
#   tools/release.sh 0.1.1 "The reader no longer opens on a blank page."
#
set -euo pipefail

VERSION="${1:-}"
NOTE="${2:-}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BUILD_FILE="app/build.gradle.kts"
NOTES_FILE="docs/RELEASE_NOTES.md"

fail() { echo "release.sh: $*" >&2; exit 1; }

# The last two numbers stop at 99, because the version code is
# major * 10000 + minor * 100 + patch. See app/build.gradle.kts.
echo "$VERSION" | grep -Eq '^[0-9]+\.[0-9]{1,2}\.[0-9]{1,2}$' \
  || fail "give a version like 0.1.1. Usage: tools/release.sh <version> [\"what changed\"]"

CURRENT="$(sed -n 's/^val appVersionName = "\(.*\)"$/\1/p' "$BUILD_FILE")"
[ -n "$CURRENT" ] || fail "cannot find appVersionName in $BUILD_FILE"

code_of() { IFS=. read -r a b c <<< "$1"; echo $(( 10#$a * 10000 + 10#$b * 100 + 10#$c )); }
TAG="v$VERSION"

if git rev-parse -q --verify "refs/tags/$TAG" > /dev/null; then
  fail "the tag $TAG exists already. Pick a higher version."
fi
# The same version is fine once: that is the first release of a version that
# was only ever built on the dev machine.
if [ "$(code_of "$VERSION")" -lt "$(code_of "$CURRENT")" ]; then
  fail "$VERSION is lower than $CURRENT, the version in $BUILD_FILE. Android refuses to go down."
fi

if [ -n "$(git status --porcelain)" ]; then
  fail "there are changes that are not committed. Commit them first, so the tag holds what you tested."
fi

# Dry run before anything is written, so a rejected push changes nothing. It
# checks the branch only; the new commit and tag are pushed after this passes.
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if ! git push --dry-run origin "$BRANCH"; then
  echo "Cannot push to origin (rejected, no network or no access). Nothing was changed." >&2
  exit 1
fi
if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  echo "Tag $TAG already exists on origin. Nothing was changed." >&2
  exit 1
fi

# The notes. The app shows them under "What is new".
if ! grep -qx "## $VERSION" "$NOTES_FILE"; then
  [ -n "$NOTE" ] || fail "no \"## $VERSION\" part in $NOTES_FILE, and no note given. Give one in quotes."
  /usr/bin/python3 - "$NOTES_FILE" "$VERSION" "$NOTE" <<'PY'
import sys
path, version, note = sys.argv[1:4]
lines = open(path, encoding="utf-8").read().split("\n")
part = ["## " + version, "", "- " + note, ""]
# Straight after the "# Release notes" title, so the newest is on top.
at = next((i + 1 for i, line in enumerate(lines) if line.startswith("# ")), 0)
while at < len(lines) and lines[at].strip() == "":
    at += 1
lines[at:at] = part
if at > 0 and lines[at - 1].strip() != "":
    lines.insert(at, "")
open(path, "w", encoding="utf-8").write("\n".join(lines))
PY
fi

sed -i "s/^val appVersionName = \".*\"\$/val appVersionName = \"$VERSION\"/" "$BUILD_FILE"

git add "$BUILD_FILE" "$NOTES_FILE"
if git diff --cached --quiet; then
  echo "Version and notes were in place already. Tagging the commit as it is."
else
  git commit -q -m "Release $VERSION"
fi
git tag "$TAG"

git push -q origin "$BRANCH"
git push -q origin "$TAG"

echo
echo "Pushed $TAG from the branch $BRANCH."
echo "GitHub is building it now. It takes about 15 minutes."
echo
echo "  Watch:   gh run watch \$(gh run list --workflow=release.yml --limit 1 --json databaseId -q '.[0].databaseId')"
echo "  After:   on the tablet, Settings, About, Updates checks when it opens; the refresh icon checks again."
