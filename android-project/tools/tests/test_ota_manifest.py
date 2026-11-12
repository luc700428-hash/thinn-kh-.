import importlib.util
import json
import subprocess
import sys
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

    def test_stable_notes_require_short_current_changes(self):
        current=Path(__file__).resolve().parents[1]/"stable_maintenance_notes.md"
        module.make_manifest(self.apk,current,"25.0.340",25000340,concise=True)
        for body in ("\n".join("- 變更" for _ in range(7)),"- "+"字"*100,"工程細節\n- 變更"):
            self.notes.write_text("<!-- THINN_INCREMENTAL_CHANGES -->\n"+body+"\n<!-- END_THINN_INCREMENTAL_CHANGES -->",encoding="utf-8")
            with self.assertRaises(ValueError):
                module.make_manifest(self.apk,self.notes,"25.0.340",25000340,concise=True)

    def test_release_cli_cannot_publish_a_long_changes_list(self):
        self.notes.write_text("<!-- THINN_INCREMENTAL_CHANGES -->\n"+"- 變更\n"*7+"<!-- END_THINN_INCREMENTAL_CHANGES -->",encoding="utf-8")
        target=Path(self.tmp.name)/"ota.json"
        result=subprocess.run([sys.executable,str(MODULE_PATH),"--apk",str(self.apk),"--notes",str(self.notes),"--version","25.0.340","--version-code","25000340","--output",str(target)],capture_output=True,text=True)
        self.assertNotEqual(0,result.returncode)
        self.assertIn("1–6 short user-facing bullets",result.stderr)
        self.assertFalse(target.exists())

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
