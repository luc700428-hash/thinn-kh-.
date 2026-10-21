"""Stage 3 alarm source regression. CI JVM tests validate the actual algorithms."""
from pathlib import Path
import unittest
BASE=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather/alarm'
TEST=Path(__file__).resolve().parents[2]/'app/src/test/java/io/atmos/weather/alarm'
class Stage3AlarmReliability(unittest.TestCase):
    def test_editor_preserves_disabled_state(self):
        text=(BASE/'R10AlarmDesign.kt').read_text()
        self.assertIn('initial?.enabled ?: true',text)
        self.assertNotIn('repeat,true,voice,snooze,uri',text)
        self.assertIn('V25AlarmMeridiem.hour24(v,',text)
        self.assertIn('V25AlarmMeridiem.hour24(hour%12,part)',text)
    def test_mutations_only_reschedule_changed_ids(self):
        page=(BASE/'WeatherAlarmPage.kt').read_text()
        self.assertIn('WeatherAlarmMutation.changedIds(stored,corrected)',page)
        self.assertIn('WeatherAlarmMutation.preserveBackgroundChanges(stored,alarms,value)',page)
        self.assertIn('changedIds.forEach { id ->',page)
        self.assertTrue((TEST/'WeatherAlarmMutationTest.kt').exists())
        self.assertTrue((TEST/'V25AlarmMeridiemTest.kt').exists())
if __name__=='__main__':unittest.main()
