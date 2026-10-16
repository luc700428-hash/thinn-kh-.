"""Structural regression tests. Compiled Android & device tests are separate gates."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'

class VisualAlarmTyphoonV23R1(unittest.TestCase):
    def test_alarm_ringtone_selection_persists_and_plays(self):
        editor=(JAVA/'alarm/WeatherAlarmPage.kt').read_text()
        alarm=(JAVA/'alarm/WeatherAlarm.kt').read_text()
        ring=(JAVA/'alarm/WeatherAlarmRingActivity.kt').read_text()
        audio=(JAVA/'alarm/WeatherAlarmAudioService.kt').read_text()
        for token in ('rememberLauncherForActivityResult','ACTION_RINGTONE_PICKER','ringtoneUri'):
            self.assertIn(token,editor)
        self.assertIn('put("ringtoneUri"',alarm)
        self.assertIn('alarm.ringtoneUri',audio)
        self.assertIn('detectHorizontalDragGestures',ring)
        self.assertIn('滑動停止鬧鐘',ring)
        self.assertIn('無法滑動？點此停止鬧鐘',ring)
    def test_warning_advice_checks_temperature(self):
        advice=(JAVA/'domain/model/WeatherAdviceEngine.kt').read_text()
        self.assertIn('weather.temperatureC <= 6.0',advice)
        self.assertIn('it.temperatureC <= 3.0',advice)
    def test_projection_is_isotropic_and_islands_included(self):
        geo=(JAVA/'earthquake/SeismicGeo.kt').read_text()
        screen=(JAVA/'earthquake/SeismicLiveMapPage.kt').read_text()
        for token in ('kmPerPixel','screenX','screenY','smallerIslands'):
            self.assertIn(token,geo)
        self.assertIn('SeismicGeo.screenX(',screen)
        self.assertIn('SeismicGeo.screenY(',screen)
        self.assertIn('SeismicGeo.kmPerPixel(',screen)
        self.assertNotIn('SeismicGeo.x(',screen)
        self.assertNotIn('SeismicGeo.y(',screen)
        self.assertIn('minLines=2,maxLines=2',screen)
    def test_weather_metric_tiles_share_adaptive_height(self):
        ui=(JAVA/'presentation/home/WeatherGauges.kt').read_text()
        self.assertIn('height(136.dp)',ui)
        self.assertIn('modifier.fillMaxSize()',ui)
    def test_typhoon_discoverable_from_weather(self):
        home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('颱風動向與官方路徑',home)
        self.assertIn('typhoonPageOpen = true',home)
        self.assertIn('state.cwaWarnings.filter',home)

if __name__=='__main__':unittest.main()
