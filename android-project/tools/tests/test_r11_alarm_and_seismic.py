"""R11 source-level tests; these do NOT substitute for Android compilation or device QA."""
from pathlib import Path
import re,unittest,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'app/src/main/java/io/atmos/weather'
RES=ROOT/'app/src/main/res'
class R11Checks(unittest.TestCase):
 def test_continuous_wheels_snap_and_one_ampm(self):
  x=(BASE/'alarm/R10AlarmDesign.kt').read_text()
  self.assertIn('rememberSnapFlingBehavior',x)
  self.assertIn('LazyColumn(',x)
  self.assertIn('detectVerticalDragGestures',x) # Only AM/PM picker uses one drag gesture; hour/minute remain fling wheels
  self.assertIn('MeridiemWheel(if(hour>=12)',x)
  self.assertIn('V25AlarmMeridiem.fromDrag',x)
  self.assertIn('Text("上午"',x)
  self.assertIn('Text("下午"',x)
  self.assertNotIn('"上午" else "下午"},',x)
 def test_alarm_has_snooze_and_swipe_stop(self):
  x=(BASE/'alarm/WeatherAlarmRingActivity.kt').read_text()
  for expected in ('稍後提醒','滑動來停止','detectHorizontalDragGestures','max*.76f','close(false)'):
   self.assertIn(expected,x)
 def test_audio_document_persistence(self):
  x=(BASE/'alarm/R10AlarmDesign.kt').read_text()
  for expected in ('ActivityResultContracts.OpenDocument','audio/*','takePersistableUriPermission','從手機選擇音樂'):
   self.assertIn(expected,x)
  self.assertIn('setDataSource(this@WeatherAlarmAudioService,uri)',(BASE/'alarm/WeatherAlarmAudioService.kt').read_text())
 def test_seismic_drill_voice_not_mixed_with_real_alert(self):
  s=(BASE/'earthquake/SeismicWarningOverlay.kt').read_text()
  self.assertIn('if(!replay && !simulation && SeismicSpeech.enabled(',s)
  self.assertIn('SeismicTrainingVoice(context,incident,',s)
 def test_verified_counties_local_priority(self):
  f=(BASE/'earthquake/SeismicVoiceFormatter.kt').read_text()
  self.assertLess(f.index('立即趴下'),f.index('$localLine'))
  self.assertIn('您所在的',f)
  self.assertIn('來源提供的最高預估震度',f)
  self.assertIn('其他有預估震度的縣市',f)
  s=(BASE/'earthquake/SeismicSpeech.kt').read_text()
  self.assertIn('SeismicWarningCounties.fromProvider',s)
 def test_custom_card_art(self):
  for name in ('dawn','rain','wood','water','fire','metal'):
   x=RES/'drawable-nodpi'/f'alarm_art_{name}.webp'
   self.assertGreater(x.stat().st_size,2500)
 def test_audio_variants_decode_as_vorbis(self):
  import shutil
  if not shutil.which('ffprobe'):self.skipTest('ffprobe not installed in this runner')
  for name in ('dawn','rain','wood','water','fire','metal'):
   for suffix in ('','_rain'):
    sound=RES/'raw'/f'thinn_alarm_{name}{suffix}.ogg'
    if name=='rain' and suffix=='':sound=RES/'raw'/'thinn_alarm_rain_clear.ogg'
    if name=='rain' and suffix=='_rain':sound=RES/'raw'/'thinn_alarm_rain.ogg'
    self.assertGreater(sound.stat().st_size,50_000)
    result=subprocess.run(['ffprobe','-v','error','-show_entries','stream=codec_name,duration','-of','default=nw=1',str(sound)],capture_output=True,text=True)
    self.assertEqual(0,result.returncode)
    self.assertIn('codec_name=vorbis',result.stdout)
if __name__=='__main__':unittest.main()
