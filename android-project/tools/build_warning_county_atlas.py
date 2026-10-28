#!/usr/bin/env python3
"""Build a named offline warning atlas from the official NLSC county SHP."""
from pathlib import Path
import gzip,hashlib,json,sys
import shapefile
from shapely.geometry import shape
from shapely import make_valid

def build(source,destination):
    source=Path(source);destination=Path(destination);destination.mkdir(parents=True,exist_ok=True)
    reader=shapefile.Reader(str(source),encoding='utf-8');counties=[]
    for feature in reader.iterShapeRecords():
        attributes=feature.record.as_dict();geometry=make_valid(shape(feature.shape.__geo_interface__))
        polygons=list(geometry.geoms) if geometry.geom_type in ('MultiPolygon','GeometryCollection') else [geometry]
        polygons=[p for p in polygons if p.geom_type=='Polygon']
        label=max(polygons,key=lambda p:p.area).representative_point()
        rings=[]
        for polygon in polygons:
            p=polygon.simplify(.00025,preserve_topology=True)
            for ring in [p.exterior,*p.interiors]:
                points=[[round(float(x),6),round(float(y),6)] for x,y in ring.coords]
                if len(points)>=4:rings.append(points)
        counties.append({'name':attributes['COUNTYNAME'],'code':attributes['COUNTYCODE'],
            'label':[round(label.x,6),round(label.y,6)],'rings':rings})
    assert len(counties)==22 and len({c['name'] for c in counties})==22
    raw=json.dumps({'schema':1,'counties':counties},ensure_ascii=False,separators=(',',':')).encode()
    output=destination/'warning_counties.tkatlasz'
    output.write_bytes(gzip.compress(raw,compresslevel=9,mtime=0))
    meta={'title':'警特報縣市識別向量底圖','authority':'內政部國土測繪中心','source_dataset':'COUNTY_MOI_1090820',
      'source':'https://maps.nlsc.gov.tw/download/縣市界線(TWD97經緯度).zip','catalog':'https://data.gov.tw/dataset/7442',
      'license':'政府資料開放授權條款第1版','license_url':'https://data.gov.tw/license','source_crs':'EPSG:3824',
      'source_shp_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'atlas_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
      'tolerance_degrees':.00025,'counties':22,'rings':sum(len(c['rings']) for c in counties),
      'vertices':sum(len(r) for c in counties for r in c['rings']),
      'note':'保存具名行政區與離島輪廓。圖形只代表行政界線；警特報涉及縣市由氣象署資料指定，局部範圍依官方原文。'}
    (destination/'warning_counties.metadata.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'bytes':output.stat().st_size,**meta},ensure_ascii=False))
if __name__=='__main__':build(*sys.argv[1:])
