#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DIST="$ROOT/termux-build/generated/assets/repository/dists/stable"
cd "$DIST"
awk '
  /^SHA256:/ { in_hash=1; next }
  /^[A-Z][A-Za-z0-9-]*:/ { if (in_hash) exit }
  in_hash && NF == 3 { print $1 "  " $3 }
' Release | sha256sum -c -
