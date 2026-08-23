#!/usr/bin/env bash
# Build the release APK for a tag from a clean checkout, then prove it is the
# same build F-Droid will make: an unsigned build of the same commit must verify
# with the signed APK's signature copied onto it (what `fdroid publish` does).
# Usage: tool/release-apk.sh v1.0.1   → dist/quickword-1.0.1.apk
set -euo pipefail

tag=${1:?usage: tool/release-apk.sh <tag>}
root=$(git rev-parse --show-toplevel)
work="$root/build/release-$tag"
rm -rf "$work" && mkdir -p "$work"

apksigner=$(find "$ANDROID_HOME"/build-tools -name apksigner | sort -V | tail -1)
PATH="$(dirname "$apksigner"):$PATH"

for kind in signed unsigned; do
  git clone -q "$root" "$work/$kind"
  git -C "$work/$kind" checkout -q "$tag"
done
cp "$root/keystore.properties" "$root/$(sed -n 's/^storeFile=//p' "$root/keystore.properties")" "$work/signed/"

for kind in signed unsigned; do
  (cd "$work/$kind" && ./gradlew --console=plain -q assembleRelease)
done
signed="$work/signed/app/build/outputs/apk/release/app-release.apk"
unsigned="$work/unsigned/app/build/outputs/apk/release/app-release-unsigned.apk"

uvx apksigcopier compare "$signed" --unsigned "$unsigned"
apksigner verify --print-certs "$signed" | grep -q "CN=QuickWord" || { echo "not signed with the QuickWord key" >&2; exit 1; }

mkdir -p "$root/dist"
out="$root/dist/quickword-${tag#v}.apk"
cp "$signed" "$out"
echo "OK: $out reproduces from $(git -C "$work/signed" rev-parse HEAD)"
