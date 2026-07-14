# HyperShell Termux environment

This directory builds a Termux bootstrap specifically for Android package
`io.github.hypershell` and prefix
`/data/data/io.github.hypershell/files/usr`.

Upstream revisions are pinned in `versions.env`. Packages built for the normal
`com.termux` prefix are intentionally rejected: native binaries and scripts
contain absolute prefix paths and are not interchangeable.

## Build after Windows restart

1. Start Ubuntu once and create its Linux user.
2. From Ubuntu, enter this repository mounted under `/mnt/c`.
3. Run the setup script once as WSL root to install host packages, then once as
   the unprivileged build user. It creates the actual build
   checkout on the Linux ext4 filesystem under `~/hypershell-termux`; building
   directly on `/mnt/c` is intentionally avoided because symlinks, permissions
   and filesystem performance are unsuitable for Termux packages.
4. Run `./termux-build/scripts/build-bootstrap.sh aarch64` for the first device
   architecture, or omit the argument to build `aarch64,arm,x86_64`.

Bootstrap builds are incremental by default and reuse checksum-verified package
outputs. Set `HYPERSHELL_FORCE_REBUILD=1` only when a deliberate clean rebuild
of every bootstrap dependency is required.

The bootstrap also includes the `pacman` executable and its local database
layout. A signed APT repository containing packages rebuilt for HyperShell is
published from the binary-only `gh-pages` branch; upstream Termux binaries are
not prefix-compatible.

The build script creates architecture-specific archives in
`termux-build/generated/assets/bootstrap/`. Gradle packages these archives as
Android assets. The Android installer verifies paths, restores symlinks and
runs the official bootstrap second stage before starting Bash.

## Repository

`build-package.sh <package> [architecture]` builds packages using the same
HyperShell prefix. `publish-repository.sh` turns the resulting `.deb` files
into a signed APT tree. Set `HYPERSHELL_REPO_SIGNING_KEY` to a local GPG key id
before publishing. The generated tree contains explicit HyperShell origin,
architecture and component metadata. Hosting credentials, private signing keys
and the final HTTPS URL are deliberately not stored in this repository.

The app installs the repository public key and configures the HTTPS source with
APT's `signed-by` option. Never replace it with the upstream `com.termux` source.
