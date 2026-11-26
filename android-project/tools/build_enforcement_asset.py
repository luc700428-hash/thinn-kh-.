#!/usr/bin/env python3
"""Builds the 駕車模式 enforcement assets in app/src/main/assets/roads/.

  build_enforcement_asset.py fetch  ASSET_DIR              download every source (CI runs this before a release)
  build_enforcement_asset.py osm    ASSET_DIR enf.json     add OpenStreetMap zones / red lights (Overpass:
                                                           relation["type"="enforcement"];out body;>;out skel qt;)
  build_enforcement_asset.py check  ASSET_DIR              validate what is there

speed_cameras.csv   內政部警政署「測速執法設置點」(data.gov.tw/dataset/7320), kept in the source's own
                    format because the app re-downloads that file weekly.
enforcement.csv     Every other official list, normalised to one table:
                      kind,lat,lon,direction,limit,label,items,source,end_lat,end_lon
                    kind: SPEED (超速), ZONE (區間測速, end_* where the source gives it), TECH (路口多功能:
                    闖紅燈、轉彎、不停讓行人…), RED (匝道/號誌闖紅燈), RAIL (平交道).

Each county publishes its own CSV/JSON with its own columns (and some swap latitude and
longitude), so columns are found by name and every point must fall inside Taiwan. A source that
cannot be reached or parsed keeps its rows from the previous file, so one county's outage never
empties the map; CI runs `fetch` before building a release.
"""
import csv, io, json, math, re, sys, urllib.request
from pathlib import Path

MOI_FIXED = "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/EA5E6FCD-B82D-43B7-A5CF-E9893253187E/resource/1C3D4C5D-44D4-418C-8CD8-0971D4BE9BE2/download"

