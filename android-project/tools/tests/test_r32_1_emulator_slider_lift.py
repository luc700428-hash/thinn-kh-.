import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SLIDER = ROOT / 'app/src/main/java/io/atmos/weather/ui/LiquidBubbleSlider.kt'


class EmulatorSliderLiftTest(unittest.TestCase):
    """CI #375: the emulator kept the held knob a solid white capsule, hiding the scene."""

    def test_held_knob_lifts_on_every_device_except_reduce_motion(self):
        source = SLIDER.read_text(encoding='utf-8')
        held = re.search(r'val held=\(dragging\|\|pressed\)&&!(\w+)', source)
        self.assertIsNotNone(held)
        self.assertEqual('reduceMotion', held.group(1))
        self.assertIn('val reduceMotion=rememberAppleReduceMotion()', source)

    def test_emulator_lift_is_one_step_without_springs(self):
        source = SLIDER.read_text(encoding='utf-8')
        self.assertIn('val reduce=reduceMotion||LiquidGlassKit.softwareRenderedDevice', source)
        self.assertRegex(source, r'val lift by animateFloatAsState\(if\(held\)1f else 0f,if\(reduce\)tween\(0\)')


if __name__ == '__main__':
    unittest.main()
