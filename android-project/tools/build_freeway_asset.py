#!/usr/bin/env python3
"""Builds app/src/main/assets/roads/freeways.txt for 駕車模式 (next interchange, service areas).

Input: two Overpass JSON files (OpenStreetMap, ODbL):
  ways.json  [out:json];(way["highway"="motorway"](21.8,119.9,25.4,122.1);
             way["highway"="trunk"]["motorroad"="yes"](21.8,119.9,25.4,122.1););out body geom;
  pts.json   [out:json];(node["highway"="motorway_junction"](...);
             nwr["highway"~"^(services|rest_area)$"](...););out tags center;
Usage: build_freeway_asset.py ways.json pts.json out.txt

Every carriageway is one-way in OSM, so a road becomes directed chains (merged between branch
points). Interchanges are the junction nodes on a chain; service areas and rest stops are
projected onto the chains running past them. Format (one record per line):
  C|id|road|next|count|lat,lon deltas (1e-5 degrees)
  I|chain|metres along|kind J/S/R/E|name|exit number|destinations
  L|chain|metres along|speed limit km/h from there on
"""
import json, math, sys, collections

def road_name(t):
    ref = (t.get('ref') or '').split(';')[0].strip()
    if not ref: return None
    if t.get('highway') == 'motorway':
        if ref.endswith('甲'): return f'國道{ref[:-1]}號甲線'
        return f'國道{ref}號'
    if ref.startswith('北') or not ref[0].isdigit(): return None
    return f'台{ref}線'

def metres(a, b):
    dy = (b[0] - a[0]) * 111_195.0
    dx = (b[1] - a[1]) * 111_195.0 * math.cos(math.radians((a[0] + b[0]) / 2))
    return math.hypot(dx, dy)

def bearing(a, b):
    y = math.sin(math.radians(b[1] - a[1])) * math.cos(math.radians(b[0]))
    x = math.cos(math.radians(a[0])) * math.sin(math.radians(b[0])) - math.sin(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.cos(math.radians(b[1] - a[1]))
    return (math.degrees(math.atan2(y, x)) + 360) % 360

def turn(a, b): return abs((a - b + 180) % 360 - 180)

def simplify(pts, keep, tol=8.0):
    """Douglas–Peucker in metres; indices in [keep] always survive."""
    if len(pts) < 3: return list(range(len(pts)))
    out = {0, len(pts) - 1} | set(keep)
    stack = [(0, len(pts) - 1)]
    lat0 = math.radians(pts[0][0])
    def xy(p): return (p[1] * 111_195.0 * math.cos(lat0), p[0] * 111_195.0)
    P = [xy(p) for p in pts]
    while stack:
        i, j = stack.pop()
        if j <= i + 1: continue
        ax, ay = P[i]; bx, by = P[j]; dx, dy = bx - ax, by - ay; L = dx * dx + dy * dy
        best, k = -1.0, -1
        for m in range(i + 1, j):
            px, py = P[m]
            if m in keep: best, k = 1e9, m; break
            t = 0 if L == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / L))
            d = math.hypot(px - ax - t * dx, py - ay - t * dy)
            if d > best: best, k = d, m
        if best > tol: out.add(k); stack += [(i, k), (k, j)]
    return sorted(out)

