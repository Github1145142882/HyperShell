#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
REPO="$ROOT/termux-build/generated/repository"
ASSETS="$ROOT/termux-build/generated/assets/repository"

[[ -f "$REPO/dists/stable/Release" ]] || {
  echo "Repository has not been generated: $REPO" >&2
  exit 1
}
mkdir -p "$ASSETS"
find "$ASSETS" -mindepth 1 -delete
cp -R "$REPO"/. "$ASSETS"/
find "$ASSETS/dists" -type f -name 'Packages.gz' -delete
release="$ASSETS/dists/stable/Release"
filtered="$release.filtered"
grep -vE 'Packages\.gz$|^[[:space:]].*[[:space:]]Release$' "$release" > "$filtered"
mv -f "$filtered" "$release"
sha256sum "$release" | cut -d' ' -f1 > "$ASSETS/VERSION"
