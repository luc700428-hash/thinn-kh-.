#!/usr/bin/env python3
"""Fail closed: a NEXT OTA cannot be published while any user requirement is unverified.

This is a recorded-evidence gate. A 'verified' label must only be set after actual
build, functional and real-device checks; source-only tests don't satisfy devices.
"""
import json
from pathlib import Path

manifest=Path(__file__).resolve().parent/'next_release_gate.json'
if not manifest.is_file():
    raise SystemExit('BLOCKED: Missing NEXT acceptance manifest')
gate=json.loads(manifest.read_text(encoding='utf-8'))
reqs=gate.get('requirements',[])
if len(reqs)<22 or len({x['id'] for x in reqs}) != len(reqs):
    raise SystemExit('BLOCKED: Release gate incomplete or duplicate requirements')
missing=[f"{x['id']}: {x['name']} ({x['status']})" for x in reqs if x.get('status')!='verified']
if not gate.get('verified_full_release',False) or missing:
    print('NEXT official OTA BLOCKED: not all promised features independently verified.')
    for m in missing:print(' -',m)
    raise SystemExit(1)
print('NEXT gate declarations are complete. Continue with signature and upgrade verification.')
