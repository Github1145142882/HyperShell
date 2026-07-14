#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
LINUX_ROOT=${HYPERSHELL_LINUX_BUILD_ROOT:-"$HOME/hypershell-termux"}
PACKAGES="$LINUX_ROOT/termux-packages"
OUTPUT="$ROOT/termux-build/generated/assets/bootstrap"
EXPECTED_PACKAGE=io.github.hypershell
ARCHITECTURES=${1:-aarch64,arm,x86_64}

actual_package=$(sed -n 's/^TERMUX_APP__PACKAGE_NAME="\([^"]*\)"/\1/p' "$PACKAGES/scripts/properties.sh")
if [[ "$actual_package" != "$EXPECTED_PACKAGE" ]]; then
  echo "Refusing to build for package '$actual_package'; expected '$EXPECTED_PACKAGE'." >&2
  exit 1
fi

mkdir -p "$OUTPUT"
cd "$PACKAGES"
build_options=(--architectures "$ARCHITECTURES" --add pacman)
if [[ ${HYPERSHELL_FORCE_REBUILD:-0} == 1 ]]; then
  build_options=(-f "${build_options[@]}")
fi
./scripts/build-bootstraps.sh "${build_options[@]}"
IFS=',' read -ra built_architectures <<< "$ARCHITECTURES"
for arch in "${built_architectures[@]}"; do
  cp -f "bootstrap-$arch.zip" "$OUTPUT/bootstrap-$arch.zip"
  sha256sum "$OUTPUT/bootstrap-$arch.zip" > "$OUTPUT/bootstrap-$arch.zip.sha256"
done

echo "HyperShell bootstrap assets written to $OUTPUT"
