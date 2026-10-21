"""V25 stage 8 static integration safety checks (not device motion certification)."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'app/src/main/java/io/atmos/weather'

class V25Stage8Motion(unittest.TestCase):
    def test_modal_has_measured_full_and_half_anchors(self):
        sheet=(SRC/'ui/GlassSheets.kt').read_text()
        for token in ('GlassSheetMotionPolicy.release(', 'GlassSheetMotionPolicy.cancel(',
                      'GlassSheetMotionPolicy.offset(', 'onSizeChanged', 'detectVerticalDragGestures',
                      'onDragCancel=', 'closing = true', 'snap()','CustomAccessibilityAction'):
            self.assertIn(token,sheet)
        self.assertIn('if (closing) return',sheet)
        self.assertIn('reduceMotion',sheet)
    def test_dock_drag_is_direct_and_release_springs(self):
        dock=(SRC/'next/NextReorderableToolDock.kt').read_text()
        self.assertIn('if (activeDrag || reduceMotion) snap()',dock)
        self.assertIn('NextMotionTokens.gentleSpring()',dock)
        self.assertIn('translationX = if (reduceMotion) 0f else slidingX',dock)
        self.assertIn('latestOnOrderChanged(updated)',dock)
        self.assertIn('CustomAccessibilityAction',dock)
    def test_motion_ignores_invalid_glint_pointer(self):
        tokens=(SRC/'next/NextMotionTokens.kt').read_text()
        self.assertIn('if (x.isFinite())',tokens)
        self.assertIn('else .5f',tokens)
    def test_no_new_landmark_dependency(self):
        self.assertFalse((SRC/'visual/FilamentLandmark.kt').exists())

if __name__=='__main__': unittest.main()
