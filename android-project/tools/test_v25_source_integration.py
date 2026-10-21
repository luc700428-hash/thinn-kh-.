#!/usr/bin/env python3
"""Local static integration audit. Not a replacement for Gradle compilation."""
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'android-project/app'
SRC = APP / 'src/main/java/io/atmos/weather'
assert (ROOT / '.github/workflows/build-apk.yml').is_file()
assert 'applicationId = "io.atmos.weather"' in (APP/'build.gradle.kts').read_text()
assert '25.0.0' in (APP/'build.gradle.kts').read_text()
assert (SRC / 'presentation/home/WeatherHomeScreen.kt').is_file()
assert (SRC / 'earthquake/SeismicSpeech.kt').is_file()
assert (SRC / 'alarm/V25AlarmMeridiem.kt').is_file()
assert (SRC / 'typhoon/TyphoonDirectionZh.kt').is_file()
assert (SRC / 'typhoon/WeatherLabLocalCsv.kt').is_file()
assert (SRC / 'typhoon/TyphoonCenterPage.kt').is_file()
assert 'WeatherLabLocalCsv.parse(csv)' in (SRC / 'typhoon/TyphoonCenterPage.kt').read_text()
assert 'V25HomeHero(' in (SRC / 'presentation/home/WeatherHomeScreen.kt').read_text()
assert 'MeridiemWheel(if(hour>=12)' in (SRC / 'alarm/R10AlarmDesign.kt').read_text()
assert 'NextWeatherAlarmPlayer.start' in (SRC / 'alarm/WeatherAlarmAudioService.kt').read_text()
base = APP/'src/main/assets/next_weather'
files = list(base.rglob('*.wav'))
assert len(files) == 42, f'expected 42 WAV assets, got {len(files)}'
assert all(p.stat().st_size>48000 for p in files)
landmark = APP/'src/main/assets/landmarks'
assert len(list(landmark.glob('tw_??.glb')))==22
assert len(list((landmark/'individual').glob('*.glb')))>=17
workflow = (ROOT / '.github/workflows/build-apk.yml').read_text()
assert 'v25_full_acceptance_gate.py' in workflow
assert 'publish_release:' in workflow
assert 'source_archive=' not in workflow
assert (ROOT/'README_完整包_先讀.md').is_file()
print('PASS complete Android source manifest: package, V25 integration, 42 WAV, 22 base GLB and gated OTA')
