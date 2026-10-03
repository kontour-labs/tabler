#!/usr/bin/env bash
# The notes for a release: which icons it adds, changes and removes, per set.
#
#   .github/scripts/release-notes.sh <previous release> <this release>
#
# Releases are Tabler's versions, `3.48.0`, or those with a fourth part for a
# re-release of the same icons, `3.48.0.1`. The previous release may be empty,
# for the first. What changed comes from `icon-diff.sh`, which the visual
# check's scope reads too.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
previous=${1:-}
current=${2:?usage: release-notes.sh <previous release> <this release>}
tabler() { cut -d. -f1-3 <<<"$1"; }
new=$(tabler "$current")
old=$([ -n "$previous" ] && tabler "$previous" || true)

export TABLER_CACHE=${TABLER_CACHE:-$(mktemp -d)}

# `a-b-2` → "`Tabler.Outline.AB2` (`a-b-2`)", the name it is used by.
list() {
  local set=$1
  awk -F- -v set="$set" '{
    name = ""
    for (i = 1; i <= NF; i++) name = name toupper(substr($i, 1, 1)) substr($i, 2)
    printf "- `Tabler.%s.%s` (`%s`)\n", set, name, $0
  }'
}

echo "Icons generated from [Tabler Icons $new](https://github.com/tabler/tabler-icons/releases/tag/v$new)."
echo

if [ -z "$old" ]; then
  mkdir -p "$TABLER_CACHE/$new"
  [ -f "$TABLER_CACHE/$new/package/tabler-nodes-filled.json" ] ||
    curl -fsSL --retry 3 "https://registry.npmjs.org/@tabler/icons/-/icons-$new.tgz" |
      tar -xz -C "$TABLER_CACHE/$new" package/tabler-nodes-outline.json package/tabler-nodes-filled.json
  for set in outline filled; do
    echo "- $(jq 'length' "$TABLER_CACHE/$new/package/tabler-nodes-$set.json") $set icons"
  done
  exit 0
fi

if [ "$old" = "$new" ]; then
  echo "The same icons as $previous, rebuilt: this release changes the generator, not the icons."
  exit 0
fi

diff=$("$here/icon-diff.sh" "$old" "$new")
pick() { awk -v kind="$1" -v set="$2" '$1 == kind && $2 == set { print $3 }' <<<"$diff"; }

breaking=false
for set in outline filled; do
  title=$(tr '[:lower:]' '[:upper:]' <<<"${set:0:1}")${set:1}
  added=$(pick added "$set")
  changed=$(pick changed "$set")
  removed=$(pick removed "$set")

  echo "## $title"
  echo
  if [ -z "$added$removed$changed" ]; then
    echo "No changes since $previous."
    echo
    continue
  fi
  if [ -n "$added" ]; then
    echo "### Added ($(wc -l <<<"$added"))"
    echo
    list "$title" <<<"$added"
    echo
  fi
  if [ -n "$changed" ]; then
    echo "### Redrawn ($(wc -l <<<"$changed"))"
    echo
    list "$title" <<<"$changed"
    echo
  fi
  if [ -n "$removed" ]; then
    breaking=true
    echo "### Removed ($(wc -l <<<"$removed")) — breaking"
    echo
    list "$title" <<<"$removed"
    echo
  fi
done

if [ "$breaking" = true ]; then
  echo "**Breaking:** Tabler removed or renamed the icons above, and code that uses them no longer compiles."
fi
