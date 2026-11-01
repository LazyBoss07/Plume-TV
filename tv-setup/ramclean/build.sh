#!/usr/bin/env bash
# Builds RAM-Clean.apk on Linux (CI) with the Android SDK's own tools, no Gradle. On Windows use build.ps1.
# Needs ANDROID_HOME with platforms;android-34 and build-tools;34.0.0, a JDK, and the signing key in
# KEYSTORE / KEYSTORE_PASS / KEY_ALIAS / KEY_PASS (the same key as Plume's, so updates install over each other).
set -euo pipefail
cd "$(dirname "$0")"
BT="$ANDROID_HOME/build-tools/34.0.0"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"

rm -rf build && mkdir -p build/classes
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 34 \
  -o build/base.apk build/res.zip
javac -encoding UTF-8 --release 8 -classpath "$JAR" -d build/classes $(find src -name '*.java') 2>&1 \
  | grep -v -e 'bootstrap classpath' -e '^1 warning' -e 'source value 8' -e 'target value 8' -e 'obsolete options' || true
"$BT/d8" --lib "$JAR" --min-api 26 --output build $(find build/classes -name '*.class')
(cd build && zip -q -0 base.apk classes.dex)
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASS --ks-key-alias "$KEY_ALIAS" --key-pass env:KEY_PASS \
  --out RAM-Clean.apk build/aligned.apk
echo "Built $(pwd)/RAM-Clean.apk"
