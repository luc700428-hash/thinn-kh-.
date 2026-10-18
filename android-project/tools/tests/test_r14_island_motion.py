"""Scope, safe vendor API usage, vector integrity (not a device/UI verification)."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
J=ROOT/'app/src/main/java/io/atmos/weather/notifications'
VECTOR=ROOT/'app/src/main/res/drawable'

class IslandMotionContract(unittest.TestCase):
    def test_original_vector_assets_are_valid_and_pairwise(self):
        names=('alarm','sun','cloud','rain','snow','timer','quake','typhoon','thunder',
               'hot','cold','air','uv','note','calendar','generic')
        for n in names:
            for phase in (0,1):
                p=VECTOR/f'island_{n}_{phase}.xml'
                self.assertTrue(p.exists(),str(p))
                root=ET.parse(p).getroot()
                self.assertEqual(root.tag,'vector')
                self.assertIn('viewportWidth',str(root.attrib))
                self.assertGreaterEqual(len(list(root.iter())),2)
                self.assertNotIn('material',p.read_text().lower())

    def test_island_uses_real_vendor_pics_not_app_launcher(self):
        s=(J/'XiaomiFocusBridge.kt').read_text()
        self.assertIn('Icon.createWithResource(context,vectorId)',s)
        self.assertIn('IslandIconClassifier.choose(',s)
        self.assertIn('IslandIconMotionPolicy.next(',s)
        self.assertIn('miui.focus.pic_alert',s)
        self.assertIn('tickerPic',s)
        self.assertIn('aodPic',s)
        self.assertIn('islandFirstFloat", isWarning || kind == Kind.ALARM_RINGING',s)
        self.assertNotIn('R.mipmap.ic_launcher',s)

    def test_no_oem_hacks_or_infinite_frame_ticker(self):
        s=(J/'XiaomiFocusBridge.kt').read_text()
        motion=(J/'IslandIconMotion.kt').read_text()
        for forbidden in ('android.os.SystemProperties','WindowManager.addView','while(true)','Handler.postDelayed','Timer().schedule'):
            self.assertNotIn(forbidden,s+motion)
        self.assertIn('ANIMATOR_DURATION_SCALE',s)
        self.assertIn('isPowerSaveMode',s)
        self.assertIn('MIN_FRAME_SPACING_MS = 12_000L',motion)
        self.assertIn('priority.rank <= active.priority.rank',(J/'IslandPriorityPolicy.kt').read_text())

    def test_alarm_fullscreen_and_stop_snooze_untouched(self):
        s=(ROOT/'app/src/main/java/io/atmos/weather/alarm/WeatherAlarmAudioService.kt').read_text()
        self.assertIn('if (!islandPreferred) b.setFullScreenIntent',s)
        self.assertIn('ACTION_STOP',s)
        self.assertIn('ACTION_SNOOZE',s)

    def test_fallback_never_drops_android_notification(self):
        s=(J/'WeatherNotificationDispatcher.kt').read_text()
        self.assertIn('manager.notify(id, notification)',s)
        self.assertIn('notification.extras.remove("miui.focus.param")',s)

if __name__=='__main__':unittest.main()
