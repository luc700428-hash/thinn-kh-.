#!/usr/bin/env python3
"""Build the offline sky data used by 星空與銀河 (app/src/main/assets/sky).

Source: d3-celestial by Olaf Frohn (BSD 3-Clause), whose star list comes from the Yale Bright
Star / HYG catalogues. Run once by hand when the data should be refreshed:

    python3 tools/build_sky_asset.py <folder with stars.6.json, constellations.json,
        constellations.lines.json, mw.json, starnames.json> app/src/main/assets/sky

Outputs (all small text files, read once when the page opens):
    stars.tsv           ra° dec° magnitude B−V [Chinese name] [constellation]
    lines.tsv           constellation  ra,dec;ra,dec|...   (stick figures)
    constellations.tsv  id  Chinese name  ra°  dec°  rank
    milkyway.tsv        level ra° dec°   (Milky Way sampled on an even grid over the sphere)
"""
import json, math, sys
from pathlib import Path

# Common Taiwanese names of the bright stars, by their IAU proper name.
CHINESE = {
    'Sirius': '天狼星', 'Canopus': '老人星', 'Arcturus': '大角星', 'Rigil Kentaurus': '南門二', 'Vega': '織女星',
    'Capella': '五車二', 'Rigel': '參宿七', 'Procyon': '南河三', 'Achernar': '水委一', 'Betelgeuse': '參宿四',
    'Hadar': '馬腹一', 'Altair': '牛郎星', 'Acrux': '十字架二', 'Aldebaran': '畢宿五', 'Spica': '角宿一',
    'Antares': '心宿二', 'Pollux': '北河三', 'Fomalhaut': '北落師門', 'Deneb': '天津四', 'Mimosa': '十字架三',
    'Regulus': '軒轅十四', 'Adhara': '弧矢七', 'Castor': '北河二', 'Gacrux': '十字架一', 'Shaula': '尾宿八',
    'Bellatrix': '參宿五', 'Elnath': '五車五', 'Alnilam': '參宿二', 'Alnitak': '參宿一', 'Mintaka': '參宿三',
    'Saiph': '參宿六', 'Alioth': '玉衡', 'Dubhe': '天樞', 'Merak': '天璇', 'Phecda': '天璣', 'Megrez': '天權',
    'Mizar': '開陽', 'Alkaid': '搖光', 'Mirfak': '天船三', 'Kaus Australis': '箕宿三', 'Wezen': '弧矢一',
    'Polaris': '北極星', 'Kochab': '北極二', 'Alphard': '星宿一', 'Algieba': '軒轅十二', 'Denebola': '五帝座一',
    'Hamal': '婁宿三', 'Nunki': '斗宿四', 'Alpheratz': '壁宿二', 'Mirach': '奎宿九', 'Almach': '天大將軍一',
    'Algol': '大陵五', 'Albireo': '輦道增七', 'Sadr': '天津一', 'Alcyone': '昴宿六', 'Markab': '室宿一',
    'Scheat': '室宿二', 'Algenib': '壁宿一', 'Enif': '危宿三', 'Schedar': '王良四', 'Caph': '王良一',
    'Ruchbah': '閣道三', 'Mira': '蒭藁增二', 'Rasalhague': '候', 'Eltanin': '天棓四', 'Alderamin': '天鉤五',
    'Menkar': '天囷一', 'Zubenelgenubi': '氐宿一', 'Zubeneschamali': '氐宿四', 'Unukalhai': '天市右垣七',
    'Sargas': '尾宿五', 'Menkent': '庫樓三', 'Peacock': '孔雀十一', 'Alnair': '鶴一', 'Avior': '海石一',
    'Miaplacidus': '南船五', 'Atria': '三角形三', 'Diphda': '土司空', 'Alhena': '井宿三', 'Mirzam': '軍市一',
    'Menkalinan': '五車三', 'Thuban': '右樞', 'Izar': '梗河一', 'Vindemiatrix': '東次將', 'Porrima': '東上相',
    'Arneb': '廁一', 'Nihal': '廁二', 'Kaus Media': '箕宿二', 'Ascella': '斗宿六', 'Dschubba': '房宿三',
    'Acrab': '房宿四', 'Rasalgethi': '帝座', 'Cor Caroli': '常陳一', 'Muphrid': '右攝提一', 'Alphecca': '貫索四',
    'Tarazed': '河鼓三', 'Sheliak': '漸台二', 'Sulafat': '漸台三', 'Electra': '昴宿一', 'Maia': '昴宿四',
}

