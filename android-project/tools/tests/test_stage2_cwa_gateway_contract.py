"""CWA credential/routing regressions; CI/real network verification still required."""
from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[2]
class Stage2CwaGatewayContract(unittest.TestCase):
    def test_no_bundled_sample_authorization_or_network_fallback(self):
        p=(R/'app/src/main/java/io/atmos/weather/earthquake/CwaOfficialGateway.kt').read_text()
        self.assertNotIn('rdec-key-',p)
        self.assertNotIn('readPublicTyphoon()',p)
        self.assertIn('SeismicPushRegistration.configured()',p)
        self.assertIn('readOfficialCwa',p)
    def test_file_api_envelope_normalized_to_existing_parser(self):
        p=(R/'app/src/main/java/io/atmos/weather/earthquake/CwaOfficialGateway.kt').read_text()
        for field in ('cwaopendata','TropicalCyclones','Sent','records','36L*3600L'):
            self.assertIn(field,p)
    def test_backend_dataset_routes_are_allowlisted(self):
        p=(R/'backend/functions/cwa_official.cjs').read_text()
        self.assertIn("dataset === 'W-C0034-005'",p)
        self.assertIn('/fileapi/v1/opendataapi/',p)
        self.assertIn('/api/v1/rest/datastore/',p)
    def test_failure_explained_without_sample_storm_or_key(self):
        p=(R/'app/src/main/java/io/atmos/weather/typhoon/TyphoonCenterPage.kt').read_text()
        self.assertIn('CwaOfficialGateway.describeFailure(error)',p)
        self.assertIn('非即時資訊',p)
if __name__=='__main__': unittest.main()
