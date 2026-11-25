import json, subprocess, unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'app/src/main/res/raw'
SRC = ROOT / 'app/src/main/java/io/atmos/weather/festival'
CLIPS = ['launch', 'boom', 'boom_small', 'crackle', 'firecracker', 'sleigh', 'carol', 'tick', 'chime', 'ghost', 'drum']


class FestivalEasterEggTests(unittest.TestCase):
    def test_every_sound_is_a_small_ogg(self):
        for c in CLIPS:
            data = (RAW / f'festival_{c}.ogg').read_bytes()
            self.assertTrue(data.startswith(b'OggS'), c)
            self.assertLess(len(data), 300_000, c)

    def test_every_festival_draws_something(self):
        overlay = (SRC / 'FestivalOverlay.kt').read_text()
        enum = (SRC / 'FestivalCalendar.kt').read_text()
        names = [l.strip().split('(')[0] for l in enum.split('enum class Festival')[1].split('}')[0].splitlines() if '("' in l]
        self.assertGreaterEqual(len(names), 18)
        for n in names:
            self.assertIn(f'Festival.{n}', overlay, n)

    def test_overlay_never_blocks_touches_or_runs_in_tests(self):
        overlay = (SRC / 'FestivalOverlay.kt').read_text()
        self.assertNotIn('pointerInput', overlay)
        self.assertNotIn('clickable', overlay)
        self.assertIn('FestivalDirector.disabled', overlay)
        self.assertIn('rememberAppleReduceMotion', overlay)

    def test_sounds_respect_silent_mode_and_setting(self):
        sound = (SRC / 'FestivalSound.kt').read_text()
        self.assertIn('RINGER_MODE_NORMAL', sound)
        self.assertIn('festival_sound', sound)

    def test_names_never_misname_taiwan(self):
        for f in SRC.glob('*.kt'):
            text = f.read_text()
            self.assertNotIn('中國台灣', text)
            self.assertNotIn('中華台北', text)


if __name__ == '__main__':
    unittest.main()
