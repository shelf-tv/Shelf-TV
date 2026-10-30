#!/usr/bin/env bash
# Build a signed release and publish it on GitHub.
#
#   ./scripts/release.sh
#
# Bump versionCode and versionName in app/build.gradle.kts and add a "## <version>" section to
# CHANGELOG.md first. The APK is attached as shelf.apk, so
# https://github.com/shelf-tv/Shelf-TV/releases/latest/download/shelf.apk (and the website's /get
# page) always gives the newest version.
#
# Signing uses ~/.android/shelf-release.properties (or $SHELF_SIGNING). That key must be the same
# for every release, or TVs can't update over the old version: keep a backup of it.
set -euo pipefail
cd "$(dirname "$0")/.."

SIGNING="${SHELF_SIGNING:-$HOME/.android/shelf-release.properties}"
[[ -f "$SIGNING" ]] || { echo "No signing key at $SIGNING."; exit 1; }

version="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)"
tag="v$version"
gh release view "$tag" >/dev/null 2>&1 && { echo "$tag is already released. Bump versionName first."; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Commit or stash your changes first."; exit 1; }

notes="$(awk -v v="$version" '/^## / { if (found) exit; found = index($0, v) > 0; next } found' CHANGELOG.md)"
[[ -n "$notes" ]] || { echo "Add a '## $version' section to CHANGELOG.md first."; exit 1; }

echo "→ Building Shelf $version…"
./gradlew assembleRelease -q
apk=app/build/outputs/apk/release/app-release.apk

apksigner="$(ls "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"/build-tools/*/apksigner | sort -V | tail -n 1)"
"$apksigner" verify --print-certs "$apk" | grep -q "CN=Hayat Butt" || { echo "APK isn't signed with the release key."; exit 1; }

rm -rf dist && mkdir dist
cp "$apk" dist/shelf.apk
(cd dist && shasum -a 256 shelf.apk > shelf.apk.sha256)

git push origin HEAD
git tag "$tag" && git push origin "$tag"
gh release create "$tag" dist/shelf.apk dist/shelf.apk.sha256 --title "Shelf $version" --notes "$notes"
echo "✓ Released $tag"
