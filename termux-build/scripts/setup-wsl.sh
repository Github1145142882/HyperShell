#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/termux-build/versions.env"

if [[ $EUID -eq 0 ]]; then
  # The pinned Termux checkout currently names python3.14-venv, while Ubuntu
  # 24.04 runners provide the distribution-supported python3-venv package.
  # Feed a compatibility-adjusted copy to bash without modifying the submodule.
  scripts_dir="$ROOT/third_party/termux-packages/scripts"
  compat_script=$(mktemp "$scripts_dir/setup-ubuntu-hypershell.XXXXXX")
  trap 'rm -f "$compat_script"' EXIT
  sed \
    -e 's/python3\.14-venv/python3-venv/g' \
    -e 's#apt\.llvm\.org/resolute#apt.llvm.org/noble#g' \
    -e 's/llvm-toolchain-resolute-/llvm-toolchain-noble-/g' \
    -e '/^mkdir -p \/tmp\/build-essential-build$/,/^\$SUDO apt-mark hold build-essential$/d' \
    "$scripts_dir/setup-ubuntu.sh" > "$compat_script"
  bash "$compat_script"
  echo "Ubuntu system dependencies are ready."
  exit 0
fi

LINUX_ROOT=${HYPERSHELL_LINUX_BUILD_ROOT:-"$HOME/hypershell-termux"}
PACKAGES="$LINUX_ROOT/termux-packages"

mkdir -p "$LINUX_ROOT"
if [[ ! -d "$PACKAGES/.git" ]]; then
  git clone https://github.com/termux/termux-packages.git "$PACKAGES"
fi
git -C "$PACKAGES" fetch --depth 1 origin "$TERMUX_PACKAGES_COMMIT"
git -C "$PACKAGES" checkout --detach "$TERMUX_PACKAGES_COMMIT"

for patch in "$ROOT"/termux-build/patches/*.patch; do
  if ! git -C "$PACKAGES" apply --check --reverse "$patch" >/dev/null 2>&1; then
    git -C "$PACKAGES" apply "$patch"
  fi
done
grep -q '^TERMUX_APP__PACKAGE_NAME="io.github.hypershell"' "$PACKAGES/scripts/properties.sh" || {
  echo "HyperShell package-name patch is not applied" >&2
  exit 1
}

"$PACKAGES/scripts/setup-android-sdk.sh"

mkdir -p "$ROOT/termux-build/generated/assets/bootstrap" "$ROOT/termux-build/generated/repository"
echo "Unprivileged Linux build checkout and Android toolchain are ready at $PACKAGES."
