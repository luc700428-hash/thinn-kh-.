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


def validate_concise_notes(text: str) -> None:
    """Stable releases list only a few current, user-facing changes."""
    start="<!-- THINN_INCREMENTAL_CHANGES -->";end="<!-- END_THINN_INCREMENTAL_CHANGES -->"
    if text.count(start)!=1 or text.count(end)!=1:
        raise ValueError("Concise release notes require one current changes block")
    body=text.split(start,1)[1].split(end,1)[0]
    lines=[line.strip() for line in body.splitlines() if line.strip()]
    if not 1<=len(lines)<=6 or any(not line.startswith("- ") or len(line)>90 for line in lines):
        raise ValueError("Release notes must have 1–6 short user-facing bullets, at most 88 characters each")
    outside=text.split(start,1)[0]+text.split(end,1)[1]
    if any(line.strip() and not line.startswith("# ") for line in outside.splitlines()) or len(text)>750:
        raise ValueError("Keep release notes to a title and the current short changes")


def make_manifest(apk: Path, notes: Path, version: str, version_code: int, preview: bool = False, concise: bool = False) -> dict:
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
    manifest = {
        "schema": 1,
        "apk_size": apk.stat().st_size,
        "version": version,
        "version_code": version_code,
        "package_name": PACKAGE,
        "apk_url": f"https://github.com/{REPOSITORY}/releases/download/{release_tag}/thinn-khi.apk",
        "sha256": checksum.hexdigest(),
        "notes": notes.read_text(encoding="utf-8"),
    }
    if concise:
        validate_concise_notes(manifest["notes"])
    if len((json.dumps(manifest,ensure_ascii=False,indent=2)+"\n").encode("utf-8")) > 131072:
        raise ValueError("OTA manifest exceeds 128 KiB; shorten release notes explicitly instead of silent truncation")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--preview", action="store_true", help="Signed rolling test-channel OTA; not a stable release")
    args = parser.parse_args()
    data = make_manifest(args.apk, args.notes, args.version, args.version_code, preview=args.preview, concise=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Public OTA manifest created: {data['version']} SHA256={data['sha256']}")


if __name__ == "__main__":
    main()
