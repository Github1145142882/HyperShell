#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SOURCE_DIR="$ROOT/termux-build/generated/sources/ubuntu"
ASSET_DIR="$ROOT/termux-build/generated/assets/ubuntu"
SOURCE="$SOURCE_DIR/ubuntu-base-24.04.4-base-arm64.tar.gz"
SOURCE_SUM="$SOURCE_DIR/ubuntu-base-24.04.4-base-arm64.tar.gz.sha256"
OUTPUT="$ASSET_DIR/ubuntu-base-24.04.4-base-arm64-android.tar.gz.bin"

[[ -f "$SOURCE" && -f "$SOURCE_SUM" ]] || {
  echo "Verified Ubuntu Base source is missing from $SOURCE_DIR" >&2
  exit 1
}
expected=$(cut -d' ' -f1 < "$SOURCE_SUM")
actual=$(sha256sum "$SOURCE" | cut -d' ' -f1)
[[ "$actual" == "$expected" ]] || {
  echo "Official Ubuntu Base SHA-256 mismatch" >&2
  exit 1
}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/root" "$ASSET_DIR"
tar -xzf "$SOURCE" --no-same-owner -C "$work/root"
# Android app-data filesystems may reject hard-link creation. Store each hard
# link as an independent regular file while preserving symlinks and modes.
tar --sort=name --hard-dereference --numeric-owner --owner=0 --group=0 \
  -I 'gzip -n -9' -cf "$OUTPUT.tmp" -C "$work/root" .
mv -f "$OUTPUT.tmp" "$OUTPUT"
sha256sum "$OUTPUT" > "$OUTPUT.sha256"
sha256sum -c "$OUTPUT.sha256"
if tar -tvzf "$OUTPUT" | grep -q '^h'; then
  echo "Android Ubuntu asset still contains hard links" >&2
  exit 1
fi
