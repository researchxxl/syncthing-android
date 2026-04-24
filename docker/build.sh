#!/usr/bin/env bash

# based on wiki/developers/Building-and-Development.md and .github/workflows/common-sign.yaml

set -eu

python3 scripts/install_minimum_android_sdk_prerequisites.py

./gradlew buildNative assembleDebug --console plain

echo "Signing debug apk"
../syncthing-android-prereq/build-tools/*/apksigner sign \
  --ks "/root/.android/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "app/build/outputs/apk/debug/app-debug.apk" \
  "app/build/outputs/apk/debug/app-debug-unsigned.apk"

# uncomment to build release and sign it using the public debug key

# ./gradlew assembleRelease --console plain
# version=$(grep '^version-name = ' gradle/libs.versions.toml | cut -d '"' -f 2)
# echo "Signing release $version"
# ../syncthing-android-prereq/build-tools/*/apksigner sign \
#   --ks "/root/.android/debug.keystore" \
#   --ks-pass pass:android \
#   --key-pass pass:android \
#   --ks-key-alias androiddebugkey \
#   --out "app/build/outputs/apk/release/syncthingfork_release_v${version}_armeabi-v7a.apk" \
#   "app/build/outputs/apk/release/app-armeabi-v7a-release-unsigned.apk"
