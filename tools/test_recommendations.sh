#!/usr/bin/env bash
# Desktop regression checks; Android SDK needed, no TV access or private configuration.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
android_jar="$sdk/platforms/android-34/android.jar"
[ -f "$android_jar" ] || { echo "Android platform 34 is required." >&2; exit 1; }
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
json_jar="$work/json.jar"
curl -fsSL --connect-timeout 10 --max-time 30 -o "$json_jar" https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar
python3 - "$json_jar" <<'PY'
import hashlib,sys
assert hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest() == '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed', 'JSON test dependency checksum mismatch'
PY
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -source 8 -target 8 -Xlint:-options -cp "$json_jar:$android_jar" -d "$work/classes" \
  tests/recommendations/Support.java app/src/io/freemytv/Recs.java app/src/io/freemytv/StremioCatalogs.java \
  app/src/io/freemytv/RecommendationRouting.java app/src/io/freemytv/RecommendationPlan.java \
  app/src/io/freemytv/AppAvailability.java app/src/io/freemytv/ArtworkCache.java tests/recommendations/ArtworkCacheTest.java tests/recommendations/RecommendationsTest.java tests/recommendations/RuntimePlanTest.java
result=0
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$work/classes:$json_jar:$android_jar" io.freemytv.ArtworkCacheTest || result=1
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$work/classes:$json_jar:$android_jar" io.freemytv.RecommendationsTest || result=1
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$work/classes:$json_jar:$android_jar" io.freemytv.RuntimePlanTest || result=1
exit "$result"
