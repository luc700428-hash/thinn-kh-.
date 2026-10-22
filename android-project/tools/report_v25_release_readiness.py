#!/usr/bin/env python3
"""Read-only V25 release readiness report; NOT release evidence or authorization."""
import argparse
import json
from pathlib import Path
from verify_v25_release_evidence import missing_proof


def summarize(gate):
    req=gate.get('requirements',[])
    blockers=missing_proof(gate)
    rows=[]
    for item in req:
        proofs=item.get('v25_evidence',[])
        if not isinstance(proofs,list): proofs=[]
        rows.append({
            'id':str(item.get('id','?')),
            'name':str(item.get('name','')),
            'state':str(item.get('status','missing')),
            'evidence_count':len(proofs),
            'requires_more_evidence':item.get('status')!='verified' or len(proofs)==0,
        })
    verified=sum(1 for row in rows if not row['requires_more_evidence'])
    return {
        'schema':1,
        'title':'thinn-khì V25 RELEASE READINESS (audit only)',
        'target_version':gate.get('target_version'),
        'requirement_count':len(req),
        'evidence_backed_candidates':verified,
        'readiness':'BLOCKED' if blockers else 'DECLARED_READY_REQUIRES_INDEPENDENT_VERIFICATION',
        'missing_proof_checks':len(blockers),
        'blockers':blockers,
        'requirements':rows,
        'disclaimer':'This report is not device testing, Firebase deployment proof, signing proof or release approval.'
    }


def main():
    ap=argparse.ArgumentParser(description='Print a read-only V25 readiness audit; never publishes OTA.')
    ap.add_argument('--manifest',type=Path,default=Path(__file__).with_name('next_release_gate.json'))
    ap.add_argument('--output',type=Path)
    ns=ap.parse_args()
    data=summarize(json.loads(ns.manifest.read_text(encoding='utf-8')))
    if ns.output:
        ns.output.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f"V25 {data['readiness']}: {data['evidence_backed_candidates']}/{data['requirement_count']} evidence-backed declarations; {data['missing_proof_checks']} missing proof checks")
    for row in data['requirements']:
        if row['requires_more_evidence']:
            print(f" - {row['id']}: {row['name']} [state={row['state']}, evidence={row['evidence_count']}]")
    if ns.output:print(f"Read-only JSON report: {ns.output}")
    # Reporting itself is successful even when the release is blocked.

if __name__=='__main__': main()
