#!/usr/bin/env bash
set -euo pipefail

REPO=${1:?Usage: verify-repository.sh <repository>}
test -s "$REPO/dists/stable/InRelease"
test -s "$REPO/dists/stable/Release"

find "$REPO/dists/stable/main" -type f -name Packages.gz -print0 |
  xargs -0 -r -n1 gzip -t
find "$REPO/pool" -type f -name '*.deb' -print0 |
  xargs -0 -r -n1 dpkg-deb --info >/dev/null

python3 - "$REPO" <<'PY'
import gzip
import hashlib
import pathlib
import sys

repo = pathlib.Path(sys.argv[1]).resolve()
release = (repo / "dists/stable/Release").read_text(encoding="utf-8")
referenced = set()
release_hashes = {}
in_sha256 = False
for line in release.splitlines():
    if line == "SHA256:":
        in_sha256 = True
        continue
    if in_sha256 and not line.startswith(" "):
        break
    if in_sha256:
        digest, size, path = line.split()
        release_hashes[path] = (digest, int(size))

for packages_gz in sorted((repo / "dists/stable/main").glob("binary-*/Packages.gz")):
    relative_gz = packages_gz.relative_to(repo / "dists/stable").as_posix()
    expected = release_hashes.get(relative_gz)
    data_gz = packages_gz.read_bytes()
    if expected is None:
        raise SystemExit(f"Release does not reference {relative_gz}")
    if expected != (hashlib.sha256(data_gz).hexdigest(), len(data_gz)):
        raise SystemExit(f"Release hash mismatch: {relative_gz}")
    packages = packages_gz.with_suffix("")
    relative_packages = packages.relative_to(repo / "dists/stable").as_posix()
    data_packages = packages.read_bytes()
    if release_hashes.get(relative_packages) != (hashlib.sha256(data_packages).hexdigest(), len(data_packages)):
        raise SystemExit(f"Release hash mismatch: {relative_packages}")
    with gzip.open(packages_gz, "rt", encoding="utf-8") as stream:
        paragraphs = stream.read().strip().split("\n\n")
    for paragraph in filter(None, paragraphs):
        fields = {}
        for line in paragraph.splitlines():
            if line.startswith(" "):
                continue
            key, separator, value = line.partition(":")
            if separator:
                fields[key] = value.strip()
        filename = fields.get("Filename")
        expected_sha = fields.get("SHA256")
        expected_size = fields.get("Size")
        if not filename or not expected_sha or not expected_size:
            raise SystemExit(f"Incomplete package stanza in {relative_gz}")
        package = (repo / filename).resolve()
        if repo not in package.parents or not package.is_file():
            raise SystemExit(f"Invalid package path: {filename}")
        data = package.read_bytes()
        if len(data) != int(expected_size):
            raise SystemExit(f"Size mismatch: {filename}")
        if hashlib.sha256(data).hexdigest() != expected_sha:
            raise SystemExit(f"SHA256 mismatch: {filename}")
        referenced.add(package)

pool = set((repo / "pool").glob("**/*.deb"))
if {path.resolve() for path in pool} != referenced:
    raise SystemExit("Repository pool and Packages indexes do not contain the same .deb files")
PY
