#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
LOCK="$ROOT/termux-build/debian-13-arm64.lock"
SOURCE_DIR="$ROOT/termux-build/generated/sources/debian"
ASSET_DIR="$ROOT/termux-build/generated/assets/debian"
OUTPUT="$ASSET_DIR/debian-13-slim-arm64-android.tar.gz.bin"

command -v curl >/dev/null
command -v node >/dev/null
[[ -f "$LOCK" ]] || { echo "Missing $LOCK" >&2; exit 1; }
# shellcheck disable=SC1090
source "$LOCK"
[[ "$OCI_INDEX_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || { echo "Invalid OCI index digest" >&2; exit 1; }

mkdir -p "$SOURCE_DIR" "$ASSET_DIR"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

token=$(curl --fail --silent --show-error \
  'https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/debian:pull' |
  node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).token))')
accept_index='application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json'
curl --fail --silent --show-error -H "Authorization: Bearer $token" -H "Accept: $accept_index" \
  "https://registry-1.docker.io/v2/library/debian/manifests/$OCI_INDEX_DIGEST" -o "$work/index.json"

manifest_digest=$(node -e 'const d=require(process.argv[1]);const m=d.manifests.find(x=>x.platform?.os==="linux"&&x.platform?.architecture==="arm64"&&(!x.platform?.variant||x.platform.variant==="v8"));if(!m)process.exit(2);console.log(m.digest)' "$work/index.json")
accept_manifest='application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json'
curl --fail --silent --show-error -H "Authorization: Bearer $token" -H "Accept: $accept_manifest" \
  "https://registry-1.docker.io/v2/library/debian/manifests/$manifest_digest" -o "$work/manifest.json"

mapfile -t layers < <(node -e 'for(const l of require(process.argv[1]).layers)console.log(l.digest)' "$work/manifest.json")
[[ "${#layers[@]}" -eq 1 ]] || { echo "Expected one Debian slim layer" >&2; exit 1; }
for digest in "${layers[@]}"; do
  layer="$SOURCE_DIR/${digest#sha256:}.tar.gz"
  if [[ ! -f "$layer" ]] || [[ "$(sha256sum "$layer" | cut -d' ' -f1)" != "${digest#sha256:}" ]]; then
    curl --fail --location --show-error -H "Authorization: Bearer $token" \
      "https://registry-1.docker.io/v2/library/debian/blobs/$digest" -o "$layer.tmp"
    [[ "$(sha256sum "$layer.tmp" | cut -d' ' -f1)" == "${digest#sha256:}" ]] || { echo "Layer digest mismatch" >&2; exit 1; }
    mv -f "$layer.tmp" "$layer"
  fi
done

CA_BUNDLE='/c/Program Files/Git/mingw64/etc/ssl/certs/ca-bundle.crt'
[[ -f "$CA_BUNDLE" ]] || { echo "Trusted CA bundle missing: $CA_BUNDLE" >&2; exit 1; }
[[ "$(sha256sum "$CA_BUNDLE" | cut -d' ' -f1)" == "$CA_BUNDLE_SHA256" ]] || {
  echo "CA bundle digest mismatch" >&2; exit 1;
}
node "$ROOT/termux-build/tools/repack-oci-layer.mjs" \
  "$layer" "$OUTPUT.tmp" "$CA_BUNDLE" "$ASSET_DIR/debian-13-packages.txt"
sed -i "1ioci_index=$OCI_INDEX_DIGEST\narm64_manifest=$manifest_digest" "$ASSET_DIR/debian-13-packages.txt"
mv -f "$OUTPUT.tmp" "$OUTPUT"
sha256sum "$OUTPUT" > "$OUTPUT.sha256"
printf '%s\n' "$OCI_INDEX_DIGEST" > "$ASSET_DIR/debian-13-oci-digest.txt"
sha256sum -c "$OUTPUT.sha256"
listing=$(tar -tzf "$OUTPUT")
for required in usr/bin/bash usr/bin/apt usr/bin/apt-get etc/ssl/certs/ca-certificates.crt usr/share/keyrings/debian-archive-keyring.gpg; do
  grep -qx "${required}" <<<"$listing" || { echo "Debian asset missing $required" >&2; exit 1; }
done
for package in bash apt debian-archive-keyring; do
  grep -q "^${package}=" "$ASSET_DIR/debian-13-packages.txt" || { echo "Package manifest missing $package" >&2; exit 1; }
done
if tar -tvzf "$OUTPUT" | grep -q '^h'; then
  echo "Android Debian asset still contains hard links" >&2
  exit 1
fi
