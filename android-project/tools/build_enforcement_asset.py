#!/usr/bin/env python3
"""Builds the 駕車模式 enforcement assets.

  build_enforcement_asset.py moi.csv freeway_red.csv osm_enforcement.json out_dir

moi.csv          內政部警政署「測速執法設置點」(data.gov.tw/dataset/7320), copied as speed_cameras.csv
                 (the app re-downloads the same file weekly, so the format must stay the source's).
freeway_red.csv  國道公路警察局闖紅燈照相地點 (data.gov.tw/dataset/100856).
osm_enforcement  Overpass: node["highway"="speed_camera"]; relation["type"="enforcement"]; out body; >; out skel qt;
                 Only average-speed zones and red-light cameras are taken from OpenStreetMap (ODbL).
Writes enforcement_extra.txt:
  R|lat|lon|travel bearing or *|label            red-light / 科技執法 camera
  Z|id|limit|name|fromLat|fromLon|toLat|toLon     區間測速 zone, travelled from → to
"""
import csv, json, math, shutil, sys
from pathlib import Path

def bearing(a, b):
    y = math.sin(math.radians(b[1] - a[1])) * math.cos(math.radians(b[0]))
    x = math.cos(math.radians(a[0])) * math.sin(math.radians(b[0])) - math.sin(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.cos(math.radians(b[1] - a[1]))
    return round((math.degrees(math.atan2(y, x)) + 360) % 360)

def main(moi, red, osm, out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    rows = list(csv.reader(open(moi, encoding='utf-8-sig')))
    assert rows[0][:9] == ['CityName', 'RegionName', 'Address', 'DeptNm', 'BranchNm', 'Longitude', 'Latitude', 'direct', 'limit'], rows[0]
    assert len(rows) > 1000
    shutil.copyfile(moi, out / 'speed_cameras.csv')
    lines = ['# thinn-khì enforcement extras. 國道公路警察局 open data; zones and red lights © OpenStreetMap contributors (ODbL).']
    for r in csv.DictReader(open(red, encoding='utf-8-sig')):
        try: lat = float(r['WGS84_北_緯度']); lon = float(r['WGS84_東_經度'])
        except ValueError: continue
        lines.append(f"R|{lat:.6f}|{lon:.6f}|*|{r['道路編號']}{r['設置地點']}")
    d = json.load(open(osm))['elements']
    nodes = {e['id']: e for e in d if e['type'] == 'node'}
    zid = 0
    for e in d:
        if e['type'] != 'relation': continue
        t = e.get('tags', {}); kind = t.get('enforcement')
        role = {}
        for m in e['members']:
            if m['type'] == 'node' and m['ref'] in nodes: role.setdefault(m['role'], nodes[m['ref']])
        if kind == 'average_speed' and 'from' in role and 'to' in role and (t.get('maxspeed') or '').isdigit():
            zid += 1; f = role['from']; to = role['to']
            lines.append(f"Z|{zid}|{t['maxspeed']}|{t.get('name', '區間測速')}|{f['lat']:.6f}|{f['lon']:.6f}|{to['lat']:.6f}|{to['lon']:.6f}")
        elif kind == 'traffic_signals' and 'device' in role:
            dev = role['device']; frm = role.get('from')
            # The camera watches traffic coming from "from" towards the signal.
            dirn = str(bearing((frm['lat'], frm['lon']), (dev['lat'], dev['lon']))) if frm and frm is not dev else '*'
            lines.append(f"R|{dev['lat']:.6f}|{dev['lon']:.6f}|{dirn}|闖紅燈照相")
    (out / 'enforcement_extra.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(len(rows) - 2, 'fixed cameras;', sum(l.startswith('R|') for l in lines), 'red-light;', zid, 'zones')

if __name__ == '__main__': main(*sys.argv[1:5])
