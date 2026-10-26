#!/usr/bin/env python3
"""Three-way text merge plus prevalidated binary payloads from the full source snapshot."""
import hashlib,json,subprocess,sys,shutil
from pathlib import Path,PurePosixPath

def run(args,cwd):return subprocess.run(args,cwd=cwd,check=True,text=True)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest() if p.is_file() else None
def safe(root,relative):
    name=PurePosixPath(relative)
    if name.is_absolute() or '..' in name.parts or not name.parts:raise ValueError('Unsafe update path')
    p=root.joinpath(*name.parts)
    if p.is_symlink():raise ValueError('Unsafe update symlink')
    p.resolve().relative_to(root)
    return p

def apply(package,checkout):
    package=Path(package).resolve();checkout=Path(checkout).resolve()
    manifest=json.loads((package/'PACKAGE_MANIFEST.json').read_text())
    entries={e['path']:e for e in manifest['changes']}
    binary=set(manifest.get('binary_changes',[]))
    if not binary.issubset(entries):raise ValueError('Unknown binary payload')
    applied=[];remaining=[]
    for rel,entry in entries.items():
        p=safe(checkout,rel);current=digest(p)
        if current==entry['after_sha256']:applied.append(rel);continue
        remaining.append(rel)
        if rel in binary:
            # Detect every binary conflict/corrupt payload BEFORE changing any tracked file.
            if current!=entry['before_sha256']:raise ValueError('Binary conflict: '+rel)
            if entry['after_sha256'] is not None and digest(safe(package/'source',rel))!=entry['after_sha256']:
                raise ValueError('Binary payload integrity mismatch: '+rel)
    if not remaining:
        print('此更新包的程式碼已存在，略過重複套用。');return
    text_remaining=[p for p in remaining if p not in binary]
    if text_remaining:
        run(['git','fetch','--depth=1','origin',manifest['base_commit']],checkout)
        args=['git','apply','--3way','--index','--binary']+['--exclude='+p for p in applied]+[str(package/'UPDATE.patch')]
        run(args,checkout)
        if subprocess.run(['git','diff','--name-only','--diff-filter=U'],cwd=checkout,capture_output=True,text=True,check=True).stdout.strip():
            raise RuntimeError('上游同一段程式已有不同修改，已停止，未推送任何內容。')
    for rel in remaining:
        if rel not in binary:continue
        entry=entries[rel];target=safe(checkout,rel)
        if entry['after_sha256'] is None:target.unlink(missing_ok=True)
        else:
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(safe(package/'source',rel),target)
        run(['git','add','--',rel],checkout)
    print('更新已套用；保留目前 main 的其他變更。')
if __name__=='__main__':
    try:apply(sys.argv[1],sys.argv[2])
    except Exception as error:raise SystemExit('更新未推送：'+str(error))