CONSTELLATION_FIX = {'飛鱼座': '飛魚座', '蝘蜒座': '蝘蜓座'}


def ra360(lon):
    return lon % 360.0


def build(src: Path, out: Path):
    out.mkdir(parents=True, exist_ok=True)
    stars = json.loads((src / 'stars.6.json').read_text())['features']
    names = json.loads((src / 'starnames.json').read_text())
    rows = []
    for f in stars:
        lon, lat = f['geometry']['coordinates']
        p = f['properties']
        try:
            bv = float(p.get('bv') or 0.6)
        except ValueError:
            bv = 0.6
        info = names.get(str(f['id']), {})
        zh = CHINESE.get(info.get('name') or '', '')
        con = info.get('c', '')
        rows.append((p['mag'], f"{ra360(lon):.3f}\t{lat:.3f}\t{p['mag']:.2f}\t{bv:.2f}\t{zh}\t{con}"))
    rows.sort()
    (out / 'stars.tsv').write_text('\n'.join(r for _, r in rows) + '\n')

    cons = json.loads((src / 'constellations.json').read_text())['features']
    seen = set(); lines = []
    for f in cons:
        cid = f['id']
        if cid in seen:  # 巨蛇座 has two parts with one name
            continue
        seen.add(cid)
        zh = CONSTELLATION_FIX.get(f['properties']['zh'], f['properties']['zh'])
        lon, lat = f['geometry']['coordinates']
        lines.append(f"{cid}\t{zh}\t{ra360(lon):.2f}\t{lat:.2f}\t{f['properties'].get('rank', '3')}")
    (out / 'constellations.tsv').write_text('\n'.join(lines) + '\n')

    figs = json.loads((src / 'constellations.lines.json').read_text())['features']
    lines = []
    for f in figs:
        parts = ['|'.join(';'.join(f"{ra360(a):.2f},{b:.2f}" for a, b in seg) for seg in f['geometry']['coordinates'])]
        lines.append(f"{f['id']}\t{parts[0]}")
    (out / 'lines.tsv').write_text('\n'.join(lines) + '\n')

    (out / 'milkyway.tsv').write_text(milky_way(json.loads((src / 'mw.json').read_text())))


def milky_way(mw, spacing_deg=1.3):
    """Samples the five nested brightness levels on an even (Fibonacci) grid over the sphere.

    A point is inside a level when a meridian ray from it to the north celestial pole crosses that
    level's ring edges an odd number of times; this works for the two rings that circle the whole
    sky (the band's edges) as well as for the small islands and holes.
    """
    import numpy as np
    n = int(4 * math.pi / math.radians(spacing_deg) ** 2)
    i = np.arange(n) + .5
    lat = np.degrees(np.arcsin(1 - 2 * i / n))
    lon = (np.degrees(math.pi * (1 + 5 ** .5) * i)) % 360
    level = np.zeros(n, dtype=int)
    for k, f in enumerate(mw['features'], start=1):
        inside = np.zeros(n, dtype=bool)
        for ring in f['geometry']['coordinates'][0]:
            r = np.array(ring, dtype=float)
            a, b = r[:-1], r[1:]
            for s in range(0, len(a), 1500):
                a1, b1 = a[s:s + 1500], b[s:s + 1500]
                d = (b1[:, 0] - a1[:, 0] + 180) % 360 - 180            # wrapped lon step
                rel = (lon[:, None] - a1[None, :, 0]) % 360             # point lon after the edge start
                rel = np.where(d[None, :] < 0, rel - 360, rel)          # measured the same way as the step
                t = rel / np.where(d == 0, 1e-9, d)[None, :]
                hit = (t >= 0) & (t < 1) & (d[None, :] != 0)
                edge_lat = a1[None, :, 1] + t * (b1[None, :, 1] - a1[None, :, 1])
                inside ^= (np.count_nonzero(hit & (edge_lat > lat[:, None]), axis=1) % 2).astype(bool)
        level = np.where(inside, k, level)
    keep = level > 0
    return ''.join(f"{l}\t{a:.1f}\t{b:.1f}\n" for l, a, b in zip(level[keep], lon[keep], lat[keep]))


if __name__ == '__main__':
    build(Path(sys.argv[1]), Path(sys.argv[2]))
