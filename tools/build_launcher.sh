#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -d "$SDK" ]; then
  echo "没有 Android SDK。把 ANDROID_HOME 指到已安装的 SDK。"
  exit 1
fi
printf 'sdk.dir=%s\n' "$SDK" > launcher/local.properties
chmod +x launcher/gradlew
(cd launcher && ./gradlew :app:assembleRelease)
UNSIGNED="launcher/app/build/outputs/apk/release/app-release-unsigned.apk"
if [ ! -f "$UNSIGNED" ]; then
  UNSIGNED="launcher/app/build/outputs/apk/release/app-release.apk"
fi
KEYSTORE="launcher/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Keting Debug, OU=Home, O=Keting, C=CN"
fi
mkdir -p dist
BUILD_TOOLS=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort | tail -1)
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" dist/keting-home.apk
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android dist/keting-home.apk
"$BUILD_TOOLS/apksigner" verify dist/keting-home.apk
echo "安装包：dist/keting-home.apk"
