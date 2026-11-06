#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/digitalnivuk/Android/Sdk}}
JDK=${JAVA_HOME:-/home/digitalnivuk/.local/share/jdks/microsoft-jdk-21}
PLATFORM=25
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
WORK=$(mktemp -d "${TMPDIR:-/tmp}/tv-harness-build.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" build

"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link -I "$SDK/platforms/android-$PLATFORM/android.jar" \
  --manifest AndroidManifest.xml -A assets --min-sdk-version "$PLATFORM" \
  --target-sdk-version 28 --version-code 1 --version-name 1.0 \
  --java "$WORK/gen" -o "$WORK/app.apk" "$WORK/res.zip"
find src "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
"$JDK/bin/javac" --release 8 -Xlint:all -Xlint:-options -Werror -encoding UTF-8 \
  -classpath "$SDK/platforms/android-$PLATFORM/android.jar" \
  -d "$WORK/classes" @"$WORK/sources.txt"
find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$BT/d8" --release --min-api "$PLATFORM" \
  --lib "$SDK/platforms/android-$PLATFORM/android.jar" \
  --output "$WORK" @"$WORK/classes.txt"
(cd "$WORK" && zip -q -X app.apk classes.dex)
"$BT/zipalign" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
KEYSTORE=${HARNESS_KEYSTORE:-./release.keystore}
: "${HARNESS_KEYSTORE_PASSWORD:?Set HARNESS_KEYSTORE_PASSWORD to sign the APK}"
HARNESS_KEY_PASSWORD=${HARNESS_KEY_PASSWORD:-$HARNESS_KEYSTORE_PASSWORD}
export HARNESS_KEY_PASSWORD
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:HARNESS_KEYSTORE_PASSWORD \
  --key-pass env:HARNESS_KEY_PASSWORD --out build/harness-tv.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/harness-tv.apk
printf 'Built build/harness-tv.apk (%s bytes)\n' "$(wc -c < build/harness-tv.apk | tr -d ' ')"
