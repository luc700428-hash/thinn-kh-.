#!/usr/bin/env python3
"""Official NLSC administrative names + GeoNames CC BY 4.0 representative coordinates."""
import json,urllib.request,concurrent.futures,xml.etree.ElementTree as E
from pathlib import Path
import io,zipfile
ROOT=Path(__file__).resolve().parents[1]
with urllib.request.urlopen('https://download.geonames.org/export/dump/TW.zip',timeout=60) as response:
 geonames_text=zipfile.ZipFile(io.BytesIO(response.read())).read('TW.txt').decode('utf-8')
by={}
all_adm=[]
for line in geonames_text.splitlines():
 p=line.split('\t')
 if p[7]=='ADM3':by[p[12]]=p;all_adm.append(p)

def get(path):
 for attempt in range(3):
  try:return E.fromstring(urllib.request.urlopen('https://api.nlsc.gov.tw/other/'+path,timeout=25).read())
  except Exception:
   if attempt==2:raise
counties=get('ListCounty');items=[]
for c in counties:
 d={i.tag:i.text for i in c};items.append((d['countycode'],d['countyname']))
def town(item):
 code,name=item;out=[];missing=[]
 for node in get('ListTown1/'+code):
  d={i.tag:i.text for i in node};key=d.get('towncode01');p=by.get(key)
  if not p and code=='D':
   found=[v for v in all_adm if v[11]=='TNN' and d.get('townname') in v[3].split(',')]
   p=found[0] if len(found)==1 else None
  if not p:missing.append((key,name,d.get('townname')));continue
  aliases=list(dict.fromkeys([p[1],p[2]]+p[3].split(',')));aliases=[x for x in aliases if x][:18]
  out.append(dict(id='tw-town-'+key,name='台灣 · '+name+' · '+d['townname'],lat=float(p[4]),lon=float(p[5]),aliases=aliases))
 return out,missing
rows=[];missing=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 for a,b in pool.map(town,items):rows+=a;missing+=b
print('Townships:',len(rows),'Missing:',missing,flush=True)
assert len(rows)>=360
path=ROOT/'app/src/main/assets/locations/taiwan_townships.json';path.parent.mkdir(exist_ok=True)
path.write_text(json.dumps(dict(schema=1,generated='2026-10-27',note='Representative township coordinates, not user GPS or exact street addresses.',sources=[dict(name='National Land Surveying and Mapping Center: county/township names',url='https://api.nlsc.gov.tw/other/ListCounty'),dict(name='GeoNames: representative coordinates and aliases',url='https://download.geonames.org/export/dump/TW.zip',license='CC BY 4.0',attribution='GeoNames.org')],places=sorted(rows,key=lambda x:x['id'])),ensure_ascii=False,indent=2)+'\n')
