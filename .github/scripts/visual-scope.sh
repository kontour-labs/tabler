#!/usr/bin/env bash
# What the visual check has to look at for this release.
#
#   .github/scripts/visual-scope.sh <previous release tag, or empty> <list file>
#
# Writes the list file and prints why:
#
#   `all`              every icon. On the first release, and whenever the code
#                      that draws them changed since the previous one: the
#                      generator, the modules, the build, or the check itself.
#   `<style>/<name>`…  otherwise only the icons Tabler added or redrew between
#                      the two releases' `tabler.version`s (`icon-diff.sh`).
#   nothing            neither: a re-release with nothing new to look at.
#
# `gradle.properties` is not code here: the release bot changes the Tabler
# version in it and nothing else. The previous tag's commit has to be present
# (a depth-1 fetch is enough: comparing two commits reads their trees only).
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
previous=${1:-}
out=${2:?usage: visual-scope.sh <previous tag> <list file>}
code=(buildSrc core outline filled test-support visual-check gradle/libs.versions.toml gradle/wrapper '*.gradle.kts')

if [ -z "$previous" ]; then
  echo all > "$out"
  echo "Every icon: there is no previous release to compare with."
  exit 0
fi

changed=$(git diff --name-only "$previous" HEAD -- "${code[@]}")
if [ -n "$changed" ]; then
  echo all > "$out"
  echo "Every icon: the code changed since $previous ($(wc -l <<<"$changed") files, $(head -3 <<<"$changed" | paste -sd, -)…)."
  exit 0
fi

version() { sed -n 's/^tabler\.version=//p'; }
old=$(git show "$previous:gradle.properties" | version)
new=$(version < gradle.properties)
if [ "$old" = "$new" ]; then
  : > "$out"
  echo "Nothing to check: the same icons as $previous (Tabler $new), and no code changed."
  exit 0
fi

"$here/icon-diff.sh" "$old" "$new" | awk '$1 == "added" || $1 == "changed" { print $2 "/" $3 }' > "$out"
echo "$(wc -l < "$out") icons added or redrawn between Tabler $old ($previous) and $new; no code changed."
