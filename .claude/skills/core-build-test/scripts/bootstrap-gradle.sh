#!/usr/bin/env bash
# Ensure ./gradlew works even when no system 'gradle' is installed.
# - If the wrapper already exists, do nothing (safe to re-run).
# - Else if a system 'gradle' exists, use it to generate the wrapper.
# - Else download the pinned Gradle distribution and generate the wrapper from it.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

VERSION="8.10.2"   # keep in sync with gradle/wrapper/gradle-wrapper.properties

if [ -f ./gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "Gradle wrapper already present — nothing to do."
  exit 0
fi

if command -v gradle >/dev/null 2>&1; then
  echo "Using system gradle to generate the wrapper..."
  gradle wrapper --gradle-version "$VERSION" --distribution-type bin
  echo "Wrapper generated."
  exit 0
fi

echo "No system gradle found; downloading Gradle ${VERSION} to bootstrap the wrapper..."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip" -o "$tmp/gradle.zip"
unzip -q "$tmp/gradle.zip" -d "$tmp"
"$tmp/gradle-${VERSION}/bin/gradle" wrapper --gradle-version "$VERSION" --distribution-type bin
echo "Wrapper generated (Gradle ${VERSION})."
