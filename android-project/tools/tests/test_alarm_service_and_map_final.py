"""Regression coverage for user-visible fixed behaviours. Still requires device and Gradle CI."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT/'app/src/main/java/io/atmos/weather'

class AlarmServiceAndMapTests(unittest.TestCase):
    def test_service_owns_audio_even_if_lock_screen_suppresses_activity(self):
        src=(APP/'alarm/WeatherAlarmAudioService.kt').read_text()
        receiver=(APP/'alarm/WeatherAlarm.kt').read_text()
        self.assertIn('startForeground(',src)
        self.assertIn('AudioAttributes.USAGE_ALARM',src)
        self.assertIn('MediaPlayer()',src)
        self.assertIn('TextToSpeech(this,this)',src)
        self.assertIn('alarm.ringtoneUri',src)
        self.assertIn('WeatherAlarmAudioService::class.java',receiver)
        self.assertIn('startForegroundService',receiver)
    def test_stop_snooze_notification_have_accessibility_fallback(self):
        service=(APP/'alarm/WeatherAlarmAudioService.kt').read_text()
        screen=(APP/'alarm/WeatherAlarmRingActivity.kt').read_text()
        for token in ['ACTION_STOP','ACTION_SNOOZE','scheduleSnooze','stopAudio()']:
            self.assertIn(token,service)
        for token in ['detectHorizontalDragGestures','滑動來停止','無法滑動？輕觸這裡停止鬧鐘']:
            self.assertIn(token,screen)
        self.assertNotIn('MediaPlayer.create(',screen)
    def test_alarm_manifest_foreground_permissions(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        ET.fromstring(manifest)
        for token in ['.alarm.WeatherAlarmAudioService','FOREGROUND_SERVICE_MEDIA_PLAYBACK',
                      'foregroundServiceType="mediaPlayback"','SCHEDULE_EXACT_ALARM','USE_FULL_SCREEN_INTENT']:
            self.assertIn(token,manifest)
    def test_map_and_alert_use_same_geographic_projection(self):
        screen=(APP/'earthquake/SeismicLiveMapPage.kt').read_text()
        warning=(APP/'earthquake/SeismicWarningOverlay.kt').read_text()
        self.assertIn('SeismicGeo.screenX(',screen)
        self.assertIn('SeismicGeo.screenX(',warning)
        self.assertIn('SeismicGeo.screenY(',warning)
        self.assertNotIn('SeismicGeo.x(',warning)
    def test_taiwan_warm_weather_never_shows_freezing_card(self):
        screen=(APP/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('snapshot.temperatureC <= 6.0 && snapshot.condition',screen)
    def test_metric_cards_fixed_pair_dimensions(self):
        source=(APP/'presentation/home/WeatherGauges.kt').read_text()
        self.assertIn('Row(Modifier.fillMaxWidth().height(136.dp)',source)
        self.assertIn('modifier.fillMaxSize()',source)
    def test_typhoon_projection_equal_distance_and_no_fake_cones(self):
        viewport=(APP/'typhoon/TyphoonViewport.kt').read_text()
        center=(APP/'typhoon/TyphoonCenterPage.kt').read_text()
        self.assertIn('cos(Math.toRadians(centerLat))',viewport)
        self.assertIn('view.project(',center)
        self.assertIn('VerifiedTrackPlanner.verifiedForecastSegments',center)

if __name__=='__main__': unittest.main()
