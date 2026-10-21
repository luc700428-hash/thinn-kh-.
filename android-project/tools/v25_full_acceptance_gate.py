#!/usr/bin/env python3
"""Safety gate: do not publish the reconstructed source as a verified full V25 release.

An acceptance file must be intentionally created *after* real CI build, device QA,
original signing certificate comparison, and 22 fully documented feature checks.
Missing, placeholder or stale evidence blocks GitHub Release publication.
"""
import json, os, pathlib, sys

here = pathlib.Path(__file__).resolve().parent
path = here / 'v25_full_acceptance.json'
if not path.is_file():
    sys.exit('BLOCKED: No v25_full_acceptance.json (full 22-item independent QA and signing evidence not yet supplied). This is a PREVIEW source, not an official OTA.')
data = json.loads(path.read_text(encoding='utf-8'))
checks = data.get('checks')
if not isinstance(checks, list) or len(checks) != 22:
    sys.exit('BLOCKED: expected exactly 22 independently evidenced acceptance checks')
commit=os.environ.get('GITHUB_SHA','')
if len(commit)!=40 or data.get('commit_sha')!=commit:
    sys.exit('BLOCKED: acceptance evidence must target the exact source commit')
if not all(isinstance(c,dict) and c.get('verified') is True and
           isinstance(c.get('evidence_url'),str) and c['evidence_url'].startswith('https://')
           for c in checks):
    sys.exit('BLOCKED: every check must include actual verification and evidence URL')
if not all(data.get(k) is True for k in
           ('android_ci_passed','device_qa_passed','signing_certificate_matched','upgrade_test_passed')):
    sys.exit('BLOCKED: Android build, device QA, signing and upgrade evidence all required')
print('V25 evidence metadata structurally complete; CI still verifies signed APK and previous version')
