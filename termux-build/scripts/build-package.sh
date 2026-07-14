#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <package> [aarch64|arm|x86_64]" >&2
  exit 2
fi

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
LINUX_ROOT=${HYPERSHELL_LINUX_BUILD_ROOT:-"$HOME/hypershell-termux"}
PACKAGE=$1
ARCH=${2:-aarch64}
cd "$LINUX_ROOT/termux-packages"
./build-package.sh -a "$ARCH" "$PACKAGE"
