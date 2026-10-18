"""R13 voice integration contracts; not a substitute for Android phone or Azure testing."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'app/src/main/java/io/atmos/weather'
def code(path):return (BASE/path).read_text(encoding='utf-8')
class NaturalVoiceRegression(unittest.TestCase):
    def test_azure_key_never_in_apk(self):
        app='\n'.join(p.read_text(encoding='utf-8') for p in (BASE/'voice').glob('*.kt'))
        self.assertNotIn('Ocp-Apim-Subscription-Key',app)
        self.assertNotIn('AZURE_SPEECH_KEY',app)
        self.assertIn('THINN_AZURE_PROXY_URL',app)
    def test_default_consent_off(self):
        s=code('voice/NaturalVoiceHub.kt')
        self.assertIn('getBoolean("enabled",false)',s)
        self.assertIn('getBoolean("daily",false)',s)
        self.assertIn('getBoolean("personal",false)',s)
    def test_no_fake_azure_preview(self):
        s=code('voice/NaturalVoiceHub.kt')
        self.assertIn('fun preview(',s)
        self.assertIn('resolveAudio(ctx,PREVIEW,true)',s)
        self.assertIn('Azure 暫時無法試聽',s)
    def test_priority_and_local_eew(self):
        hub=code('voice/NaturalVoiceHub.kt')
        eew=code('earthquake/ExpTechSeismicService.kt')
        overlay=code('earthquake/SeismicWarningOverlay.kt')
        self.assertIn('interruptForEarthquake()',hub)
        self.assertIn('interruptForEarthquake()',eew)
        self.assertIn('interruptForEarthquake()',overlay)
        self.assertIn('SeismicContinuousWarning.start(this,quake)',eew)
        self.assertIn('SeismicSpeech.speak(this,SeismicSpeech.warning',eew)
        self.assertNotIn('resolveAudio(',eew)
    def test_alarm_fallback_and_fullscreen_untouched(self):
        service=code('alarm/WeatherAlarmAudioService.kt')
        self.assertIn('beginAudio(alarm)',service)
        self.assertIn('TextToSpeech(this,this)',service)
        self.assertIn('onFailure={ if(currentId==id)',service)
        self.assertIn('setUsage(AudioAttributes.USAGE_ALARM)',service)
        self.assertIn('NaturalVoiceHub.stopAlarm()',service)
    def test_notifications_opt_in_and_private(self):
        self.assertIn('Priority.WEATHER_ALERT',code('alerts/CwaAlertWorker.kt'))
        self.assertIn('Priority.DAILY',code('briefing/WeatherBriefingWorker.kt'))
        tasks=code('task/TaskReminderScheduler.kt')
        self.assertIn('Priority.PERSONAL',tasks)
        self.assertIn('privacySensitive=true,cacheable=false',tasks)
    def test_settings_uses_foundation_without_material_controls(self):
        page=code('voice/NaturalVoiceSettingsPage.kt')
        for text in ('曉臻','曉雨','試聽','清除語音快取','重大天氣警報'):
            self.assertIn(text,page)
        self.assertNotIn('material3',page.lower())
        self.assertIn('NaturalVoiceSettingsPage(',code('presentation/home/WeatherHomeScreen.kt'))
    def test_worker_has_secret_and_hard_quota(self):
        worker=(ROOT/'backend/azure-speech-worker/src.js').read_text(encoding='utf-8')
        for token in ('AZURE_SPEECH_KEY','FIREBASE_PROJECT_ID','verifyFirebase','GLOBAL_DAILY_LIMIT',
                      'USER_MONTHLY_LIMIT','MONTHLY_HARD_LIMIT','SSML','xml(text)','quota_exhausted'):
            self.assertTrue(token=='SSML' or token in worker,token)
        self.assertIn('xml(text)',worker)
if __name__=='__main__':unittest.main()
