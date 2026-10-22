"""Static assertions; CI must still compile app and exercise tests/device."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
class R11UITests(unittest.TestCase):
    def test_both_periods_visible_and_centered(self):
        s=(JAVA/'alarm/R10AlarmDesign.kt').read_text()
        # All time columns share the same scroll physics; AM/PM has two finite rows.
        self.assertIn('Wheel(value,2,',s)
        self.assertIn('"上午" else "下午"',s)
        self.assertIn('IOSWheelSelection.rowValue(',s)
        self.assertIn('IOSWheelSelection.predictedSteps(',s)
        self.assertLess(s.index('Wheel(hour%12,12,'),s.index('Wheel(minute,60,'))
        self.assertLess(s.index('Wheel(minute,60,'),s.index('MeridiemWheel(if(hour<12) 0 else 1'))
        core=(JAVA/'alarm/IOSWheelSelection.kt').read_text()
        self.assertIn('index.takeIf{it in 0 until count}',core)
        self.assertIn('proposed.coerceIn((value-count+1)*stepPx,value*stepPx)',core)
        conversion=(JAVA/'alarm/V25AlarmMeridiem.kt').read_text()
        self.assertIn('return hour12 + meridiem * 12',conversion)
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
