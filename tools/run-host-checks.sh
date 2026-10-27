#!/usr/bin/env bash
# Offline checks only: no Android SDK, app installation, device connection, or private configuration.
set -euo pipefail
cd "$(dirname "$0")/.."

task_jdk="${JAVA_HOME:-}"
if [ -z "$task_jdk" ] || [ ! -x "$task_jdk/bin/javac" ]; then
  for candidate in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do
    if [ -x "$candidate/bin/javac" ]; then task_jdk="$candidate"; break; fi
  done
fi
if [ -n "$task_jdk" ] && [ -x "$task_jdk/bin/javac" ]; then
  task_javac="$task_jdk/bin/javac"
  task_java="$task_jdk/bin/java"
else
  task_javac=javac
  task_java=java
fi
task_classes="$(mktemp -d)"
trap 'rm -rf "$task_classes"' EXIT
"$task_javac" --release 8 -d "$task_classes" \
  app/src/io/freemytv/AppAvailability.java \
  app/src/io/freemytv/CleanupRecovery.java \
  tools/tests/AppAvailabilityChecks.java \
  tools/tests/CleanupRecoveryChecks.java
"$task_java" -cp "$task_classes" io.freemytv.AppAvailabilityChecks
"$task_java" -cp "$task_classes" io.freemytv.CleanupRecoveryChecks
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_tv_audit.py'
