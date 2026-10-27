#!/usr/bin/env bash
# Run only on a disposable emulator; weather data is a deterministic fixture.
set -euo pipefail
project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
evidence="$project/build/ui-regression"
mkdir -p "$evidence"
if [[ "$(timeout 10s adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo 'UI 回歸測試僅允許在可拋棄的 Android 模擬器執行。' >&2
  exit 2
fi
limit="${THINN_UI_TIMEOUT_SECONDS:-480}"
if [[ ! "$limit" =~ ^[0-9]+$ ]] || (( limit < 1 || limit > 480 )); then
  echo 'UI 測試時間上限必須為 1 至 480 秒。' >&2
  exit 2
fi
collect_evidence() {
  local status=$?
  trap - EXIT
  set +e
  if (( status != 0 )); then
    # SIGQUIT emits Java thread stacks when a test/UI thread is blocked.
    timeout 10s adb shell 'for p in $(pidof io.atmos.weather); do kill -3 "$p"; done' >/dev/null 2>&1
    timeout 15s adb shell dumpsys activity top > "$evidence/activity.txt" 2>&1
    timeout 15s adb shell dumpsys gfxinfo io.atmos.weather > "$evidence/gfxinfo.txt" 2>&1
    timeout 10s adb exec-out screencap -p > "$evidence/last-screen.png" 2>/dev/null
  fi
  timeout 15s adb logcat -d -t 8000 > "$evidence/logcat.txt" 2>&1
  timeout 20s adb pull /sdcard/Android/data/io.atmos.weather/files/ui-regression "$evidence/screens" >/dev/null 2>&1
  printf '%s\n' "$status" > "$evidence/exit-code.txt"
  exit "$status"
}
trap collect_evidence EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
timeout 10s adb logcat -c
gradle_bin="${THINN_GRADLE_BIN:-gradle}"
set +e
timeout --signal=TERM --kill-after=20s "${limit}s" \
  "$gradle_bin" --no-daemon --no-configuration-cache --console=plain --project-dir "$project" --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx2300m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.atmos.weather.ui.RenderingRegressionTest \
  -Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000 \
  2>&1 | tee "$evidence/connected-tests.log"
result=${PIPESTATUS[0]}
set -e
if (( result != 0 )); then
  echo "UI 測試失敗或逾時（狀態 $result）；停止候選 APK 建置。" >&2
  exit "$result"
fi
python3 "$project/tools/verify_rendering_results.py" "$project/app/build/outputs/androidTest-results/connected"
