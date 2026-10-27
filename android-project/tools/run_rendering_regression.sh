#!/usr/bin/env bash
# Run only on a disposable emulator; weather data is a deterministic fixture.
set -euo pipefail
project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
evidence="$project/build/ui-regression"
mkdir -p "$evidence"
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo 'UI 回歸測試僅允許在可拋棄的 Android 模擬器執行。' >&2
  exit 2
fi
adb logcat -c
gradle_bin="${THINN_GRADLE_BIN:-gradle}"
set +e
"$gradle_bin" --no-daemon --console=plain --project-dir "$project" --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx2300m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.atmos.weather.ui.RenderingRegressionTest \
  2>&1 | tee "$evidence/connected-tests.log"
result=${PIPESTATUS[0]}
set -e
adb logcat -d -t 5000 > "$evidence/logcat.txt" || true
adb pull /sdcard/Android/data/io.atmos.weather/files/ui-regression "$evidence/screens" >/dev/null 2>&1 || true
exit "$result"
