"""Stage 4 source-level guards. CI JVM tests and field validation remain mandatory."""
from pathlib import Path
import unittest

SRC=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather/earthquake'

class Stage4SeismicSafety(unittest.TestCase):
    def test_live_list_only_accepts_fresh_non_simulated_events(self):
        src=(SRC/'ExpTechSeismicService.kt').read_text(encoding='utf-8')
        part=src.split('private fun handleFrame(type:String,raw:String)',1)[1]
        assert part.index('if (!ExpTechRealtimeParser.isFresh(quake,time)) return@forEach') < part.index('state.copy(incident=quake, recentIncidents=')
        self.assertIn('if (!TaiwanEewScope.relevant(quake)) return@forEach',part)
        self.assertIn('if (quake.cancelled)',part)
        self.assertIn('quake.serial>=active.serial',part)
        self.assertIn('recentIncidents=state.recentIncidents.filterNot { it.id==quake.id }',part)
    def test_station_is_ranked_globally_and_requires_new_frames(self):
        src=(SRC/'StationDetectionGate.kt').read_text(encoding='utf-8')
        self.assertIn('priority.firstNotNullOfOrNull',src)
        self.assertIn('compareByDescending<Trigger> { it.value }',src)
        self.assertIn('now-lastSampleMs in 1..12_000L',src)
        self.assertIn('streak<3',src)
        self.assertIn('lastSentMs<120_000L',src)
    def test_simulator_remains_local_only(self):
        src=(SRC/'SeismicSimulation.kt').read_text(encoding='utf-8')
        self.assertIn('test=true',src)
        self.assertNotIn('FirebaseMessaging',src)

if __name__=='__main__':unittest.main()
