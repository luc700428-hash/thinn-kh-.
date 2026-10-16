#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle 8.11.1 not found. Build in Android Studio or via .github/workflows/android-apk.yml' >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo 'Missing ANDROID_HOME/ANDROID_SDK_ROOT; install Android SDK Platform 35 first.' >&2
  exit 1
fi
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
printf 'APK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
