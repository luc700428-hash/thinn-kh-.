import re, unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRAW = ROOT / 'app/src/main/res/drawable'
SRC = ROOT / 'app/src/main/java/io/atmos/weather'


class QuakeBullWidgetTests(unittest.TestCase):
    def test_twenty_four_vector_frames_loop(self):
        frames = sorted(DRAW.glob('widget_bull_f*.xml'))
        self.assertEqual(24, len(frames))
        first = frames[0].read_text()
        self.assertIn('<vector', first)
        # The loop closes: the last frame is the bull at rest again, nearly the first frame.
        y = lambda t: [float(v) for v in re.findall(r'translateY="(-?[0-9.]+)"', t)]
        self.assertLess(max(abs(a - b) for a, b in zip(y(first), y(frames[-1].read_text()))), 2.0)
        self.assertTrue(any('trimPathEnd' in f.read_text() for f in frames), 'ground cracks grow')

    def test_widgets_switch_to_the_bull_for_quakes(self):
        s = (SRC / 'widgets/WeatherBuddyWidget.kt').read_text()
        self.assertIn('WidgetEvent.Kind.EEW||it.kind==WidgetEvent.Kind.QUAKE', s)
        self.assertIn('BULL_FRAMES', s)
        badge = (SRC / 'widgets/AdaptiveWidgetLayout.kt').read_text()
        self.assertIn('setChronometerCountDown', badge)


if __name__ == '__main__':
    unittest.main()
