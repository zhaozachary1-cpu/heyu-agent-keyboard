#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
 export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BT="$SDK/build-tools/${ANDROID_BUILD_TOOLS:-36.0.0}"; ANDROID="$SDK/platforms/android-36/android.jar"
mkdir -p build/classes build/dex build/generated output .signing build/package-assets
if [[ "${HEYU_DELIVERY_DRY_RUN:-0}" != 0 ]]; then echo 'Public build supports production only; no test receiver is packaged.'; exit 1; fi
printf 'package ai.heyu.agent; final class DeliveryBuild { static final boolean DRY_RUN=false; }\n' > build/generated/DeliveryBuild.java
APK="output/heyu-agent-1.0.0.apk"
cp AndroidManifest.xml build/generated/AndroidManifest.xml
# Explicit asset allowlist: no bridge URL, client token, model key, private cert or local data.
find build/package-assets -type f -delete
cp assets/dict_pinyin.dat assets/agent-prompts.json assets/open-source-licenses.txt build/package-assets/
python3 scripts/fetch-android-deps.py
bash scripts/build-pinyin.sh
find build/classes -type f -delete
find build/dex -name "classes*.dex" -delete
DEPS=$(find build/deps -name '*.jar' | paste -sd: -)
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk --manifest build/generated/AndroidManifest.xml -I "$ANDROID" -A build/package-assets build/resources.zip
javac --release 17 -classpath "$ANDROID:$DEPS" -d build/classes $(find src -name '*.java') build/generated/DeliveryBuild.java
jar cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$ANDROID" --min-api 30 --output build/dex build/classes.jar $(find build/deps -name '*.jar')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes*.dex)
(cd build/native && zip -q ../../build/unsigned.apk lib/*/libheyu_pinyin.so)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
KEYSTORE="${HEYU_KEYSTORE:-.signing/agent.keystore}"
if [[ ! -f "$KEYSTORE" ]]; then
 mkdir -p "$(dirname "$KEYSTORE")"; chmod 700 "$(dirname "$KEYSTORE")"
 keytool -genkeypair -keystore "$KEYSTORE" -alias demo -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Heyu Local Build' >/dev/null 2>&1
 chmod 600 "$KEYSTORE"
fi
export HEYU_STORE_PASSWORD="${HEYU_STORE_PASSWORD:-android}"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "${HEYU_KEY_ALIAS:-demo}" --ks-pass env:HEYU_STORE_PASSWORD --out "$APK" build/aligned.apk
"$BT/apksigner" verify "$APK"
shasum -a 256 "$APK"
