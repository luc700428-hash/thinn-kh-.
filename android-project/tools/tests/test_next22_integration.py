"""Regression safety for NEXT integrated source; not a device certification."""
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
HOME=ROOT/'app/src/main/java/io/atmos/weather/presentation/home/WeatherHomeScreen.kt'
GATE=ROOT/'tools/next_release_gate.json'

class NextIntegratedSafety(unittest.TestCase):
    def test_neutral_typhoon_entry_and_verified_only_alert(self):
        s=HOME.read_text(encoding='utf-8')
        self.assertTrue('NEXT_INTEGRATED_TYPOON_NEUTRAL_ENTRY' in s or
                        'NEXT_PHASE1_PERMANENT_TYPHOON_ENTRY' in s)
        self.assertIn('Text("颱風資訊",',s)
        self.assertIn('if (officialStormActive || verifiedTyphoonWarning!=null) {',s)
        self.assertIn('verifiedTyphoonWarning?.let { official ->',s)
        self.assertIn('TaiwanStormRelevance.isMovingTowardTaiwan',s)

    def test_forecast_never_opens_a_modal_automatically(self):
        s=HOME.read_text(encoding='utf-8')
        self.assertIn('OfficialStormTimeline.parse(data)',s)
        self.assertNotIn('nextOfficialStormPrompt',s)
        self.assertNotIn('thinn_next_official_typhoon_sheet',s)
        self.assertIn('if (typhoonPageOpen)',s)
        self.assertIn('TyphoonDirection.zh(it)',s)

    def test_rss_readability_and_no_technical_copy(self):
        s=HOME.read_text(encoding='utf-8')
        self.assertIn('notice.summary.ifBlank',s)
        self.assertIn('Color(0xB2A43B47)',s)
        self.assertNotIn('近 24 小時官方 RSS 公告；是否仍有效請核對官方資訊',s)
        self.assertIn('LaunchedEffect(query)',s)

    def test_full_release_gate_is_not_bypassed(self):
        g=json.loads(GATE.read_text(encoding='utf-8'))
        self.assertEqual(len(g['requirements']),22)
        # Manifest is the authority. This test never writes or promotes status.
        self.assertIn('verified_full_release',g)
        tool=(ROOT/'tools/verify_next_release_gate.py').read_text(encoding='utf-8')
        self.assertIn("x.get('status')!='verified'",tool)
        self.assertIn('verified_full_release',tool)
        workflow=(ROOT.parent/'.github/workflows/build-apk.yml').read_text(encoding='utf-8')
        self.assertIn('NEXT complete feature acceptance gate',workflow)
        self.assertIn('verify_next_release_gate.py',workflow)
        self.assertIn("github.event_name == 'workflow_dispatch'",workflow)
        self.assertIn("if: env.SIGN_READY == 'true'",workflow.split(
            'Verify OTA upgrade compatibility with the current published APK',1)[1].split(
            'Produce public OTA manifest from signed APK',1)[0])

    def test_signed_ota_checks_still_mandatory(self):
        workflow=(ROOT.parent/'.github/workflows/build-apk.yml').read_text(encoding='utf-8')
        for t in ('Verify OTA upgrade compatibility with the current published APK',
                  'Official APK signature differs', 'Verify tracked Android source',
                  'Publish official OTA GitHub Release','embedded-build-source-proof.json'):
            self.assertIn(t,workflow)

if __name__=='__main__':unittest.main()

