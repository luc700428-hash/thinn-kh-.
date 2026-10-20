#!/usr/bin/env python3
"""Blocks V25 official release without proof. This tool does not generate proof."""
from pathlib import Path
import json

def missing_proof(gate):
    errors=[]
    required=set(['home_layout', 'scene_14', 'ambience_14', 'alarm_28', 'hi_fi_audio', 'liquid_optics', 'motion_physics', 'drag_toolbar', 'icons_typography', 'haptics', 'typhoon', 'quake', 'radar', 'alarm_reliability', 'super_island', 'ota_ui', 'apple_resource_audit', 'ios27_design_system_audit', 'swiftui27_article_audit', 'android_build', 'device_validation', 'signed_ota'])
    req=gate.get('requirements',[])
    if len(req)!=22 or {r.get('id') for r in req}!=required:
        errors.append('Incorrect set of 22 requirements')
    if gate.get('target_version')!='25.0' or gate.get('released') is True:
        errors.append('V25 version metadata invalid')
    for item in req:
        key=item.get('id','?')
        if item.get('status')!='verified': errors.append(f'{key}: incomplete')
        if len(item.get('v25_acceptance',[]))<2:errors.append(f'{key}: criteria missing')
        proofs=item.get('v25_evidence',[])
        if not isinstance(proofs,list) or not proofs:
            errors.append(f'{key}: no evidence');continue
        for p in proofs:
            if not isinstance(p,dict) or p.get('result')!='pass' or p.get('kind') not in ('ci','device','audit') or not str(p.get('reference','')).strip():
                errors.append(f'{key}: invalid evidence')
        if key in ('device_validation','signed_ota','haptics','super_island','alarm_reliability') and not any(
            isinstance(p,dict) and p.get('kind')=='device' and p.get('result')=='pass' and
            str(p.get('model','')).strip() and str(p.get('os_version','')).strip() and
            str(p.get('reference','')).strip() for p in proofs):
            errors.append(f'{key}: real device proof required')
    if gate.get('verified_full_release') is not True:errors.append('full release declaration absent')
    return errors
if __name__=='__main__':
    gate=json.loads((Path(__file__).parent/'next_release_gate.json').read_text(encoding='utf-8'))
    errors=missing_proof(gate)
    if errors:
        print('V25 OTA BLOCKED: '+str(len(errors))+' pending checks')
        for issue in errors:print(' - '+issue)
        raise SystemExit(1)
    print('V25 evidence declarations pass; continue APK compatibility checks.')
