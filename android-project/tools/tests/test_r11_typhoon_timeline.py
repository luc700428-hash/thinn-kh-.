"""Static assertions; CI must still compile app and exercise tests/device."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
class R11UITests(unittest.TestCase):
    def test_both_periods_visible_and_centered(self):
        s=(JAVA/'ui/IOSDrumPicker.kt').read_text()
        self.assertIn('"AM" else "PM"',s)
        self.assertIn('"AM 上午或 PM 下午",false',s)
        self.assertIn('center+(i-3)*h+(offset*h).roundToInt()',s)
        self.assertNotIn('updateValue(1-currentValue)',s)
    def test_official_only_track_and_taiwan_filter(self):
        s=(JAVA/'typhoon/OfficialStormTimeline.kt').read_text()
        for token in ('OfficialTyphoonTrack.extract','verifiedForecastSegments','isMovingTowardTaiwan','closest <= 900.0','closest < first-75.0'):
            self.assertIn(token,s)
    def test_scrubbing_visual_without_material_widgets(self):
        s=(JAVA/'typhoon/StormTimelineOverlay.kt').read_text()
        self.assertIn('detectDragGestures',s)
        self.assertIn('BasicText',s)
        self.assertIn('尚未提供',s)
        self.assertNotIn('material3',s)
    def test_no_auto_popup_and_checks_at_20min(self):
        s=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('TaiwanStormRelevance.isMovingTowardTaiwan',s)
        self.assertNotIn('approachingStormPopupOpen',s)
        self.assertIn('if (officialStormActive || verifiedTyphoonWarning!=null)',s)
        self.assertIn('delay(20*60_000L)',s)
if __name__=='__main__':unittest.main()
