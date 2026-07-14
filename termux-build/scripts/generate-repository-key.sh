#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
KEY_HOME=${1:-/tmp/hypershell-repository-gpg}
PRIVATE_OUTPUT=${2:-/mnt/c/tmp/hypershell-repository-private.asc}
PUBLIC_OUTPUT="$ROOT/app/src/main/assets/hypershell-repository.asc"

if [[ ${HYPERSHELL_FORCE_KEY_ROTATION:-0} != 1 ]] && \
   [[ -s "$PUBLIC_OUTPUT" || -s "$PRIVATE_OUTPUT" ]]; then
  echo "Repository key already exists; refusing accidental rotation." >&2
  exit 1
fi

rm -rf "$KEY_HOME"
mkdir -m 700 "$KEY_HOME"
export GNUPGHOME="$KEY_HOME"
gpg --batch --passphrase '' --quick-generate-key \
  'HyperShell Repository <repository@hypershell.local>' rsa3072 sign 0
mkdir -p "$ROOT/app/src/main/assets"
gpg --armor --export 'HyperShell Repository' \
  > "$PUBLIC_OUTPUT"
gpg --armor --export-secret-keys 'HyperShell Repository' > "$PRIVATE_OUTPUT"
gpg --with-colons --fingerprint 'HyperShell Repository' \
  | awk -F: '/^fpr:/ {print $10; exit}' \
  > "$ROOT/termux-build/repository-key.fingerprint"
test -s "$PRIVATE_OUTPUT"
test -s "$PUBLIC_OUTPUT"
