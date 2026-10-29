"""Cloud narration retirement and independent safety audio migration contracts."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'app/src/main/java/io/atmos/weather'
def code(path):return (BASE/path).read_text(encoding='utf-8')
class NaturalVoiceRegression(unittest.TestCase):
    def test_no_cloud_transport_or_keys_remain_in_client(self):
        app='\n'.join(p.read_text() for p in (BASE/'voice').glob('*.kt'))
        for removed in ('HttpURLConnection','FirebaseAuth','Ocp-Apim-Subscription-Key','AZURE_SPEECH_KEY','resolveAudio('):
            self.assertNotIn(removed,app)
    def test_disabled_for_previously_enabled_users(self):
        s=code('voice/NaturalVoiceHub.kt')
        self.assertRegex(s,r'fun enabled\([^\n]*\)=false')
        self.assertRegex(s,r'fun offer\([^\n]*\)=false')
        self.assertIn('.edit().clear().apply()',s)
        self.assertIn('natural_voice_v1',s)
        self.assertIn('.deleteRecursively()',s)
        self.assertIn('NaturalVoiceHub.retire(this@MainActivity)',code('MainActivity.kt'))
    def test_settings_and_routes_are_removed(self):
        self.assertFalse((BASE/'voice/NaturalVoiceSettingsPage.kt').exists())
        for path in ('ui/GlassNavigation.kt','presentation/home/WeatherHomeScreen.kt'):
            text=code(path)
            for term in ('自然語音','NaturalVoiceSettingsPage','onNaturalVoice','naturalVoiceOpen'):
                self.assertNotIn(term,text)
    def test_eew_remains_immediate_and_local(self):
        self.assertIn('SeismicAlertNotifier.deliver(this,quake,scenario)',code('earthquake/ExpTechSeismicService.kt'))
        for path in ('earthquake/SeismicAlertNotifier.kt','earthquake/SeismicWarningOverlay.kt'):
            text=code(path)
            self.assertIn('SeismicContinuousWarning.start(',text)
            self.assertIn('SeismicSpeech.speak(',text)
            self.assertNotIn('resolveAudio(',text)
    def test_alarm_fallback_and_fullscreen_remain(self):
        service=code('alarm/WeatherAlarmAudioService.kt')
        for term in ('beginAudio(alarm)','TextToSpeech(this,this)','if(!cloudAccepted)',
                     'setUsage(AudioAttributes.USAGE_ALARM)','ACTION_SNOOZE','WeatherAlarmSpeechCache.load(this)'):
            self.assertIn(term,service)
    def test_retired_workers_cannot_generate_cloud_audio(self):
        hub=code('voice/NaturalVoiceHub.kt')
        for term in ('CoroutineScope','MediaPlayer','TextToSpeech','URL('):self.assertNotIn(term,hub)
    def test_server_credentials_are_not_distributed(self):
        worker=(ROOT/'backend/azure-speech-worker/src.js').read_text()
        for term in ('AZURE_SPEECH_KEY','verifyFirebase','GLOBAL_DAILY_LIMIT','xml(text)'):
            self.assertIn(term,worker)
if __name__=='__main__':unittest.main()
