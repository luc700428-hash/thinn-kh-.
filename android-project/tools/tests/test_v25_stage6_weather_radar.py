"""V25 stage6 source contract; physical tests of map layers are still required."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'app/src/main/java/io/atmos/weather'
class WeatherRadarNoSyntheticReadings(unittest.TestCase):
    def test_temperature_gaps_are_not_fabricated(self):
        src=(BASE/'presentation/home/InteractiveForecast.kt').read_text(encoding='utf-8')
        self.assertNotIn('temps.firstOrNull() ?: 20.0',src)
        self.assertIn('h.temperatureC?.takeIf { it.isFinite() }',src)
        self.assertIn('if (point==null)',src)
        self.assertIn('if(hasLine) drawPath(path',src)
        self.assertIn('coords[selected]?.let { at ->',src)
        self.assertIn('氣溫預報，曲線未繪製',src)
    def test_radar_retrieval_does_not_imply_observation_now(self):
        src=(BASE/'ui/RadarThreeHourCard.kt').read_text(encoding='utf-8')
        self.assertIn('observation.modifiedAt?.let',src)
        self.assertIn('官方圖檔更新時間未提供',src)
        self.assertIn('downloadedMinutes',src)
        self.assertIn('weather.fetchedAt)',src)
        self.assertNotIn('val age=(System.currentTimeMillis()-observation.fetchedAt)',src)
        self.assertIn('timeFormatter.format(hour.at)',src)
        self.assertIn('if(probability==null) "—"',src)
    def test_wind_compass_safe_for_negative_inputs(self):
        f=(BASE/'presentation/home/WeatherWindDirection.kt').read_text()
        h=(BASE/'presentation/home/WeatherGauges.kt').read_text()
        self.assertIn('Math.floorMod(degrees,360)',f)
        self.assertIn('WeatherWindDirection.label(degrees)',h)
    def test_retains_map_seismic_and_update_safety(self):
        self.assertTrue((BASE/'maps/NlscTerrain.kt').exists())
        self.assertTrue((BASE/'earthquake/ExpTechSeismicService.kt').exists())
        self.assertTrue((BASE/'update/AppUpdater.kt').exists())
        self.assertFalse((BASE/'visual/FilamentLandmark.kt').exists())
if __name__=='__main__': unittest.main()