def main(ways_path, pts_path, out_path):
    ways = [w for w in json.load(open(ways_path))['elements'] if w['type'] == 'way' and road_name(w['tags'])]
    for w in ways:
        w['road'] = road_name(w['tags'])
        if w['tags'].get('oneway') == '-1': w['nodes'].reverse(); w['geometry'].reverse()
    starts = collections.defaultdict(list); ends = collections.defaultdict(list)
    for i, w in enumerate(ways):
        starts[(w['road'], w['nodes'][0])].append(i); ends[(w['road'], w['nodes'][-1])].append(i)
    # Merge a way into the next when the link is unambiguous on both sides.
    nxt = {}; prv = {}
    for i, w in enumerate(ways):
        s = starts.get((w['road'], w['nodes'][-1]), [])
        if len(s) == 1 and len(ends.get((w['road'], w['nodes'][-1]), [])) == 1 and s[0] != i:
            nxt[i] = s[0]; prv[s[0]] = i
    seen = set(); chains = []
    for i in range(len(ways)):
        if i in seen or i in prv: continue
        run = [i]; seen.add(i)
        while run[-1] in nxt and nxt[run[-1]] not in seen: run.append(nxt[run[-1]]); seen.add(run[-1])
        chains.append(run)
    for i in range(len(ways)):          # closed loops left over
        if i in seen: continue
        run = [i]; seen.add(i)
        while run[-1] in nxt and nxt[run[-1]] not in seen: run.append(nxt[run[-1]]); seen.add(run[-1])
        chains.append(run)
    built = []
    for run in chains:
        nodes = []; geo = []; limits = []
        for k, wi in enumerate(run):
            w = ways[wi]; ns = w['nodes']; gs = [(g['lat'], g['lon']) for g in w['geometry']]
            limits.append((len(geo) - (1 if k else 0), w['tags'].get('maxspeed', '')))
            if k: ns, gs = ns[1:], gs[1:]
            nodes += ns; geo += gs
        built.append({'road': ways[run[0]]['road'], 'nodes': nodes, 'geo': geo, 'first': run[0], 'last': run[-1], 'limits': limits})
    # Main continuation at a branch: the same road, the smallest turn.
    by_first = collections.defaultdict(list)
    for ci, c in enumerate(built): by_first[(c['road'], c['nodes'][0])].append(ci)
    for c in built:
        cands = [ci for ci in by_first.get((c['road'], c['nodes'][-1]), []) if built[ci] is not c]
        endb = bearing(c['geo'][max(0, len(c['geo']) - 3)], c['geo'][-1]) if len(c['geo']) > 1 else 0
        def score(ci):
            g = built[ci]['geo']; return turn(endb, bearing(g[0], g[min(2, len(g) - 1)])) if len(g) > 1 else 180
        c['next'] = min(cands, key=score) if cands else -1
    pts = json.load(open(pts_path))['elements']
    junctions = {e['id']: e for e in pts if e['type'] == 'node' and e['tags'].get('highway') == 'motorway_junction'}
    node_at = {}
    for ci, c in enumerate(built):
        for k, n in enumerate(c['nodes']): node_at.setdefault(n, []).append((ci, k))
    items = collections.defaultdict(list)
    keep = collections.defaultdict(set)
    for nid, e in junctions.items():
        name = e['tags'].get('name') or e['tags'].get('name:zh')
        if not name: continue
        for ci, k in node_at.get(nid, []):
            dest = (e['tags'].get('exit_to') or e['tags'].get('destination') or '').replace(';', '、')
            items[ci].append(['J', k, name, e['tags'].get('ref', ''), dest]); keep[ci].add(k)
    # Service areas and rest stops: named ones, projected onto chains within 350 m.
    places = {}
    for e in pts:
        t = e['tags']; hw = t.get('highway')
        if hw not in ('services', 'rest_area'): continue
        name = (t.get('name') or '').split('(')[0].split('（')[0].strip()
        if not (name.endswith('服務區') or name.endswith('休息站')): continue
        c = e.get('center') or e
        places.setdefault(name, []).append((c['lat'], c['lon']))
    for name, spots in places.items():
        kind = 'S' if name.endswith('服務區') else 'R'
        for ci, c in enumerate(built):
            best = None
            for spot in spots:
                for k, p in enumerate(c['geo']):
                    d = metres(p, spot)
                    if d < 350 and (best is None or d < best[0]): best = (d, k)
            if best: items[ci].append([kind, best[1], name, '', '']); keep[ci].add(best[1])
    out = ['# thinn-khì freeways and expressways for 駕車模式. Data © OpenStreetMap contributors (ODbL).']
    remap = {}
    order = [ci for ci, c in enumerate(built) if len(c['geo']) > 1]
    for new, ci in enumerate(order): remap[ci] = new
    total = 0
    for ci in order:
        c = built[ci]
        for start, _ in c['limits']: keep[ci].add(max(0, start))
        idx = simplify(c['geo'], keep[ci])
        pos = {k: n for n, k in enumerate(idx)}
        cum = [0.0]
        for a, b in zip(c['geo'], c['geo'][1:]): cum.append(cum[-1] + metres(a, b))
        g = [c['geo'][k] for k in idx]; total += len(g)
        prev = (0, 0); enc = []
        for la, lo in g:
            q = (round(la * 1e5), round(lo * 1e5)); enc.append(f'{q[0] - prev[0]},{q[1] - prev[1]}'); prev = q
        out.append(f"C|{remap[ci]}|{c['road']}|{remap.get(c['next'], -1)}|{len(g)}|{';'.join(enc)}")
        # Offsets are measured on the simplified line the app walks.
        scum = [0.0]
        for a, b in zip(g, g[1:]): scum.append(scum[-1] + metres(a, b))
        # Speed limit (km/h) from each way's maxspeed, written where it changes.
        prev_limit = None
        for start, ms in c['limits']:
            v = int(ms) if ms.isdigit() else None
            if v and v != prev_limit:
                out.append(f"L|{remap[ci]}|{round(scum[pos[max(0, start)]])}|{v}"); prev_limit = v
        last = {}
        for kind, k, name, ref, dest in sorted(items[ci], key=lambda x: x[1]):
            if name.endswith('服務區'): kind, ref = 'S', ''
            elif name.endswith('休息站'): kind, ref = 'R', ''
            elif name.endswith('端'): kind = 'E'
            at = round(scum[pos[k]])
            # One line per place: split exits (49A/49B) and the junction node next to a service area.
            if name in last and at - last[name] < 2500: continue
            last[name] = at
            out.append(f"I|{remap[ci]}|{at}|{kind}|{name}|{ref}|{dest}")
    open(out_path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print(len(order), 'chains', total, 'points', sum(len(v) for v in items.values()), 'items')

if __name__ == '__main__': main(*sys.argv[1:4])
