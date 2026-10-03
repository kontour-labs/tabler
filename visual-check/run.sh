#!/usr/bin/env bash
# Does every icon look like Tabler's? Draws each one twice — Tabler's SVG in
# Chromium, our ImageVector in Compose Desktop — and compares the pixels.
#
#   visual-check/run.sh
#
# Needs Node with Playwright and its Chromium (`npm i -g playwright && npx
# playwright install chromium`). Not part of CI: a browser and a few minutes,
# for a question that only changes when the generator does. Run it after any
# change to how the icons are drawn.
#
# Writes `visual-check/build/report.tsv` (every icon's score) and
# `visual-check/build/contact-sheet.png`. Fails if any icon differs from Tabler's
# by more than anti-aliasing, or if a deliberately wrong drawing gets through.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
version=$(sed -n 's/^tabler\.version=//p' "$here/../gradle.properties")
work="$here/build"
rm -rf "$work/tabler" "$work/ours" "$work/package"
mkdir -p "$work"

echo "Tabler Icons $version"
curl -fsSL --retry 3 "https://registry.npmjs.org/@tabler/icons/-/icons-$version.tgz" | tar -xz -C "$work" package/icons

NODE_PATH="${NODE_PATH:-$(npm root -g)}" node "$here/render-svgs.cjs" "$work/package" "$work/tabler"

"$here/../gradlew" -p "$here" --console=plain -q run --args="$work $here/curated.txt"
