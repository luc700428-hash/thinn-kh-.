"""R10 source-policy smoke tests; Android compilation and device QA remain required."""
from pathlib import Path
import re,unittest
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
RAW=ROOT/'app/src/main/res/raw'
class R10SafetyAndVisual(unittest.TestCase):
    def test_live_and_fcm_filter_before_alert(self):
        live=(JAVA/'earthquake/ExpTechSeismicService.kt').read_text()
        push=(JAVA/'push/SeismicPushPolicy.kt').read_text()
        self.assertIn('if (!TaiwanEewScope.relevant(quake)) return@forEach',live)
        self.assertIn('if(foreign && e.region == "TAIWAN")',push)
        self.assertLess(live.index('if (!TaiwanEewScope.relevant(quake))'),live.index('sendWarning(quake,scenario)'))
    def test_simulator_is_local_and_labeled(self):
        scenario=(JAVA/'earthquake/SeismicSimulation.kt').read_text()
        overlay=(JAVA/'earthquake/SeismicWarningOverlay.kt').read_text()
        self.assertIn('test=true',scenario)
        self.assertIn('simulation=true', (JAVA/'earthquake/EarthquakeCenterPage.kt').read_text())
        for text in ('演練情境震度（虛構）','並非真實速報','SeismicTrainingVoice'):
            self.assertIn(text,overlay)
        for forbidden in ('FirebaseMessaging','NotificationManager','sendWarning(','register('):
            self.assertNotIn(forbidden,scenario)
    def test_wheels_and_audio_cards_not_material(self):
        ui=(JAVA/'alarm/R10AlarmDesign.kt').read_text()
        page=(JAVA/'alarm/WeatherAlarmPage.kt').read_text()
        for must in ('detectVerticalDragGestures','R10Switch','R10RingtonePicker','remember { mutableStateOf'):
            self.assertIn(must,ui)
        self.assertIn('R10AlarmEditor(initial=editing',page)
        self.assertNotIn('AndroidView(',ui)
        self.assertNotIn('ButtonDefaults.',ui)
        self.assertEqual(6,len(re.findall(r'Choice\("(dawn|rain|wood|water|fire|metal)"',ui)))
        for n in ('dawn','dawn_rain','rain','rain_clear','wood','water','fire','metal'):
            self.assertGreater((RAW/f'thinn_alarm_{n}.ogg').stat().st_size,50000)
    def test_weather_voice_keeps_freshness_and_fallback(self):
        alarm=(JAVA/'alarm/WeatherAlarmAudioService.kt').read_text()
        narr=(JAVA/'alarm/WeatherAlarmNarration.kt').read_text()
        for word in ('早安','午安','晚上好','MAX_WEATHER_AGE_MS'):
            self.assertIn(word,narr)
        for word in ('withTimeoutOrNull(6_000L)','R10AlarmTones.resource','WeatherAlarmSpeechCache.load'):
            self.assertIn(word,alarm)
if __name__=='__main__':unittest.main()
