"""R10.2 static safety / feature contracts; passing does not prove Android build or animations."""
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[2]
PKG=ROOT/'app/src/main/java/io/atmos/weather'
RAW=ROOT/'app/src/main/res/raw'

class R102UserRequirements(unittest.TestCase):
    def read(self,p):return (PKG/p).read_text()
    def test_real_warning_counties_source_verified(self):
        n=self.read('earthquake/SeismicWarningNarration.kt')
        self.assertIn('SeismicWarningCounties.fromProvider(raw)',n)
        self.assertIn('您所在的',n)
        self.assertIn('其他縣市預估震度',n)
        self.assertIn('沒有來源提供的預估震度',n)
        self.assertNotIn('epicentralKm(',n)
        self.assertNotIn('random(',n)
        self.assertIn('SeismicWarningNarration.build(',self.read('earthquake/SeismicSpeech.kt'))
    def test_simulation_does_not_play_live_alert_or_tts(self):
        ui=self.read('earthquake/SeismicWarningOverlay.kt')
        self.assertIn('if(!replay && !simulation) SeismicContinuousWarning.start',ui)
        self.assertIn('if(!replay && !simulation) SeismicContinuousWarning.stop',ui)
        self.assertIn('if(!replay && !simulation && SeismicSpeech.enabled',ui)
        self.assertIn('SeismicTrainingVoice(context,incident',ui)
        train=self.read('earthquake/SeismicTrainingVoice.kt')
        self.assertIn('模擬演練',train)
        self.assertIn('removePrefix("地震速報！慎防強烈搖晃！趴下、掩護、穩住！")',train)
    def test_wheel_has_fast_settle_and_reliable_meridiem(self):
        s=self.read('alarm/R10AlarmDesign.kt')
        self.assertIn('VelocityTracker()',s)
        self.assertNotIn('animateDecay(',s)
        self.assertNotIn('exponentialDecay(',s)
        self.assertIn('V25AlarmMeridiem.fromDrag(',s)
        # New shared iOS-style wheel settles with a bounded spring, not a fixed 140ms tween.
        self.assertIn('Animatable(visualStart).animateTo(0f,spring(',s)
        self.assertIn('stiffness=io.atmos.weather.ui.AppleMotion.springStiffness',s)
        self.assertIn('animateTo(',s)
        self.assertNotIn('while(acc>36f)',s)
        # The alarm editor and the other time settings share one renderer.
        self.assertIn('AppleAlarmTimeSelector(hour=hour,minute=minute,',s)
        self.assertIn('MeridiemWheel(if(safeHour<12) 0 else 1',s)
        self.assertIn('io.atmos.weather.alarm.AppleAlarmTimeSelector(',
                      self.read('ui/GlassClockDial.kt'))
        self.assertIn('Text("上午"',s)
        self.assertIn('Text("下午"',s)
        self.assertIn('onDragEnd={',s)
        self.assertNotIn('val expected=offsetPx+velocity*.065f',s)
        self.assertIn('val coastPx=if(reduceMotion) 0f',s)
        self.assertNotIn('updateValue(1-currentValue)',s)
        self.assertIn('liveUpdate(((liveValue-nearest)%count+count)%count)',s)
        self.assertIn('offsetPx-=nearest*stepPx',s)
        self.assertIn('val selectedDetent=',s)
        self.assertIn('LaunchedEffect(selectedDetent)',s)
        self.assertNotIn('HapticEngine.Event.DRAG',s)
        self.assertIn('Layout(content = {',s)
        self.assertIn('center+(index-3)*rowHeight+fraction.roundToInt()',s)
        self.assertNotIn('.clickable { hour=(hour+12)%24 }',s)
    def test_meridiem_fixed_rows_and_synchronous_commit(self):
        s=self.read('alarm/R10AlarmDesign.kt')
        meridiem=s.split('private fun MeridiemWheel(',1)[1].split('private fun Wheel(',1)[0]
        self.assertIn('V25AlarmMeridiem.fromDrag(',meridiem)
        self.assertIn('latestUpdate(next)',meridiem)
        self.assertIn('Text("上午"',meridiem)
        self.assertIn('Text("下午"',meridiem)
        self.assertLess(meridiem.index('Text("上午"'),meridiem.index('Text("下午"'))
        self.assertNotIn('updateValue(1-currentValue)',meridiem)
        self.assertNotIn('VelocityTracker()',meridiem)
        self.assertIn('animateDpAsState(',meridiem)
        self.assertIn('reduce_motion',meridiem)

    def test_hour_minute_snap_does_not_wait_for_fling(self):
        s=self.read('alarm/R10AlarmDesign.kt')
        wheel=s.split('private fun Wheel(',1)[1].split('fun R10AlarmEditor(',1)[0]
        self.assertIn('coerceIn(-stepPx*2f,stepPx*2f)',wheel)
        # The value still commits synchronously before visual spring settlement.
        self.assertIn('Animatable(visualStart).animateTo(0f,spring(',wheel)
        self.assertIn('dampingRatio=io.atmos.weather.ui.AppleMotion.springDamping',wheel)
        self.assertLess(wheel.index('liveUpdate(((liveValue-nearest)%count+count)%count)'),
                        wheel.index('movingJob=scope.launch {',wheel.index('onDragEnd={')))

    def test_air_quality_short_labels_are_honest(self):
        p=(PKG/'presentation/home/HomeWeatherBriefing.kt').read_text()
        self.assertIn('HomeWeatherFact("空氣品質"',p)
        self.assertIn('"AQI ${weather.usAqi}"',p)
        self.assertIn('美國標準模型推估，非臺灣環境部官方 AQI',p)
        self.assertNotIn('HomeWeatherFact("空氣品質模型"',p)
    def test_ringtone_import_with_persisted_permission(self):
        s=self.read('alarm/R10AlarmDesign.kt')
        self.assertIn('ActivityResultContracts.OpenDocument()',s)
        self.assertIn('takePersistableUriPermission',s)
        self.assertIn('arrayOf("audio/*")',s)
        self.assertIn('ToneArtwork(',s)
        self.assertIn('R10AlarmTones.resource',s)
    def test_six_tones_each_have_three_variants(self):
        for label in ('dawn','rain','wood','water','fire','metal'):
            for mood in ('','_clear','_rain'):
                data=RAW/f'thinn_alarm_{label}{mood}.ogg'
                self.assertTrue(data.is_file() and data.stat().st_size>20000,str(data))
    def test_full_screen_alarm_rebuilt_without_old_description(self):
        s=self.read('alarm/WeatherAlarmRingActivity.kt')
        for token in ('稍後提醒','滑動來停止','detectHorizontalDragGestures','LocalHapticFeedback','88.sp'):
            self.assertIn(token,s)
        self.assertNotIn('"起床天氣語音"',s)
        self.assertNotIn('"日常提醒"',s)
        self.assertNotIn('material3.Button',s)
        self.assertIn('setVolumeControlStream(AudioManager.STREAM_ALARM)',s)
        self.assertIn('AudioAttributes.USAGE_ALARM',self.read('alarm/R10AlarmDesign.kt'))
        editor=self.read('alarm/R10AlarmDesign.kt')
        self.assertNotIn('響鈴會先播放原創音樂，接著',editor)
    def test_shared_button_haptics(self):
        s=self.read('ui/GlassControls.kt')
        self.assertIn('HapticEngine.Event.TAP',s)
        # Shared buttons now follow approved UI motion tokens and remain ripple-free.
        self.assertIn('spring(dampingRatio=AppleMotion.springDamping,stiffness=AppleMotion.springStiffness)',s)
        self.assertIn('indication=null',s)
        motion=self.read('ui/AppleMotion.kt')
        self.assertIn('const val springDamping: Float = 0.72f',motion)
        self.assertIn('const val springStiffness: Float = 190f',motion)
        self.assertIn('const val pressedScale: Float = 0.94f',motion)
        self.assertIn('const val pressedAlpha: Float = 0.70f',motion)

if __name__=='__main__':unittest.main()