# (data.gov.tw id, default kind, urls tried in order). Kaohsiung serves JSON from openapi.kcg.gov.tw.
SOURCES = [
    ("100856", "RED", ["https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/EF974C67-917B-43DA-A49C-F1480EEEDB23/resource/C1EC920B-69C5-4A59-B965-2C15A5708C66/download"]),
    ("31908", "RAIL", ["https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/55F2095B-8557-426A-BD98-EE7AAD7BED66/resource/3628D20C-4831-4441-ABA2-1268D7ABEF4F/download"]),
    ("135957", "TECH", ["https://data.taipei/api/dataset/986fa73e-c470-4ebf-9f35-3a1c9d2a8788/resource/4715904f-6ce1-41c2-8a68-3bc5303f3607/download"]),
    ("126156", "ZONE", ["https://data.ntpc.gov.tw/api/datasets/27b97ad9-9dba-4ca9-b0ed-14b29000ffec/csv/file"]),
    ("178168", "TECH", ["https://opendata.tycg.gov.tw/api/dataset/46bd8de0-cb90-4149-a530-5f5b1751b450/resource/636bb62e-cc5b-4ad4-ac11-5849ad5c432d/download"]),
    ("170673", "TECH", ["https://newdatacenter.taichung.gov.tw/api/v1/no-auth/resource.download?rid=b3f58c63-53b3-4c01-8203-2a7afa08bb13"]),
    ("172725", "ZONE", ["https://newdatacenter.taichung.gov.tw/api/v1/no-auth/resource.download?rid=e968989a-bfb2-4b6c-8bd0-1b1af3c54745"]),
    ("172905", "TECH", ["https://www.chpb.gov.tw/opendata/deJgXKJjLESCdBNje_-bYA/csv"]),
    ("178161", "ZONE", ["https://www.chpb.gov.tw/opendata/iIqaLCWmEU-cNkBdBB-CAQ/csv"]),
    ("52544", "TECH", ["https://data.chiayi.gov.tw/opendata/api/getResource?oid=26242d8c-9340-4553-a67f-5084a37552d4&rid=4adcea0f-ee46-4a9f-bb6e-8f4f3a1817bb"]),
    ("178121", "TECH", ["https://ws.kinmen.gov.tw/001/Upload/0/relfile/0/0/9eaa309e-e67d-42ca-bd22-518d1dc39bb5.csv"]),
    ("178120", "ZONE", ["https://ws.kinmen.gov.tw/001/Upload/0/relfile/0/0/5ad94bbb-f590-4e3e-b779-5c3ac8a2135f.csv"]),
    ("178357", "TECH", ["https://ws.yunlin.gov.tw/001/Upload/539/opendata/0/2491/be1537c9-659a-46a6-940b-0b89e2c594fa.json", "https://ws.yunlin.gov.tw/001/Upload/539/opendata/0/2491/be1537c9-659a-46a6-940b-0b89e2c594fa.csv"]),
    ("178140", "TECH", ["https://ws-tm.cyhg.gov.tw/001/Upload/1330/relfile/13620/256051/b66743e4-ca79-4ae3-a14f-6a2a42b1b103.csv"]),
    ("159972", "TECH", ["https://www-ws.pthg.gov.tw/Upload/2015pthg/0/relfile/0/0/6a3e921a-ff5f-4de3-90ab-1d2094ad1170.json", "https://www-ws.pthg.gov.tw/Upload/2015pthg/0/relfile/0/0/6a3e921a-ff5f-4de3-90ab-1d2094ad1170.csv"]),
    ("178144", "TECH", ["https://odws.hccg.gov.tw/001/Upload/25/opendataback/9059/2006/51a0af55-4a9f-43fb-86cc-5ea09ab58bef.json", "https://odws.hccg.gov.tw/001/Upload/25/opendataback/9059/2006/51a0af55-4a9f-43fb-86cc-5ea09ab58bef.csv"]),
    ("109336", "TECH", ["https://ws.hsinchu.gov.tw/001/Upload/1/opendata/8774/297/f65385d7-f178-4cee-8ccc-053c14aadfdb.json", "https://ws.hsinchu.gov.tw/001/Upload/1/opendata/8774/297/f65385d7-f178-4cee-8ccc-053c14aadfdb.csv"]),
    ("172950", "ZONE", ["https://ws.hsinchu.gov.tw/001/Upload/1/opendata/8774/2759/acf3a77d-470e-4420-90c0-64bf860fa875.json", "https://ws.hsinchu.gov.tw/001/Upload/1/opendata/8774/2759/acf3a77d-470e-4420-90c0-64bf860fa875.csv"]),
    ("172940", "TECH", ["https://opendata.penghu.gov.tw/dataset/bbb46c6f-dd1a-4ca4-b4e4-ef25dab065fc/resource/ecebae77-66fb-4228-83cb-d3e57383dfa2/download/pb140300-2025-02-21-1740105639.json", "https://opendata.penghu.gov.tw/dataset/bbb46c6f-dd1a-4ca4-b4e4-ef25dab065fc/resource/4666f04f-88ef-4ca9-8cad-d4ee82c76d7e/download/pb140300-2025-02-21-1740105639.csv"]),
    ("176021", "TECH", ["https://data.nantou.gov.tw/dataset/1f5bdf50-d743-4f65-96e0-cd5e8cbd7441/resource/f9cb2592-d3cf-4ce2-b6b2-ea96d155394b/download/1150724.csv"]),
    ("178159", "TECH", ["https://www.klg.gov.tw/cht/upload/TechArea.csv", "https://www.klg.gov.tw/cht/upload/TechAreaAvgRate.csv"]),
    ("176558", "TECH", ["https://openapi.kcg.gov.tw/Api/Service/Get/e67e1f3d-94ca-461d-9e59-10bc10d6a0bf", "https://data.kcg.gov.tw/File/DirectDownload/e67e1f3d-94ca-461d-9e59-10bc10d6a0bf", "https://openapi.kcg.gov.tw/Api/Service/Get/181e0e72-b21a-4fc6-b377-554053bb4215", "https://data.kcg.gov.tw/File/DirectDownload/181e0e72-b21a-4fc6-b377-554053bb4215"]),
    ("176561", "TECH", ["https://openapi.kcg.gov.tw/Api/Service/Get/fb29936f-c4ff-4fcf-8d22-24aa62a558cb", "https://data.kcg.gov.tw/File/DirectDownload/fb29936f-c4ff-4fcf-8d22-24aa62a558cb", "https://openapi.kcg.gov.tw/Api/Service/Get/ee8223ca-c5d3-4c51-a123-2654c543e76a", "https://data.kcg.gov.tw/File/DirectDownload/ee8223ca-c5d3-4c51-a123-2654c543e76a"]),
    ("176555", "TECH", ["https://openapi.kcg.gov.tw/Api/Service/Get/d6903a09-3613-4caa-8575-59a049333d86", "https://data.kcg.gov.tw/File/DirectDownload/d6903a09-3613-4caa-8575-59a049333d86", "https://openapi.kcg.gov.tw/Api/Service/Get/6e200eb8-223a-47bb-9416-8f6ed58d28fa", "https://data.kcg.gov.tw/File/DirectDownload/6e200eb8-223a-47bb-9416-8f6ed58d28fa"]),
    ("177827", "TECH", ["https://openapi.kcg.gov.tw/Api/Service/Get/b026de91-efa3-46bb-8b95-4c20c8854936", "https://data.kcg.gov.tw/File/DirectDownload/b026de91-efa3-46bb-8b95-4c20c8854936", "https://openapi.kcg.gov.tw/Api/Service/Get/cbc5c9ac-8e34-407e-b759-af6b4aed4a24", "https://data.kcg.gov.tw/File/DirectDownload/cbc5c9ac-8e34-407e-b759-af6b4aed4a24"]),
    ("176560", "TECH", ["https://openapi.kcg.gov.tw/Api/Service/Get/47a810f7-8a2e-4d50-b25e-3f61da15b527", "https://data.kcg.gov.tw/File/DirectDownload/47a810f7-8a2e-4d50-b25e-3f61da15b527", "https://openapi.kcg.gov.tw/Api/Service/Get/93516c0b-64a1-4194-a190-d8a67ca04860", "https://data.kcg.gov.tw/File/DirectDownload/93516c0b-64a1-4194-a190-d8a67ca04860"]),
    ("169081", "ZONE", ["https://openapi.kcg.gov.tw/Api/Service/Get/d96f572a-731d-434a-9f92-0177ca2de31b", "https://data.kcg.gov.tw/File/directDownload/d96f572a-731d-434a-9f92-0177ca2de31b", "https://openapi.kcg.gov.tw/Api/Service/Get/03d76cca-f335-443b-b87a-43717f23b436", "https://data.kcg.gov.tw/File/directDownload/03d76cca-f335-443b-b87a-43717f23b436"]),
]

