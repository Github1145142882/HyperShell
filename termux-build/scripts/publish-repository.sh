#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
LINUX_ROOT=${HYPERSHELL_LINUX_BUILD_ROOT:-"$HOME/hypershell-termux"}
DEBS=${1:-"$LINUX_ROOT/termux-packages/output"}
REPO=${2:-"$ROOT/termux-build/generated/repository"}
ASSET_REPO="$ROOT/termux-build/generated/assets/repository"
DIST=stable
COMPONENT=main

command -v apt-ftparchive >/dev/null || {
  echo "apt-ftparchive is required (package: apt-utils)" >&2
  exit 1
}

mkdir -p "$REPO/pool/$COMPONENT" "$REPO/dists/$DIST/$COMPONENT/binary-aarch64" \
  "$REPO/dists/$DIST/$COMPONENT/binary-arm" "$REPO/dists/$DIST/$COMPONENT/binary-x86_64"
find "$REPO/pool/$COMPONENT" -maxdepth 1 -type f -name '*.deb' -delete
find "$DEBS" -maxdepth 1 -type f -name '*.deb' -exec cp -f {} "$REPO/pool/$COMPONENT/" \;

for arch in aarch64 arm x86_64; do
  target="$REPO/dists/$DIST/$COMPONENT/binary-$arch"
  (cd "$REPO" && apt-ftparchive -a "$arch" packages "pool/$COMPONENT") > "$target/Packages"
  gzip -n -9 -c "$target/Packages" > "$target/Packages.gz"
done

release_options=(
  -o APT::FTPArchive::Release::Origin=HyperShell
  -o APT::FTPArchive::Release::Label=HyperShell
  -o APT::FTPArchive::Release::Suite="$DIST"
  -o APT::FTPArchive::Release::Codename="$DIST"
  -o APT::FTPArchive::Release::Architectures="aarch64 arm x86_64"
  -o APT::FTPArchive::Release::Components="$COMPONENT"
  -o APT::FTPArchive::Release::Description="Packages rebuilt for io.github.hypershell"
)
(cd "$REPO" && apt-ftparchive "${release_options[@]}" release "dists/$DIST") > "$REPO/dists/$DIST/Release"
if [[ -n "${HYPERSHELL_REPO_SIGNING_KEY:-}" ]]; then
  gpg --batch --yes --local-user "$HYPERSHELL_REPO_SIGNING_KEY" --armor --detach-sign \
    --output "$REPO/dists/$DIST/Release.gpg" "$REPO/dists/$DIST/Release"
  gpg --batch --yes --local-user "$HYPERSHELL_REPO_SIGNING_KEY" --clearsign \
    --output "$REPO/dists/$DIST/InRelease" "$REPO/dists/$DIST/Release"
else
  rm -f "$REPO/dists/$DIST/Release.gpg" "$REPO/dists/$DIST/InRelease"
  echo "Repository generated unsigned; set HYPERSHELL_REPO_SIGNING_KEY before publishing." >&2
fi

"$ROOT/termux-build/scripts/sync-repository-assets.sh"
echo "Offline APK repository copied to $ASSET_REPO"
