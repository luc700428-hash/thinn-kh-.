"""R10.8 alarm screen regression checks; not a substitute for Android compilation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/java/io/atmos/weather/alarm/WeatherAlarmRingActivity.kt"

class R108RingingScreen(unittest.TestCase):
    def test_reference_layout_and_accessible_stop(self):
        src = SOURCE.read_text(encoding="utf-8")
        for token in (
            'background(Color.Black)', '"鬧鐘"', '88.sp',
            '"稍後提醒"', '"滑動來停止"',
            'detectHorizontalDragGestures',
            'onClick(label = "無法滑動？輕觸這裡停止鬧鐘")',
            'onSnooze = { close(true) }', 'onStop = { close(false) }'
        ):
            self.assertIn(token, src)
        self.assertNotIn('Brush.radialGradient', src)
        self.assertNotIn('Spacer(Modifier.weight(1f))', src)

    def test_os_alarm_audio_and_touch_physics(self):
        src = SOURCE.read_text(encoding="utf-8")
        self.assertIn('setVolumeControlStream(AudioManager.STREAM_ALARM)', src)
        self.assertIn('fingerDown || reduceMotion', src)
        self.assertIn('spring(dampingRatio = .68f, stiffness = 280f)', src)
        self.assertIn('dragPx >= maxDrag * .75f', src)
        self.assertIn('setShowWhenLocked(true)', src)
        self.assertIn('setTurnScreenOn(true)', src)
        self.assertIn('FLAG_KEEP_SCREEN_ON', src)
        self.assertNotIn('override fun onBackPressed() { close(false) }', src)
        self.assertIn('WeatherAlarmAudioService.ACTION_STOP', src)
        self.assertIn('WeatherAlarmAudioService.ACTION_SNOOZE', src)
        self.assertNotIn('MediaPlayer.create', src)
        self.assertNotIn('AudioManager.STREAM_MUSIC', src)

if __name__ == "__main__":
    unittest.main()
