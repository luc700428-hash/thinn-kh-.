"""Source routing and original PCM signal checks; not a lock-screen device test."""
from pathlib import Path
import math,struct,unittest,wave,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
class SeismicDeliveryV27(unittest.TestCase):
    def test_both_transports_use_same_threshold_and_protected_activity(self):
        service=(JAVA/'earthquake/ExpTechSeismicService.kt').read_text()
        push=(JAVA/'push/ThinnFirebaseMessagingService.kt').read_text()
        notifier=(JAVA/'earthquake/SeismicAlertNotifier.kt').read_text()
        self.assertIn('SeismicAlertNotifier.deliver(this,quake,scenario)',service)
        self.assertIn('SeismicAlertNotifier.deliver(this,quake,',push)
        self.assertIn('SeismicAlertThreshold.accepts(ctx,e)',notifier)
        self.assertIn('SeismicAlertDelivery.pendingIntent(ctx,ticket)',notifier)
        self.assertIn('SeismicForegroundAlertHost()', (JAVA/'MainActivity.kt').read_text())
        activity=(JAVA/'earthquake/SeismicAlertActivity.kt').read_text()
        self.assertLess(activity.index('SeismicAlertDelivery.resolve(this,intent'),activity.index('setShowWhenLocked(allowed)'))
        android='{http://schemas.android.com/apk/res/android}'
        manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        alert=next(x for x in manifest.findall('application/activity') if x.get(android+'name')=='.earthquake.SeismicAlertActivity')
        self.assertEqual(alert.get(android+'exported'),'false')
    def test_report_is_three_equal_pitch_pulses_with_headroom_and_no_music(self):
        with wave.open(str(ROOT/'app/src/main/res/raw/thinn_eq_report_signal.wav')) as f:
            self.assertEqual((f.getnchannels(),f.getsampwidth(),f.getframerate()),(1,2,44100))
            rate=f.getframerate();samples=struct.unpack('<'+'h'*f.getnframes(),f.readframes(f.getnframes()))
        self.assertAlmostEqual(len(samples)/rate,1.35,places=3)
        self.assertLessEqual(max(abs(v) for v in samples),int(32767*.29))
        blocks=[math.sqrt(sum(v*v for v in samples[i:i+220])/220) for i in range(0,len(samples)-220,220)]
        starts=[i for i,v in enumerate(blocks) if v>150 and (i==0 or blocks[i-1]<=150)]
        self.assertEqual(len(starts),3)
        for start in (.16,.53,.90):
            tone=samples[round(start*rate):round((start+.1)*rate)]
            crossings=sum(a<=0<b for a,b in zip(tone,tone[1:]))
            self.assertAlmostEqual(crossings/(len(tone)/rate),1000,delta=12)
        policy=(JAVA/'earthquake/SeismicSettings.kt').read_text()
        self.assertIn('R.raw.thinn_eq_report_signal',policy)
        self.assertIn('/raw/${ctx.resources.getResourceEntryName(s.sound)}',policy)
        self.assertIn('legacy?.importance',policy)
if __name__=='__main__':unittest.main()
