#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ -f ./gradlew ]]; then
  build_gradle=(bash ./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  build_gradle=(gradle)
else
  echo '需要 Gradle 8.13、JDK 17 與 Android SDK 36；也可使用 GitHub Actions 的 build-apk.yml。' >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo '請先設定 ANDROID_HOME 或 ANDROID_SDK_ROOT，安裝 platforms;android-36 與 build-tools;36.0.0。' >&2
  exit 1
fi
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/tests -p 'test_*.py'
# The complete package includes these original, credited field recordings.
for sound in light_rain heavy_rain thunder_strike; do
  if [[ ! -s "app/src/main/res/raw/$sound.ogg" ]]; then
    python3 tools/prepare_real_weather_audio.py
    break
  fi
done
"${build_gradle[@]}" --no-daemon :app:testDebugUnitTest :app:assembleDebug
printf 'APK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
