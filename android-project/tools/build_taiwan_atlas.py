#!/usr/bin/env python3
"""Rebuild our offline atlas from NLSC's public county SHP (pyshp + shapely).
Usage: python build_taiwan_atlas.py COUNTY_MOI_1090820.shp assets/maps
The source ZIP is https://maps.nlsc.gov.tw/download/縣市界線(TWD97經緯度).zip
"""
import hashlib, json, pathlib, struct, sys
import shapefile
from shapely.geometry import Polygon

def build(source, destination):
    source, destination = pathlib.Path(source), pathlib.Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    reader = shapefile.Reader(str(source), encoding='utf-8')
    rings=[]
    for shape in reader.shapes():
        starts=list(shape.parts)+[len(shape.points)]
        for a,b in zip(starts, starts[1:]):
            coords=shape.points[a:b]
            # Retain the Taiwanese map region; remote islands are not invented or relocated.
            if len(coords)>=4 and any(117.0<=x<=124.5 and 20.5<=y<=27.0 for x,y in coords):
                rings.append(coords)
    levels=[]
    for tolerance in (.002, .0004, .00004):
        simplified=[]
        for ring in rings:
            poly=Polygon(ring).simplify(tolerance,preserve_topology=True)
            pts=list(poly.exterior.coords)
            if len(pts)>=4: simplified.append(pts)
        levels.append((tolerance,simplified))
    output=destination/'taiwan_counties.tkatlas'
    with output.open('wb') as f:
        f.write(b'TKMAP001');f.write(struct.pack('>i',len(levels)))
        for tolerance,rings_ in levels:
            f.write(struct.pack('>fi',tolerance,len(rings_)))
            for ring in rings_:
                f.write(struct.pack('>i',len(ring)))
                for x,y in ring: f.write(struct.pack('>ff',x-118.0,30.0-y))
    meta={'title':'縣市界線 TWD97 經緯度','authority':'內政部國土測繪中心',
          'source':'https://maps.nlsc.gov.tw/download/縣市界線(TWD97經緯度).zip',
          'catalog':'https://data.gov.tw/dataset/7442','license':'政府資料開放授權條款第1版',
          'license_url':'https://data.gov.tw/license','source_dataset_version':'1090820',
          'source_crs':'EPSG:3824 (TWD97 geographic)','source_shp_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
          'atlas_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
          'vertices':[sum(len(r) for r in rr) for _,rr in levels],
          'tolerance_degrees':[t for t,_ in levels],
          'note':'行政界線向量顯示；不是災害、領海或風險範圍。高倍率使用最細向量，無線上瓦片依賴。'}
    (destination/'taiwan_counties.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'bytes':output.stat().st_size,'vertices':meta['vertices']},ensure_ascii=False))
if __name__=='__main__': build(*sys.argv[1:])
