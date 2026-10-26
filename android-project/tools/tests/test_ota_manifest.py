import importlib.util
import json
import tempfile
import unittest
from hashlib import sha256
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "create_ota_manifest.py"
spec = importlib.util.spec_from_file_location("ota_tool", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.apk = root / "thinn-khi.apk"
        self.notes = root / "notes.md"
        self.apk.write_bytes(b"test_signed_apk_bytes")
        self.notes.write_text("新功能更新\n", encoding="utf-8")

    def test_public_asset_and_exact_digest(self):
        item = module.make_manifest(self.apk, self.notes, "22.0.123", 22000123)
        self.assertEqual(item["sha256"], sha256(b"test_signed_apk_bytes").hexdigest())
        self.assertEqual(item["package_name"], "io.atmos.weather")
        self.assertEqual(item["version"], "22.0.123")
        self.assertEqual(item["version_code"], 22000123)
        self.assertEqual(item["apk_url"], "https://github.com/luc700428-hash/thinn-kh-./releases/download/v22.0.123/thinn-khi.apk")
        self.assertIn("新功能", item["notes"])

    def test_long_notes_are_complete_not_silently_truncated(self):
        text="實際發行內容\n"*4000
        self.notes.write_text(text,encoding='utf-8')
        item=module.make_manifest(self.apk,self.notes,"25.0.300",25000300)
        self.assertEqual(item['notes'],text)
    def test_oversize_manifest_fails_before_publication(self):
        self.notes.write_text("大"*50000,encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'128 KiB'):
            module.make_manifest(self.apk,self.notes,"25.0.300",25000300)

    def test_reject_version_injection(self):
        with self.assertRaises(ValueError):
            module.make_manifest(self.apk, self.notes, "../../other", 1)

    def test_reject_empty_apk(self):
        self.apk.write_bytes(b"")
        with self.assertRaises(ValueError):
            module.make_manifest(self.apk, self.notes, "22.0.124", 22000124)

    def test_reject_invalid_version_code(self):
        with self.assertRaises(ValueError):
            module.make_manifest(self.apk, self.notes, "22.0.124", 0)


if __name__ == "__main__":
    unittest.main()
