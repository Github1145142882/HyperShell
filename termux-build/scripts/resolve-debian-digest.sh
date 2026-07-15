#!/usr/bin/env bash
set -euo pipefail
token=$(curl --fail --silent --show-error \
  'https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/debian:pull' |
  node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).token))')
curl --fail --silent --show-error --head \
  -H "Authorization: Bearer $token" \
  -H 'Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json' \
  'https://registry-1.docker.io/v2/library/debian/manifests/13-slim' |
  tr -d '\r' | awk -F': ' 'tolower($1)=="docker-content-digest" {print $2}'