FIELDS = ["kind", "lat", "lon", "direction", "limit", "label", "items", "source", "end_lat", "end_lon"]
TECH_WORDS = ("闖紅燈", "轉彎", "迴轉", "讓行人", "車不讓人", "號誌", "標線", "淨空", "跨越", "車道", "駛入", "禁行", "禁駛", "多功能")


def in_taiwan(lat, lon): return 21.5 <= lat <= 26.5 and 118.0 <= lon <= 122.5


def number(s):
    m = re.search(r"-?\d+(?:\.\d+)?", str(s or ""))
    return float(m.group()) if m else None


def decode(b):
    for enc in ("utf-8-sig", "cp950", "big5", "utf-16"):
        try: return b.decode(enc)
        except UnicodeDecodeError: pass
    return b.decode("utf-8", "replace")


def table(raw):
    """Rows as dicts from CSV or JSON (a list, or a dict holding one)."""
    text = decode(raw).lstrip("﻿").strip()
    if text[:1] in "[{":
        data = json.loads(text)
        if isinstance(data, dict):
            data = next((v for v in data.values() if isinstance(v, list)), [])
        return [{str(k): ("" if v is None else str(v)) for k, v in r.items()} for r in data if isinstance(r, dict)]
    if text.lower().startswith("<"): return []
    return list(csv.DictReader(io.StringIO(text)))


