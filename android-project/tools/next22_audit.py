#!/usr/bin/env python3
"""thinn-khì NEXT 22-feature source audit.

Records honest source evidence. It neither changes any acceptance status nor
pretends that visual, sound-quality or OEM device checks were performed.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import wave
from datetime import datetime, timezone

SCRIPT = Path(__file__).resolve()
ROOT = SCRIPT.parents[1]  # android-project/tools/next22_audit.py -> android-project
APP = ROOT / 'app/src/main'
KOTLIN = APP / 'java/io/atmos/weather'

# Paths here are architectural signals, NOT pass/fail release verdicts.
FEATURES = {
    'home_layout': (['presentation/home/WeatherHomeScreen.kt','presentation/home/HomeWeatherBriefing.kt'], []),
    'scene_14': (['next/WeatherScenes.kt','next/NextWeatherBackdrop.kt'], []),
    'ambience_14': (['next/NextPcmAudio.kt'], []),
    'alarm_28': (['next/NextWeatherAlarmPlayer.kt','alarm/R10AlarmDesign.kt'], []),
    'hi_fi_audio': (['next/NextPcmAudio.kt'], []),
    'liquid_optics': (['next/NextOpticalSurface.kt','ui/ThinnOpticalMaterial.kt'], []),
    'motion_physics': (['next/NextMotionTokens.kt','alarm/R10AlarmDesign.kt'], []),
    'drag_toolbar': (['next/NextReorderableToolDock.kt','ui/GlassNavigation.kt'], []),
    'icons_typography': (['notifications/IslandIconMotion.kt','ui/ThinnDesignTokens.kt'], []),
    'haptics': (['haptics/HapticEngine.kt','haptics/HapticSettings.kt'], []),
    'typhoon': (['typhoon/TyphoonCenterPage.kt','typhoon/StormTimelineOverlay.kt','typhoon/OfficialStormTimeline.kt'], []),
    'quake': (['earthquake/TaiwanEewScope.kt','earthquake/SeismicWarningNarration.kt','earthquake/SeismicLiveMapPage.kt'], []),
    'radar': (['maps/NlscTerrain.kt','ui/RadarHistoryDialog.kt'], []),
    'alarm_reliability': (['alarm/WeatherAlarmAudioService.kt','alarm/R10AlarmDesign.kt','alarm/SamsungTtsSelector.kt'], []),
    'super_island': (['notifications/XiaomiFocusBridge.kt','notifications/IslandEventPublisher.kt'], []),
    'ota_ui': (['update/AppUpdater.kt','update/AppUpdatePage.kt','next/NextOtaPresentation.kt'], []),
    'apple_resource_audit': ([], []),
    'ios27_design_system_audit': ([], []),
    'swiftui27_article_audit': ([], []),
    'android_build': ([], ['app/build.gradle.kts','../.github/workflows/build-apk.yml']),
    'device_validation': ([], []),
    'signed_ota': (['update/AppUpdater.kt'], ['../.github/workflows/build-apk.yml']),
}

EXTERNAL = {
    'hi_fi_audio': '音質需耳機/喇叭測試，波形分析不能代替主觀品質',
    'icons_typography': '須不同螢幕與字級的真機對照',
    'haptics': '觸覺須 Samsung／小米實機驗證',
    'typhoon': '須有可驗證 CWA 真實資料及錯誤/過期測試',
    'quake': '實時通知須確認來源、到達率與臺灣離島篩選',
    'radar': '須以官方地圖/雷達資料和無網路情境測試',
    'alarm_reliability': '鎖屏、重開機、AM/PM、Spotify 授權與播放路徑須實機驗證',
    'super_island': 'HyperOS 焦點通知及島面由小米系統核准，App 不能強制控制',
    'apple_resource_audit': '需逐頁比對可取得的 Apple HIG 公開規格',
    'ios27_design_system_audit': '需提供使用者指定的 Design System 來源與相容性記錄',
    'swiftui27_article_audit': '需提供指定文章及子連結之可核對來源',
    'android_build': '須 GitHub Actions 針對此 commit 完成乾淨建置',
    'device_validation': '必須獲得 Samsung／小米／Android 16 真機驗收紀錄',
    'signed_ota': '正式版必須在原機由前版覆蓋安裝、資料保留、SHA 與簽章一致',
}
SCENES = ('sunny','drizzle','light_rain','moderate_rain','heavy_rain',
          'thunderstorm','severe_thunderstorm','haze','fog','dense_fog',
          'dust_storm','light_snow','moderate_snow','heavy_snow')


def inspect_audio(path:Path):
    try:
        with wave.open(str(path),'rb') as wf:
            channels=wf.getnchannels();depth=wf.getsampwidth();rate=wf.getframerate();frames=wf.getnframes()
            # Boundary and clipping checks are numerical, not an aural quality verdict.
            data=wf.readframes(frames)
            if channels!=1 or depth!=2 or not 16000<=rate<=48000 or frames<rate*8:
                return {'ok':False,'issue':'PCM format/duration does not meet source contract'}
            samples=memoryview(data).cast('h')
            if len(samples)<2:return {'ok':False,'issue':'missing samples'}
            clips=sum(1 for v in samples if abs(v)>=32767)
            discontinuity=abs(int(samples[0])-int(samples[-1]))
            result={'ok':clips==0 and discontinuity<=1,'rate':rate,'channels':channels,
                    'duration_sec':round(frames/rate,2),'clipped_samples':clips,
                    'loop_endpoint_delta':discontinuity,
                    'sha256':hashlib.sha256(data).hexdigest()[:16]}
            return result
    except (OSError,EOFError,wave.Error,ValueError) as ex:
        return {'ok':False,'issue':str(ex)[:250]}


def audit(root:Path=ROOT):
    gate_path=root/'tools/next_release_gate.json'
    gate=json.loads(gate_path.read_text(encoding='utf-8'))
    requirements=gate.get('requirements',[])
    # Fail *this audit* if the real release contract has silently changed.
    ids=[x.get('id') for x in requirements]
    if len(ids)!=22 or set(ids)!=set(FEATURES):
        raise RuntimeError('22-item release contract changed; do not pretend coverage')
    rows=[]
    kotlin=root/'app/src/main/java/io/atmos/weather'
    for item in requirements:
        fid=item['id']; kotlin_files,extra_files=FEATURES[fid]
        file_paths=[kotlin/x for x in kotlin_files]+[root/x for x in extra_files]
        missing=[str(p.relative_to(root)) for p in file_paths if not p.is_file()]
        rows.append({'id':fid,'name':item['name'],'release_gate_status':item['status'],
                     'source_files_present':not missing and bool(file_paths),
                     'missing_source_files':missing,
                     'requires_external_verification':EXTERNAL.get(fid,'需要實機操作、邊界情境和品質驗收'),
                     'verified_by_this_audit':False})
    audio={}
    for group in ('ambience','music/dawn','music/rain'):
        audio[group]={}
        for scene in SCENES:
            p=root/'app/src/main/assets/next_weather'/group/(scene+'.wav')
            audio[group][scene]=inspect_audio(p) if p.exists() else {'ok':False,'issue':'missing asset'}
    result={
        'title':'thinn-khì NEXT 22 source audit — NOT a release approval',
        'generated_at_utc':datetime.now(timezone.utc).isoformat(),
        'release_verified_count':sum(row['release_gate_status']=='verified' for row in rows),
        'release_requirement_count':len(rows),
        'full_release_declared':bool(gate.get('verified_full_release',False)),
        'source_file_groups_present':sum(row['source_files_present'] for row in rows),
        'wave_assets_count':sum(len(v) for v in audio.values()),
        'audio_assets_numerically_ok':sum(x['ok'] for v in audio.values() for x in v.values()),
        'audio_assets_failed':[
            {'group':g,'scene':s,'problem':r.get('issue','clipping or loop boundary')}
            for g,v in audio.items() for s,r in v.items() if not r['ok']
        ],
        'rows':rows,
        'audio':audio,
        'conclusion':'No release approval. Source coverage does not substitute device/OEM/backend tests.'
    }
    return result


def render_markdown(result):
    lines=['# thinn-khì NEXT｜22 項單次整合查核','',
           f"**正式已驗收：{result['release_verified_count']} / {result['release_requirement_count']}**",
           f"**音檔數值檢測：{result['audio_assets_numerically_ok']} / {result['wave_assets_count']}**",
           '**結論：靜態原始碼查核不是 22 項正式驗收。不能據此發布 OTA。**','',
           '| 項目 | 驗收狀態 | 原始碼證據 | 尚需補充 |','|---|---|---|---|']
    for row in result['rows']:
        present='存在' if row['source_files_present'] else ('缺檔' if row['missing_source_files'] else '無法僅由程式檔驗證')
        lines.append(f"| {row['id']} | {row['release_gate_status']} | {present} | {row['requires_external_verification']} |")
    if result['audio_assets_failed']:
        lines.extend(['','## 音檔數值檢查未通過'])
        for issue in result['audio_assets_failed'][:25]:
            lines.append(f"- {issue['group']}/{issue['scene']}: {issue['problem']}")
    lines.extend(['','## 不可變更的正式發布條件','',
                  '- 不能自行改寫任何 `blocked/partial` 為 `verified`。',
                  '- 須以真實測試紀錄支持每個 verified 狀態。',
                  '- 完成手機覆蓋安裝、相同簽章、版本遞增及完整 22 項後才執行 `publish_release=true`。',
                  '- CI 成功只證明建置／測試關卡，不能代替實體裝置與外部平台授權。',''])
    return '\n'.join(lines)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out-dir',type=Path,default=Path.cwd())
    args=parser.parse_args()
    out=args.out_dir;out.mkdir(parents=True,exist_ok=True)
    r=audit()
    (out/'next22_source_audit.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (out/'next22_source_audit.md').write_text(render_markdown(r),encoding='utf-8')
    print(f"完整驗收 {r['release_verified_count']}/22；靜態檔案證據 {r['source_file_groups_present']}/22；波形檢測 {r['audio_assets_numerically_ok']}/42。")
    print('報告：',out/'next22_source_audit.md')
    if r['audio_assets_failed']:
        print('存在音檔數值問題：',len(r['audio_assets_failed']))
    # A source-audit tool returns nonzero for missing structural prerequisites,
    # but never returns a *release success* conclusion.
    structural=[x for x in r['rows'] if x['missing_source_files']]
    return 1 if structural or r['audio_assets_failed'] else 0

if __name__=='__main__':
    sys.exit(main())

