#!/usr/bin/env python3
"""Generate public OTA metadata shipped as a GitHub Release asset.

All APK files are already signed. Do not create the manifest for unsigned APKs.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

REPOSITORY = "luc700428-hash/thinn-kh-."
PACKAGE = "io.atmos.weather"


def make_manifest(apk: Path, notes: Path, version: str, version_code: int, preview: bool = False) -> dict:
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise ValueError("version must be a safe x.y.z string")
    if version_code < 1:
        raise ValueError("version_code must be positive")
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("signed APK is missing or empty")
    checksum = hashlib.sha256()
    with apk.open("rb") as fp:
        for block in iter(lambda: fp.read(1024 * 1024), b""):
            checksum.update(block)
    if preview and not version.startswith("25.0."):
        raise ValueError("preview release must use V25 version")
    release_tag = "v25-preview" if preview else f"v{version}"
    return {
        "schema": 1,
        "version": version,
        "version_code": version_code,
        "package_name": PACKAGE,
        "apk_url": f"https://github.com/{REPOSITORY}/releases/download/{release_tag}/thinn-khi.apk",
        "sha256": checksum.hexdigest(),
        "notes": notes.read_text(encoding="utf-8")[:16000],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--preview", action="store_true", help="Signed rolling test-channel OTA; not a stable release")
    args = parser.parse_args()
    data = make_manifest(args.apk, args.notes, args.version, args.version_code, preview=args.preview)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Public OTA manifest created: {data['version']} SHA256={data['sha256']}")


if __name__ == "__main__":
    main()
