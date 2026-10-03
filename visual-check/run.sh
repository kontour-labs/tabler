#!/usr/bin/env bash
# Does every icon look like Tabler's? Draws each one twice — Tabler's SVG in
# Chromium, our ImageVector in Compose Desktop — and compares the pixels.
#
#   visual-check/run.sh               every icon, about five minutes
#   visual-check/run.sh <list file>   only the `style/name` lines in it
#
# The release workflow runs it before publishing, on the icons Tabler added or
# redrew, or on every icon when the code changed since the last release
# (`.github/scripts/visual-scope.sh`).
#
# Needs Node with Playwright (`npm i -g playwright && npx playwright install
# chromium`), or with `playwright-core` and PLAYWRIGHT_CHANNEL=chrome for an
# installed Chrome, as in CI.
#
# Writes `visual-check/build/report.tsv` (each icon's score) and
# `visual-check/build/contact-sheet.png`. Fails if any icon differs from Tabler's
# by more than anti-aliasing, or if a deliberately wrong drawing gets through.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
list=${1:+$(cd "$(dirname "$1")" && pwd)/$(basename "$1")}
version=$(sed -n 's/^tabler\.version=//p' "$here/../gradle.properties")
work="$here/build"
rm -rf "$work/tabler" "$work/ours" "$work/package" "$work/report.tsv" "$work/contact-sheet.png"
mkdir -p "$work"

if [ -n "$list" ] && [ ! -s "$list" ]; then
  echo "Nothing to check."
  exit 0
fi

echo "Tabler Icons $version${list:+, $(wc -l < "$list") icons}"
curl -fsSL --retry 3 "https://registry.npmjs.org/@tabler/icons/-/icons-$version.tgz" | tar -xz -C "$work" package/icons

# The controls' icons are drawn whatever is being checked: they must still fail.
render=()
if [ -n "$list" ]; then
  sort -u "$list" <(printf '%s\n' outline/arrow-up outline/arrow-down outline/chevron-left outline/chevron-right \
    outline/circle outline/circle-dot filled/star filled/heart outline/x outline/check outline/chevron-down outline/home) \
    > "$work/render.txt"
  render=("$work/render.txt")
fi
NODE_PATH="${NODE_PATH:-$(npm root -g)}" node "$here/render-svgs.cjs" "$work/package" "$work/tabler" "${render[@]}"

# No daemon in CI: it would sit on its 3 GB beside the check's JVM.
"$here/../gradlew" -p "$here" ${CI:+--no-daemon} --console=plain -q writeClasspath
java -Xmx1g -Djava.awt.headless=true -cp "$(cat "$work/classpath.txt")" VisualCheckKt \
  "$work" "$here/curated.txt" ${list:+"$list"}
