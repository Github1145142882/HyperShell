# HyperShell

HyperShell is a HyperOS-style Android terminal and root file browser built with
Jetpack Compose, Miuix and the official Termux terminal emulator/view.

The Android package name is `io.github.hypershell`. Termux packages are rebuilt
for its absolute prefix instead of consuming incompatible `com.termux` binaries.

## Build the app

The checked-in source excludes generated bootstrap archives, Ubuntu rootfs files,
APK outputs and `.deb` repositories. See `termux-build/README.md` to prepare the
pinned Linux build environment, then generate the assets before running:

```text
./gradlew :app:assembleDebug
```

## Package repository

The `Build HyperShell repository` workflow rebuilds requested packages, signs the
APT metadata, and publishes binary artifacts to the `gh-pages` branch. The app
uses the bundled public key and the following source:

```text
https://raw.githubusercontent.com/Github1145142882/HyperShell/gh-pages/
```

Repository signing key fingerprint is recorded in
`termux-build/repository-key.fingerprint`. The private key is stored only as the
GitHub Actions secret `HYPERSHELL_REPOSITORY_PRIVATE_KEY`.

## Safety boundary

HyperShell does not hide processes, erase system audit logs, spoof package
identity, or bypass Magisk/KernelSU authorization. Terminal history and output
remain process-local.
