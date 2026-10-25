#!/usr/bin/env python3
"""Apply the packaged binary-aware patch; preserve independent upstream commits."""
import hashlib,json,subprocess,sys
from pathlib import Path

def run(args,cwd):return subprocess.run(args,cwd=cwd,check=True,text=True)
def apply(package,checkout):
    package=Path(package).resolve();checkout=Path(checkout).resolve()
    manifest=json.loads((package/'PACKAGE_MANIFEST.json').read_text())
    applied=[];remaining=[]
    for entry in manifest['changes']:
        rel=entry['path'];p=checkout/rel
        digest=hashlib.sha256(p.read_bytes()).hexdigest() if p.is_file() else None
        (applied if digest==entry['after_sha256'] else remaining).append(rel)
    if not remaining:
        print('此更新包的程式碼已存在，略過重複套用。');return
    run(['git','fetch','--depth=1','origin',manifest['base_commit']],checkout)
    args=['git','apply','--3way','--index','--binary']+['--exclude='+p for p in applied]+[str(package/'UPDATE.patch')]
    run(args,checkout)
    if subprocess.run(['git','diff','--name-only','--diff-filter=U'],cwd=checkout,capture_output=True,text=True,check=True).stdout.strip():
        raise RuntimeError('上游同一段程式已有不同修改，已停止，未推送任何內容。')
    print('更新已套用；保留目前 main 的其他變更。')
if __name__=='__main__':
    try:apply(sys.argv[1],sys.argv[2])
    except Exception as error:raise SystemExit('更新未推送：'+str(error))
