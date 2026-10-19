from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2] / "app/src/main/java/io/atmos/weather"

class AlarmSpeechR10Contract(unittest.TestCase):
    def test_alarm_time_greeting_and_freshness(self):
        speech=(ROOT/'alarm/WeatherAlarmNarration.kt').read_text()
        for text in ('"早安"','"午安"','"晚上好"','MAX_WEATHER_AGE_MS','rainAndAdvice','接下來六小時'):
            self.assertIn(text,speech)
        self.assertNotIn('目前沒有警報',speech)

    def test_fresh_source_and_audio_fallback(self):
        home=(ROOT/'presentation/home/WeatherHomeScreen.kt').read_text()
        service=(ROOT/'alarm/WeatherAlarmAudioService.kt').read_text()
        cache=(ROOT/'alarm/WeatherAlarmSpeechCache.kt').read_text()
        self.assertIn('WeatherAlarmSpeechCache.save',home)
        self.assertNotIn('val summary = "早安',home)
        self.assertIn('refreshAlarmWeather()',service)
        self.assertIn('withTimeoutOrNull(6_000L)',service)
        self.assertIn('WeatherAlarmNarration.build(ZonedDateTime.now()',service)
        selector=(ROOT/'alarm/SamsungTtsSelector.kt').read_text()
        self.assertIn('!v.isNetworkConnectionRequired',selector)
        self.assertIn('SamsungTtsSelector.prepare(engine)',service)
        self.assertNotIn('engine.currentEngine',service)
        self.assertIn('SamsungTtsSelector.intended(this)',service)
        self.assertIn('player?.setVolume(1f,1f)',service)
        self.assertIn('w.fetchedAt.toEpochMilli()',cache)
        self.assertIn('w.observedAt.toEpochMilli()',cache)

if __name__=='__main__': unittest.main()
