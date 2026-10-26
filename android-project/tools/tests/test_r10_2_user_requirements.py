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
        s=self.read('ui/IOSDrumPicker.kt')
        self.assertIn('VelocityTracker()',s)
        self.assertIn('DrumWheelRules.move(',s)
        self.assertIn('IOSClockDrums(',self.read('alarm/R10AlarmDesign.kt'))
        self.assertIn('IOSClockDrums(',self.read('ui/GlassClockDial.kt'))
        self.assertNotIn('private fun Wheel(',self.read('alarm/R10AlarmDesign.kt'))
    def test_meridiem_fixed_rows_and_synchronous_commit(self):
        s=self.read('ui/IOSDrumPicker.kt')
        self.assertIn('h/12,2',s)
        self.assertIn('"AM" else "PM"',s)
        self.assertIn('"AM 上午或 PM 下午",false',s)
        self.assertIn('report(next)',s)
        self.assertIn('DisposableEffect(count)',s)

    def test_hour_minute_snap_does_not_wait_for_fling(self):
        s=self.read('ui/IOSDrumPicker.kt')
        self.assertIn('onVerticalDrag={c,dy->c.consume()',s)
        self.assertIn('move(dy)',s)
        self.assertIn('selected=next;sent=next;report(next)',s)
        self.assertIn('Animatable(offset).animateTo(0f',s)

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
        import struct
        for label in ('dawn','rain','wood','water','fire','metal'):
            for mood in ('','_clear','_rain'):
                path=RAW/f'thinn_alarm_{label}{mood}.ogg'
                self.assertTrue(path.is_file(),str(path))
                data=path.read_bytes()
                self.assertEqual(data[:4],b'OggS',str(path))
                ident=data.index(b'\x01vorbis')
                rate=struct.unpack_from('<I',data,ident+12)[0]
                self.assertEqual(rate,44100,str(path))
                position=0;last_frame=0
                while position<len(data):
                    self.assertEqual(data[position:position+4],b'OggS',str(path))
                    frame=struct.unpack_from('<Q',data,position+6)[0]
                    if frame != (1<<64)-1:last_frame=frame
                    count=data[position+26]
                    position+=27+count+sum(data[position+27:position+27+count])
                self.assertEqual(position,len(data),str(path))
                self.assertGreater(last_frame/rate,8,str(path))
    def test_full_screen_alarm_rebuilt_without_old_description(self):
        s=self.read('alarm/WeatherAlarmRingActivity.kt')
        for token in ('稍後提醒','滑動來停止','detectHorizontalDragGestures','LocalHapticFeedback','92.sp'):
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
        self.assertIn('spring(dampingRatio=response.damping,stiffness=response.stiffness)',s)
        self.assertIn('indication=null',s)
        motion=self.read('ui/AppleMotion.kt')
        self.assertIn('const val springDamping: Float = 0.72f',motion)
        self.assertIn('const val springStiffness: Float = 190f',motion)
        self.assertIn('const val pressedScale: Float = 0.96f',motion)
        self.assertIn('const val pressedAlpha: Float = 0.92f',motion)

if __name__=='__main__':unittest.main()