def key(h): return re.sub(r"\s+", "", re.sub(r"\(.*?\)|（.*?）", "", h.lstrip("﻿"))).lower()


def col(cols, *needles, avoid=()):
    for n in needles:
        for c in cols:
            k = key(c)
            if n in k and not any(a in k for a in avoid): return c
    return None


def normalise(sid, kind, rows):
    if not rows: return []
    cols = list(rows[0].keys())
    lat_c = col(cols, "startlatitude", "座標緯度", "緯度", "latitude", "lat", "n-wgs84", avoid=("end",))
    lon_c = col(cols, "startlongitude", "座標經度", "經度", "longitude", "lon", "e-wgs84", avoid=("end", "經緯"))
    pair_c = col(cols, "經緯度", "座標")
    end_lat = col(cols, "endlatitude"); end_lon = col(cols, "endlongitude")
    label_c = col(cols, "設置地點", "設置位置", "地點", "location", "address", "路段", "位置", avoid=("描述",))
    dir_c = col(cols, "拍攝方向", "測照行向", "取締行向", "方向", "行向", "direct")
    lim_c = col(cols, "速限", "limit")
    item_c = col(cols, "取締項目", "違規取締項目", "item", "ban", "項目")
    type_c = col(cols, "科技執法種類", "種類", "型式", "設備型式")
    out = []
    for r in rows:
        lat = lon = None
        if lat_c and lon_c: lat, lon = number(r.get(lat_c)), number(r.get(lon_c))
        if (lat is None or lon is None) and pair_c:
            nums = [float(x) for x in re.findall(r"\d+\.\d+", r.get(pair_c, ""))]
            if len(nums) >= 2: lat, lon = nums[0], nums[1]
        if lat is None or lon is None: continue
        if lat > 90 and lon < 90: lat, lon = lon, lat           # columns swapped at the source
        if not in_taiwan(lat, lon): continue
        label = (r.get(label_c) or "").strip() if label_c else ""
        items = "、".join(x for x in ((r.get(type_c) or "").strip() if type_c else "", (r.get(item_c) or "").strip() if item_c else "") if x)
        text = label + items + (r.get(dir_c, "") if dir_c else "")
        if re.fullmatch(r"[、;,\s]*(違規停車|違停|停車)[、;,\s]*", (r.get(item_c) or "") if item_c else ""): continue
        k = kind
        if "區間" in text or kind == "ZONE": k = "ZONE"
        elif kind == "TECH" and not any(w in text for w in TECH_WORDS) and ("超速" in text or "測速" in text): k = "SPEED"
        limit = number(r.get(lim_c)) if lim_c else None
        if limit is None: limit = number(re.search(r"速限\s*(\d+)", text).group(1)) if re.search(r"速限\s*(\d+)", text) else None
        el = number(r.get(end_lat)) if end_lat else None; eo = number(r.get(end_lon)) if end_lon else None
        if el is not None and eo is not None and el > 90: el, eo = eo, el
        if el is not None and eo is not None and not in_taiwan(el, eo): el = eo = None
        out.append({"kind": k, "lat": f"{lat:.6f}", "lon": f"{lon:.6f}", "direction": (r.get(dir_c) or "").strip() if dir_c else "",
                    "limit": str(int(limit)) if limit and 10 <= limit <= 130 else "", "label": label[:80] or items[:40],
                    "items": items[:80], "source": sid, "end_lat": f"{el:.6f}" if el is not None else "", "end_lon": f"{eo:.6f}" if eo is not None else ""})
    return out


