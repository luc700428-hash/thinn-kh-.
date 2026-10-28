"""R9 source-level safety contracts (not an Android runtime/device test)."""
import pathlib, re, unittest

SRC = pathlib.Path(__file__).resolve().parents[2] / 'app/src/main/java/io/atmos/weather'

class R9Contracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.home = (SRC/'presentation/home/WeatherHomeScreen.kt').read_text()
        cls.map = (SRC/'earthquake/SeismicLiveMapPage.kt').read_text()
        cls.parser = (SRC/'earthquake/SeismicStationData.kt').read_text()
        cls.service = (SRC/'earthquake/ExpTechSeismicService.kt').read_text()
        cls.panel = (SRC/'ui/ThinnAtmospherePanel.kt').read_text()

    def test_home_panels_share_atmospheric_material(self):
        self.assertGreaterEqual(self.home.count('ThinnAtmospherePanel('), 2)
        self.assertIn('ThinnOpticalMaterial.surface', self.panel)

    def test_service_cards_adapt_to_text_and_keep_observation_status(self):
        self.assertIn('heightIn(min=72.dp)',self.map)
        self.assertIn('heightIn(min=122.dp)',self.map)
        self.assertIn('readingLabel',self.map)
        self.assertIn('SeismicMetricCard("有效測站"',self.map)

    def test_no_stale_readings_as_live(self):
        self.assertIn('上次觀測，非即時', self.map)
        self.assertIn('lastRtsAt > 0L', self.map)
        self.assertIn('if (stations.any { it.intensity != null || it.accelerationGal != null })', self.service)
        self.assertIn('val fresh = rtsHealth == SeismicFeedState.LIVE', self.map)

    def test_empty_frame_does_not_advance_freshness(self):
        handle = self.service.split('private fun handleFrame(', 1)[1].split('live.update { it.copy(lastEewAt=time) }',1)[0]
        self.assertIn('if (stations.any { it.intensity != null || it.accelerationGal != null })', handle)
        self.assertIn('stations.mapNotNull{it.observedAtMs}', handle)
        self.assertIn('StationFreshness.merge',handle)
        self.assertLess(handle.index('if (stations.any { it.intensity != null || it.accelerationGal != null })'), handle.index('lastRtsAt=if(newest!=null)'))

    def test_only_explicit_offline_state(self):
        self.assertIn('node.has("online")', self.parser)
        self.assertIn('StationAvailability.MAINTENANCE', self.parser)
        self.assertIn('StationAvailability.OFFLINE', self.parser)
        self.assertIn('station.availability == StationAvailability.OFFLINE', self.map)
        self.assertIn('if (offline)', self.map)

    def test_blue_flash_needs_fresh_frame(self):
        self.assertIn('stationFlash.value', self.map)
        self.assertIn('fresh && stationFlash.value', self.map)
        self.assertIn('stations.isNotEmpty() && fresh', self.map)

    def test_motion_accessibility(self):
        self.assertIn('reduce_motion',self.map)
        self.assertIn('reduce_transparency',self.panel)

if __name__=='__main__':
    unittest.main()
