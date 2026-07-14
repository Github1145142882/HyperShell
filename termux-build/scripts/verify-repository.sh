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

for packages_gz in sorted((repo / "dists/stable/main").glob("binary-*/Packages.gz")):
    relative_gz = packages_gz.relative_to(repo).as_posix()
    if relative_gz not in release:
        raise SystemExit(f"Release does not reference {relative_gz}")
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
