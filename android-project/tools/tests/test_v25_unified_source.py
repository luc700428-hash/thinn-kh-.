"""V25 source wiring checks; device testing remains separate."""
import unittest
from pathlib import Path
SRC=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class UnifiedWiringTests(unittest.TestCase):
    def test_real_home_weather_hero_is_wired(self):
        h=(SRC/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('V25HomeHero(',h)
        self.assertIn('temperature=snapshot.temperatureC.roundToInt()',h)
        hero=(SRC/'next/V25HomeHero.kt').read_text()
        self.assertIn('reduce_motion',hero)
        self.assertIn('reduce_transparency',hero)
    def test_fixed_am_pm_and_no_blind_toggle(self):
        a=(SRC/'ui/IOSDrumPicker.kt').read_text()
        self.assertIn('IOSDrumColumn(h/12,2',a)
        self.assertIn('"AM" else "PM"',a)
        self.assertIn('"AM 上午或 PM 下午",false',a)
        self.assertNotIn('updateValue(1-currentValue)',a)
    def test_global_glimmer_obeys_accessibility(self):
        src=(SRC/'next/NextOpticalSurface.kt').read_text()
        self.assertIn('if(reducedMotion || reduceTransparency || calm)',src)
        self.assertIn('opticalPhase',src)
    def test_taiwan_voice_used_in_both_earthquake_speakers(self):
        for f in ('SeismicSpeech.kt','SeismicTrainingVoice.kt'):
            self.assertIn('V25TaiwanVoice.configure(',
                          (SRC/'earthquake'/f).read_text())
    def test_source_aligned_storm_and_weatherlab(self):
        s=(SRC/'typhoon/TyphoonCenterPage.kt').read_text()
        self.assertIn('sameStormConfirmed',s)
        self.assertIn('V25StormComparison.compare(',s)
        self.assertIn('rememberTaiwanVectorAtlas()',s)
    def test_widget_mood_from_actual_weather_and_radar_reload(self):
        self.assertIn('OpticalWidgetData.read(',
                      (SRC/'widgets/AtmosWidgets.kt').read_text())
        self.assertIn('NativeRadarViewer(latitude,longitude,onClose)',(SRC/'ui/RadarHistoryDialog.kt').read_text())
        self.assertIn('GlassIconButton({attempt++})',(SRC/'radar/NativeRadarViewer.kt').read_text())
    def test_manual_official_ota_retain_signed_upgrade_checks(self):
        workflow=(SRC.parents[7]/'.github/workflows/build-apk.yml').read_text()
        self.assertIn('Publish official OTA GitHub Release',workflow)
        self.assertIn('Official APK signature differs',workflow)
        self.assertNotIn('publish_maintenance:',workflow)
if __name__=='__main__':unittest.main()