def get(url, timeout=40):
    req = urllib.request.Request(url, headers={"User-Agent": "thinn-khi-data/1 (+https://github.com/luc700428-hash/thinn-kh-.)"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        if r.status != 200: raise IOError(r.status)
        return r.read(30_000_000)


def fetch(asset_dir):
    asset_dir = Path(asset_dir)
    old = {}
    target = asset_dir / "enforcement.csv"
    if target.exists():
        for r in csv.DictReader(open(target, encoding="utf-8")): old.setdefault(r["source"], []).append(r)
    try:
        moi = get(MOI_FIXED)
        text = decode(moi)
        if text.lstrip("﻿").startswith("CityName,") and text.count("\n") > 1000:
            (asset_dir / "speed_cameras.csv").write_bytes(moi); print("speed_cameras.csv refreshed")
    except Exception as e:
        print("MOI fixed list kept:", e)
    rows = []
    for sid, kind, urls in SOURCES:
        got = []
        # Several files can make up one list (or be the JSON and CSV of it): take every one that
        # parses; duplicates go in write().
        for u in urls:
            try:
                got += normalise(sid, kind, table(get(u)))
            except Exception as e:
                print(f"  {sid} {u[:60]}… {e}")
        prev = old.get(sid, [])
        if got and len(got) >= len(prev) * .5:
            rows += got; print(f"{sid}: {len(got)}")
        else:
            rows += prev; print(f"{sid}: kept {len(prev)} (fetched {len(got)})")
    # Rows from elsewhere (OpenStreetMap zones, added once by `osm`) are carried over as they are.
    listed = {sid for sid, _, _ in SOURCES}
    rows += [r for sid, rs in old.items() if sid not in listed for r in rs]
    write(asset_dir, rows)


def osm(asset_dir, osm_json):
    """Adds OpenStreetMap 區間測速 zones (from/to) and red-light cameras as source "osm"."""
    d = json.load(open(osm_json))["elements"]
    nodes = {e["id"]: e for e in d if e["type"] == "node"}
    rows = [r for r in csv.DictReader(open(Path(asset_dir) / "enforcement.csv", encoding="utf-8")) if r["source"] != "osm"]
    for e in d:
        if e["type"] != "relation": continue
        t = e.get("tags", {}); role = {}
        for m in e["members"]:
            if m["type"] == "node" and m["ref"] in nodes: role.setdefault(m["role"], nodes[m["ref"]])
        if t.get("enforcement") == "average_speed" and "from" in role and "to" in role and (t.get("maxspeed") or "").isdigit():
            f, to = role["from"], role["to"]
            rows.append({"kind": "ZONE", "lat": f"{f['lat']:.6f}", "lon": f"{f['lon']:.6f}", "direction": "", "limit": t["maxspeed"],
                         "label": t.get("name", "區間測速"), "items": "區間平均速率", "source": "osm", "end_lat": f"{to['lat']:.6f}", "end_lon": f"{to['lon']:.6f}"})
        elif t.get("enforcement") == "traffic_signals" and "device" in role:
            v = role["device"]
            rows.append({"kind": "RED", "lat": f"{v['lat']:.6f}", "lon": f"{v['lon']:.6f}", "direction": "", "limit": "", "label": "闖紅燈照相",
                         "items": "闖紅燈", "source": "osm", "end_lat": "", "end_lon": ""})
    write(asset_dir, rows)


def write(asset_dir, rows):
    seen = set(); keep = []
    for r in rows:
        k = (r["kind"], r["lat"][:8], r["lon"][:9], r["direction"])
        if k in seen: continue
        seen.add(k); keep.append(r)
    with open(Path(asset_dir) / "enforcement.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, FIELDS); w.writeheader(); w.writerows(keep)
    print(len(keep), "enforcement points")


def check(asset_dir):
    rows = list(csv.DictReader(open(Path(asset_dir) / "enforcement.csv", encoding="utf-8")))
    assert rows and list(rows[0].keys()) == FIELDS
    for r in rows:
        assert r["kind"] in ("SPEED", "ZONE", "TECH", "RED", "RAIL"), r
        assert in_taiwan(float(r["lat"]), float(r["lon"])), r
        assert "中國" not in r["label"] + r["items"]
    print(len(rows), "rows OK")


if __name__ == "__main__":
    {"fetch": fetch, "check": check, "osm": osm}[sys.argv[1]](*sys.argv[2:4])
