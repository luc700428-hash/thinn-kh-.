"""R20: device-specific TTS identity and explicit fallback policies."""
from pathlib import Path
import unittest
P=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather/alarm'
class AlarmTtsR20(unittest.TestCase):
    def test_engine_identity(self):
        s=(P/'WeatherAlarmAudioService.kt').read_text()
        self.assertIn('engine.currentEngine',s)
        self.assertIn('SamsungTtsSelector.prepare(engine)',s)
        self.assertNotIn('fallBackToDefaultEngine()',s)
        self.assertIn('TextToSpeech(this,this)',s)
    def test_user_can_choose_and_preview(self):
        s=(P/'R10AlarmDesign.kt').read_text()
        self.assertIn('播報聲線／引擎',s)
        self.assertIn('SamsungTtsSelector.preview(ctx)',s)
    def test_samsung_fail_does_not_silently_use_google(self):
        s=(P/'SamsungTtsSelector.kt').read_text()
        self.assertIn('preferredEngine(ctx)',s)
        self.assertIn('engine.currentEngine',s)
        self.assertIn('!v.isNetworkConnectionRequired',s)
        self.assertIn('未切換 Google',s)
if __name__=='__main__':unittest.main()
