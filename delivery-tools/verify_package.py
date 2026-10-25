#!/usr/bin/env python3
"""Verify the entire delivered snapshot before using any upload credentials."""
import hashlib,json,sys
from pathlib import Path,PurePosixPath

def verify(root):
    root=Path(root).resolve()
    manifest=json.loads((root/'PACKAGE_MANIFEST.json').read_text())
    if manifest.get('schema')!=1:raise ValueError('Unsupported package manifest')
    seen=set()
    for item in manifest['files']:
        rel=PurePosixPath(item['path'])
        if rel.is_absolute() or '..' in rel.parts or not rel.parts or item['path'] in seen:
            raise ValueError('Unsafe or duplicate package path')
        seen.add(item['path']);p=root.joinpath(*rel.parts)
        if p.is_symlink() or not p.is_file():raise ValueError('Missing package file: '+str(rel))
        if hashlib.sha256(p.read_bytes()).hexdigest()!=item['sha256']:
            raise ValueError('Package integrity mismatch: '+str(rel))
    print('完整性檢查通過：',len(seen),'個檔案')
    return manifest
if __name__=='__main__':
    try:verify(Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parent)
    except Exception as error:raise SystemExit('更新包驗證失敗：'+str(error))
