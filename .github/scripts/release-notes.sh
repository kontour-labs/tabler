#!/usr/bin/env bash
# The notes for a release: which icons it adds, changes and removes, per set.
#
#   .github/scripts/release-notes.sh <previous release> <this release>
#
# Releases are Tabler's versions, `3.48.0`, or those with a fourth part for a
# re-release of the same icons, `3.48.0.1`. The previous release may be empty,
# for the first. Reads both Tabler releases' npm tarballs — 1.2 MB each, the
# same `tabler-nodes-<set>.json` the generator reads — and compares the two by
# icon name, then by path data.
set -euo pipefail

previous=${1:-}
current=${2:?usage: release-notes.sh <previous release> <this release>}
tabler() { cut -d. -f1-3 <<<"$1"; }
new=$(tabler "$current")
old=$([ -n "$previous" ] && tabler "$previous" || true)

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fetch() {
  mkdir -p "$work/$1"
  curl -fsSL --retry 3 "https://registry.npmjs.org/@tabler/icons/-/icons-$1.tgz" |
    tar -xz -C "$work/$1" package/tabler-nodes-outline.json package/tabler-nodes-filled.json
}

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

fetch "$new"
if [ -z "$old" ]; then
  for set in outline filled; do
    count=$(jq 'length' "$work/$new/package/tabler-nodes-$set.json")
    echo "- $count $set icons"
  done
  exit 0
fi

if [ "$old" = "$new" ]; then
  echo "The same icons as $previous, rebuilt: this release changes the generator, not the icons."
  exit 0
fi

fetch "$old"
breaking=false
for set in outline filled; do
  title=$(tr '[:lower:]' '[:upper:]' <<<"${set:0:1}")${set:1}
  before="$work/$old/package/tabler-nodes-$set.json"
  after="$work/$new/package/tabler-nodes-$set.json"
  added=$(jq -r --slurpfile o "$before" 'keys - ($o[0] | keys) | .[]' "$after")
  removed=$(jq -r --slurpfile n "$after" 'keys - ($n[0] | keys) | .[]' "$before")
  changed=$(jq -r --slurpfile o "$before" \
    'to_entries[] | select($o[0][.key] != null and $o[0][.key] != .value) | .key' "$after")

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
