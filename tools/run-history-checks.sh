#!/usr/bin/env bash
# Run only with explicit device-test authorization. Requires the current app to be built and installed first.
set -euo pipefail
cd "$(dirname "$0")/.."
DEVICE="${1:?Usage: tools/run-history-checks.sh IP:PORT}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ="$SDK/platforms/android-34/android.jar"
JDK="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME="$JDK"
TMP_CHECKS=$(mktemp -d)
installed=false
cleanup() {
  if $installed; then "$SDK/platform-tools/adb" -s "$DEVICE" uninstall io.freemytv.historychecks >/dev/null || true; fi
  rm -rf "$TMP_CHECKS"
}
trap cleanup EXIT
mkdir -p "$TMP_CHECKS/classes" "$TMP_CHECKS/dex"
cat > "$TMP_CHECKS/AndroidManifest.xml" <<'MANIFEST'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.freemytv.historychecks">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="33" />
  <application android:label="Free My TV history checks" android:hasCode="true" />
  <instrumentation android:name="io.freemytv.HistoryChecks" android:targetPackage="io.freemytv" android:targetProcesses="io.freemytv:home" />
</manifest>
MANIFEST
"$BT/aapt2" link -o "$TMP_CHECKS/base.apk" -I "$AJ" --manifest "$TMP_CHECKS/AndroidManifest.xml"
"$JDK/bin/javac" -source 8 -target 8 -Xlint:-options -cp "$AJ:out/classes" -d "$TMP_CHECKS/classes" tools/tests/HistoryChecks.java
"$BT/d8" --lib "$AJ" --classpath out/classes --output "$TMP_CHECKS/dex" "$TMP_CHECKS"/classes/io/freemytv/*.class
cp "$TMP_CHECKS/base.apk" "$TMP_CHECKS/checks.apk"
(cd "$TMP_CHECKS/dex" && zip -q "$TMP_CHECKS/checks.apk" classes.dex)
"$BT/zipalign" -f 4 "$TMP_CHECKS/checks.apk" "$TMP_CHECKS/aligned.apk"
"$BT/apksigner" sign --ks "${FREEMYTV_KEYSTORE:-freemytv.keystore}" --ks-pass "pass:${FREEMYTV_KEYSTORE_PASS:-freemytv}" "$TMP_CHECKS/aligned.apk"
"$SDK/platform-tools/adb" -s "$DEVICE" install -r --no-incremental "$TMP_CHECKS/aligned.apk"
installed=true
instrument_args=(am instrument -w -e inspect "${2:-false}")
if [ -n "${3:-}" ]; then instrument_args+=(-e sample_title "$3"); fi
instrument_args+=(io.freemytv.historychecks/io.freemytv.HistoryChecks)
printf -v instrument_command '%q ' "${instrument_args[@]}"
"$SDK/platform-tools/adb" -s "$DEVICE" shell "$instrument_command" | tee "$TMP_CHECKS/result.txt"
if [ "${2:-false}" != true ]; then rg -q '^PASS:' "$TMP_CHECKS/result.txt"; fi
if [ "${2:-false}" != true ]; then
  "$SDK/platform-tools/adb" -s "$DEVICE" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
fi
