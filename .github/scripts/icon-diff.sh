#!/usr/bin/env bash
# Which icons one Tabler release adds, redraws and removes against another.
#
#   .github/scripts/icon-diff.sh <old Tabler version> <new Tabler version>
#
# One line per icon, sorted: `added|changed|removed <outline|filled> <name>`.
# Compares the npm tarballs' `tabler-nodes-<set>.json` — what the generator
# reads — by name, then by path data. Shared by the release notes and the
# visual check's scope, so the two never disagree about what is new.
#
# Tarballs are kept in $TABLER_CACHE (default: a temporary directory).
set -euo pipefail

old=${1:?usage: icon-diff.sh <old version> <new version>}
new=${2:?usage: icon-diff.sh <old version> <new version>}

if [ -n "${TABLER_CACHE:-}" ]; then
  cache=$TABLER_CACHE
  mkdir -p "$cache"
else
  cache=$(mktemp -d)
  trap 'rm -rf "$cache"' EXIT
fi
fetch() {
  [ -f "$cache/$1/package/tabler-nodes-filled.json" ] && return
  mkdir -p "$cache/$1"
  curl -fsSL --retry 3 "https://registry.npmjs.org/@tabler/icons/-/icons-$1.tgz" |
    tar -xz -C "$cache/$1" package/tabler-nodes-outline.json package/tabler-nodes-filled.json
}
fetch "$old"
fetch "$new"

for set in outline filled; do
  before="$cache/$old/package/tabler-nodes-$set.json"
  after="$cache/$new/package/tabler-nodes-$set.json"
  jq -r --arg set "$set" --slurpfile o "$before" 'keys - ($o[0] | keys) | .[] | "added \($set) \(.)"' "$after"
  jq -r --arg set "$set" --slurpfile o "$before" \
    'to_entries[] | select($o[0][.key] != null and $o[0][.key] != .value) | "changed \($set) \(.key)"' "$after"
  jq -r --arg set "$set" --slurpfile n "$after" 'keys - ($n[0] | keys) | .[] | "removed \($set) \(.)"' "$before"
done | LC_ALL=C sort
