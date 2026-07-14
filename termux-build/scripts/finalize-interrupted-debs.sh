#!/usr/bin/env bash
set -euo pipefail

PACKAGES=${HYPERSHELL_LINUX_BUILD_ROOT:-"$HOME/hypershell-termux"}/termux-packages
cd "$PACKAGES"
for package in pacman proot libandroid-shmem libtalloc; do
  dir="$HOME/.termux-build/$package/package"
  [[ -f "$dir/control.tar.xz" && -f "$dir/data.tar.xz" ]] || continue
  control=$(tar -xOf "$dir/control.tar.xz" ./control)
  name=$(printf '%s\n' "$control" | sed -n 's/^Package: //p')
  version=$(printf '%s\n' "$control" | sed -n 's/^Version: //p')
  arch=$(printf '%s\n' "$control" | sed -n 's/^Architecture: //p')
  printf '2.0\n' > "$dir/debian-binary"
  target="$PACKAGES/output/${name}_${version}_${arch}.deb"
  rm -f "$target"
  (cd "$dir" && ar r "$target" debian-binary control.tar.xz data.tar.xz)
  echo "$target"
done
