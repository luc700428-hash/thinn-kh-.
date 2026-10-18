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
        self.assertIn('removePrefix("地震速報，第三方資料。")',train)
    def test_wheel_has_true_inertia_and_only_one_ampm_label(self):
        s=self.read('alarm/R10AlarmDesign.kt')
        self.assertIn('VelocityTracker()',s)
        self.assertIn('animateDecay(',s)
        self.assertIn('exponentialDecay(',s)
        self.assertIn('animateTo(',s)
        self.assertNotIn('while(acc>36f)',s)
        self.assertIn('Text(if(hour<12) "上午" else "下午"',s)
        self.assertNotIn('Wheel(if(hour>=12)',s)
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
        editor=self.read('alarm/R10AlarmDesign.kt')
        self.assertNotIn('響鈴會先播放原創音樂，接著',editor)
    def test_shared_button_haptics(self):
        s=self.read('ui/GlassControls.kt')
        self.assertIn('HapticEngine.Event.TAP',s)
        self.assertIn('spring(dampingRatio=.76f,stiffness=430f)',s)

if __name__=='__main__':unittest.main()
