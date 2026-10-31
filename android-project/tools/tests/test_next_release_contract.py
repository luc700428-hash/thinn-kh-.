"""NEXT App integration & audio fixture checks, runnable inside the GitHub Actions Python suite.

This is a source/asset regression test, NOT an instrumented Android device test.
"""
import hashlib
import struct
import unittest
import wave
from pathlib import Path

ANDROID = Path(__file__).resolve().parents[2]
APP = ANDROID/'app/src/main'
BASE = APP/'java/io/atmos/weather'
SCENES = ('sunny','drizzle','light_rain','moderate_rain','heavy_rain',
          'thunderstorm','severe_thunderstorm','haze','fog','dense_fog',
          'dust_storm','light_snow','moderate_snow','heavy_snow')

class NextIntegrationReleaseContract(unittest.TestCase):
    def test_all_42_real_weather_audio_files(self):
        for folder in ('ambience','music/dawn','music/rain'):
            hashes=set()
            for scene in SCENES:
                path=APP/'assets/next_weather'/folder/f'{scene}.wav'
                self.assertTrue(path.is_file(),str(path))
                hashes.add(hashlib.sha256(path.read_bytes()).hexdigest())
                with wave.open(str(path),'rb') as wav:
                    self.assertEqual(wav.getnchannels(),1)
                    self.assertEqual(wav.getsampwidth(),2)
                    self.assertEqual(wav.getframerate(),44100)
                    self.assertGreater(wav.getnframes(),wav.getframerate()*8)
                    first=struct.unpack('<hh',wav.readframes(2))
                    wav.setpos(wav.getnframes()-2)
                    last=struct.unpack('<hh',wav.readframes(2))
                    self.assertLessEqual(abs(first[0]-last[1]),1,str(path))
                    self.assertLessEqual(abs((first[1]-first[0])-(last[1]-last[0])),2,str(path))
            self.assertEqual(len(hashes),14,f'duplicate files in {folder}')

    def test_home_renders_weather_scene_without_layout_replacement(self):
        src=(BASE/'presentation/home/WeatherHomeScreen.kt').read_text(encoding='utf-8')
        self.assertIn('NextWeatherAmbience(',src)
        self.assertIn('NextWeatherBackdrop(',src)
        self.assertIn('HorizontalPager(',src)
        self.assertIn('AppGlassNavigation(',src)
        self.assertIn('settingsPrefs.getBoolean("sound_enabled",true)',src)
        self.assertIn('if(!settingsPrefs.contains("sound_enabled"))',src)

    def test_main_dock_has_real_navigation_and_fixed_order(self):
        nav=(BASE/'ui/GlassNavigation.kt').read_text(encoding='utf-8')
        dock=(BASE/'next/NextReorderableToolDock.kt').read_text(encoding='utf-8')
        for word in ('NextReorderableToolDock(', 'actions=tabs', 'allowReorder=false',
                     'canonical.indexOf(id)', 'remove("next_navigation_order")'):
            self.assertIn(word,nav)
        self.assertNotIn('actions=orderedActions',nav)
        for word in ('detectHorizontalDragGestures', 'if(allowReorder)',
                     'detectVerticalDragGestures', 'onDragEnd=', 'latestOnSelected(destination.id)',
                     'touchX = if(vertical).5f else lightX', 'touchY=if(vertical)lightX else .08f'):
            self.assertIn(word,dock)

    def test_alarm_retains_background_service_and_fallback(self):
        service=(BASE/'alarm/WeatherAlarmAudioService.kt').read_text(encoding='utf-8')
        for item in ('NextWeatherAlarmPlayer.start(', 'if(nextPlayer==null)',
                     'setAlarmVolume(.18f)','setAlarmVolume(1f)',
                     'ACTION_SNOOZE','stopAudio()',
                     'WeatherAlarmSpeechCache.load(this)'):
            self.assertIn(item,service)
        self.assertNotIn('if(result != TextToSpeech.SUCCESS) runCatching{player?.setVolume(1f,1f)}',service)

    def test_ota_shows_actual_download_state_without_fake_success(self):
        page=(BASE/'update/AppUpdatePage.kt').read_text(encoding='utf-8')
        card=(BASE/'next/NextOtaPresentation.kt').read_text(encoding='utf-8')
        for item in ('NextOtaStatusCard(', 'AppUpdater.downloadAndInstall(',
                     'downloadJob?.cancel()','installerPrompted',
                     'onDownload={pending?.let {downloadOfficial(it)}}'):
            self.assertIn(item,page)
        self.assertIn('尚未確認完成升級',card)
        self.assertNotIn('NextOtaState.Completed -> "已確認完成升級。"',page)

    def test_glass_accessibility_and_motion_settings(self):
        surface=(BASE/'next/NextOpticalSurface.kt').read_text(encoding='utf-8')
        nav=(BASE/'ui/GlassNavigation.kt').read_text(encoding='utf-8')
        for item in ('reduceTransparency', 'drawWithContent','Brush.radialGradient'):
            self.assertIn(item,surface)
        self.assertIn('reduceMotion=rememberAppleReduceMotion()',nav)

if __name__=='__main__':unittest.main()
