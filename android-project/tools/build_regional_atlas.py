#!/usr/bin/env python3
"""Build an offline, un-simplified regional land + minor-island atlas from public-domain NE."""
from pathlib import Path
import argparse,gzip,hashlib,io,json,struct,zipfile
try:
    import shapefile
except ImportError:
    raise SystemExit('Install the generation-only dependency: python3 -m pip install pyshp==2.3.1')
ROOT=Path(__file__).resolve().parents[1]
BOUNDS=(90.0,-15.0,180.0,60.0)
def clip(ring):
    points=[(float(x),float(y)) for x,y,*_ in ring]
    if points and points[0]==points[-1]:points.pop()
    for axis,bound,above in [(0,BOUNDS[0],True),(0,BOUNDS[2],False),(1,BOUNDS[1],True),(1,BOUNDS[3],False)]:
        if not points:return []
        out=[];a=points[-1];inside=lambda p:p[axis]>=bound if above else p[axis]<=bound
        for b in points:
            ain,bin=inside(a),inside(b)
            if ain!=bin:
                t=(bound-a[axis])/(b[axis]-a[axis]);out.append((a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])))
            if bin:out.append(b)
            a=b
        points=out
    if len(points)<3:return []
    return points+[points[0]]
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir',type=Path,required=True,help='Directory containing ne_10m_land.zip and ne_10m_minor_islands.zip')
    source_dir=parser.parse_args().source_dir
    polygons=[];sources=[]
    for name,minor in [('ne_10m_land',False),('ne_10m_minor_islands',True)]:
        payload=(source_dir/(name+'.zip')).read_bytes();z=zipfile.ZipFile(io.BytesIO(payload))
        shp=next(n for n in z.namelist() if n.endswith('.shp'));reader=shapefile.Reader(shp=io.BytesIO(z.read(shp)))
        for shape in reader.iterShapes():
            x0,y0,x1,y1=shape.bbox
            if x1<BOUNDS[0] or x0>BOUNDS[2] or y1<BOUNDS[1] or y0>BOUNDS[3]:continue
            g=shape.__geo_interface__;groups=g['coordinates'] if g['type']=='MultiPolygon' else [g['coordinates']]
            for rings in groups:
                clipped=[r for r in (clip(r) for r in rings) if r]
                if not clipped:continue
                pts=[p for r in clipped for p in r]
                bounds=(min(p[0] for p in pts),min(p[1] for p in pts),max(p[0] for p in pts),max(p[1] for p in pts))
                polygons.append((minor,bounds,clipped))
        sources.append(dict(url=f'https://naturalearth.s3.amazonaws.com/10m_physical/{name}.zip',sha256=hashlib.sha256(payload).hexdigest()))
    out=io.BytesIO();out.write(b'TKREG001');out.write(struct.pack('>i',len(polygons)))
    for minor,bounds,rings in polygons:
        out.write(struct.pack('>?4fi',minor,*bounds,len(rings)))
        for ring in rings:
            out.write(struct.pack('>i',len(ring)))
            for lon,lat in ring:out.write(struct.pack('>2f',lon-100,lat))
    asset=ROOT/'app/src/main/assets/maps/west_pacific_10m.tkatlasz'
    asset.write_bytes(gzip.compress(out.getvalue(),compresslevel=9,mtime=0))
    meta=dict(source='Natural Earth physical land + minor islands',license='Public domain',license_url='https://www.naturalearthdata.com/about/terms-of-use/',bounds=list(BOUNDS),sources=sources,
              polygons=len(polygons),minor_islands=sum(1 for p in polygons if p[0]),vertices=sum(len(r) for p in polygons for r in p[2]),compressed_bytes=asset.stat().st_size,sha256=hashlib.sha256(asset.read_bytes()).hexdigest(),processing='Clip to regional extent; retain source vertices and small-island layer; no extra line simplification. Not a navigation chart.')
    (asset.parent/'west_pacific_10m.sources.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n');print(json.dumps(meta))
if __name__=='__main__':main()
