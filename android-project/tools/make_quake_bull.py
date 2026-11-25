#!/usr/bin/env python3
"""The earthquake buddy for widgets: a chibi bull with horns that jumps, lands, cracks the ground
and kicks up dust, as a seamless loop of vector frames.

Original artwork drawn here in code. The bull is 地牛, the earth ox of Taiwanese lore: it rears up and stamps. Writes res/drawable/widget_bull_fNN.xml (VectorDrawable
frames for a ViewFlipper), widget_bull_still.xml, and with --preview an SVG/PNG contact sheet.
Every quantity is a smooth periodic function of the loop phase, so the last frame flows into the
first without a jump.
"""
from pathlib import Path
import math, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/main/res/drawable'
FRAMES = 24
VIEW = 120.0
GROUND = 100.0


def ell(cx, cy, rx, ry):
    return (f"M{cx - rx:.2f},{cy:.2f}a{rx:.2f},{ry:.2f} 0 1,0 {2 * rx:.2f},0"
            f"a{rx:.2f},{ry:.2f} 0 1,0 {-2 * rx:.2f},0z")


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (f"M{x + r:.2f},{y:.2f}h{w - 2 * r:.2f}a{r:.2f},{r:.2f} 0 0,1 {r:.2f},{r:.2f}v{h - 2 * r:.2f}"
            f"a{r:.2f},{r:.2f} 0 0,1 {-r:.2f},{r:.2f}h{-(w - 2 * r):.2f}a{r:.2f},{r:.2f} 0 0,1 {-r:.2f},{-r:.2f}"
            f"v{-(h - 2 * r):.2f}a{r:.2f},{r:.2f} 0 0,1 {r:.2f},{-r:.2f}z")


def mirror(d):
    """Mirror simple absolute M/L/C/Q path data across x = 60."""
    out = []
    tokens = d.replace(',', ' ').split()
    for tok in tokens:
        if tok[0].isalpha():
            out.append(tok)
        else:
            out.append(tok)
    # Paths given to mirror() use only absolute commands with x,y pairs.
    res, i, cmd = [], 0, None
    nums = []
    for tok in d.replace(',', ' ').replace('M', ' M ').replace('L', ' L ').replace('C', ' C ').replace('Q', ' Q ').replace('Z', ' Z ').split():
        if tok in 'MLCQZ':
            if nums:
                res.append(' '.join(nums)); nums = []
            res.append(tok)
        else:
            nums.append(tok)
    if nums:
        res.append(' '.join(nums))
    final = []
    for part in res:
        if part in 'MLCQZ':
            final.append(part)
        else:
            v = [float(x) for x in part.split()]
            pts = []
            for k in range(0, len(v), 2):
                pts.append(f"{VIEW - v[k]:.2f},{v[k + 1]:.2f}")
            final.append(' '.join(pts))
    return ' '.join(final)


class Node:
    def __init__(self, kind, **kw):
        self.kind = kind; self.kw = kw; self.children = kw.pop('children', [])


def path(d, fill=None, stroke=None, width=0, alpha=1.0, trim=1.0, cap='round'):
    return Node('path', d=d, fill=fill, stroke=stroke, width=width, alpha=alpha, trim=trim, cap=cap)


def group(children, tx=0, ty=0, px=0, py=0, sx=1, sy=1, rot=0):
    return Node('group', children=children, tx=tx, ty=ty, px=px, py=py, sx=sx, sy=sy, rot=rot)


def to_vector(node, ind='  '):
    if node.kind == 'path':
        k = node.kw
        a = [f'android:pathData="{k["d"]}"']
        if k['fill']:
            a += [f'android:fillColor="{k["fill"]}"', f'android:fillAlpha="{k["alpha"]:.3f}"']
        if k['stroke']:
            a += [f'android:strokeColor="{k["stroke"]}"', f'android:strokeWidth="{k["width"]}"', f'android:strokeAlpha="{k["alpha"]:.3f}"',
                  f'android:strokeLineCap="{k["cap"]}"', 'android:strokeLineJoin="round"']
            if k['trim'] < 1:
                a.append(f'android:trimPathEnd="{k["trim"]:.3f}"')
        return f'{ind}<path {" ".join(a)}/>\n'
    k = node.kw
    inner = ''.join(to_vector(c, ind + '  ') for c in node.children)
    return (f'{ind}<group android:translateX="{k["tx"]:.3f}" android:translateY="{k["ty"]:.3f}" android:pivotX="{k["px"]:.2f}" '
            f'android:pivotY="{k["py"]:.2f}" android:scaleX="{k["sx"]:.4f}" android:scaleY="{k["sy"]:.4f}" android:rotation="{k["rot"]:.2f}">\n'
            f'{inner}{ind}</group>\n')


def to_svg(node):
    if node.kind == 'path':
        k = node.kw
        if k['fill']:
            return f'<path d="{k["d"]}" fill="{k["fill"]}" fill-opacity="{k["alpha"]:.3f}"/>'
        dash = ''
        if k['trim'] < 1:
            dash = f' pathLength="1" stroke-dasharray="{max(k["trim"], 0.0001):.3f} 2"'
        return (f'<path d="{k["d"]}" fill="none" stroke="{k["stroke"]}" stroke-width="{k["width"]}" stroke-opacity="{k["alpha"]:.3f}" '
                f'stroke-linecap="{k["cap"]}" stroke-linejoin="round"{dash}/>')
    k = node.kw
    t = (f'translate({k["tx"]:.3f},{k["ty"]:.3f}) translate({k["px"]:.2f},{k["py"]:.2f}) rotate({k["rot"]:.2f}) '
         f'scale({k["sx"]:.4f},{k["sy"]:.4f}) translate({-k["px"]:.2f},{-k["py"]:.2f})')
    return f'<g transform="{t}">' + ''.join(to_svg(c) for c in node.children) + '</g>'


# ---- motion: smooth periodic keyframes (Catmull-Rom on a loop) ----
# 地牛翻身: the earth ox stands breathing, rears up on its hind legs, and stamps down; the ground
# splits with a glow from below, stones jump, dust rolls out and it snorts.
KEYS = [  # phase, rear (deg, negative = front up), body squash, head (deg), front legs (deg), breath
    (0.000, 0.0, 1.000, 0.0, 0.0, 0.0),
    (0.120, 0.0, 1.000, 1.5, 0.0, 1.0),
    (0.220, -3.5, 1.010, -2.0, 6.0, 0.4),
    (0.330, -13.0, 1.020, -9.0, 30.0, 0.0),
    (0.430, -16.5, 1.020, -11.0, 38.0, 0.0),
    (0.510, -7.0, 1.000, -3.0, 14.0, 0.0),
    (0.565, 0.8, 0.955, 7.0, -4.0, 0.0),
    (0.620, 0.3, 0.975, 8.5, -2.0, 0.0),
    (0.700, 0.0, 1.005, 4.0, 0.0, 0.0),
    (0.800, 0.0, 1.000, 1.0, 0.0, 0.6),
    (0.900, 0.0, 1.000, 0.0, 0.0, 1.0),
]


def sample(t):
    n = len(KEYS)
    ph = [k[0] for k in KEYS]
    for i in range(n):
        a = ph[i]; b = ph[(i + 1) % n] + (1.0 if i == n - 1 else 0.0)
        tt = t if t >= a else t + 1.0
        if a <= tt <= b:
            u = (tt - a) / (b - a)
            p0, p1, p2, p3 = KEYS[(i - 1) % n], KEYS[i], KEYS[(i + 1) % n], KEYS[(i + 2) % n]
            out = []
            for j in range(1, 6):
                v0, v1, v2, v3 = p0[j], p1[j], p2[j], p3[j]
                out.append(0.5 * ((2 * v1) + (-v0 + v2) * u + (2 * v0 - 5 * v1 + 4 * v2 - v3) * u * u + (-v0 + 3 * v1 - 3 * v2 + v3) * u ** 3))
            return out
    return [0, 1, 0, 0, 0]


def smoothstep(a, b, x):
    if b == a:
        return 1.0 if x >= b else 0.0
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)


HIDE, HIDE_DARK, HIDE_LIGHT, MUZZLE = '#4B3A30', '#33271F', '#6E5545', '#8C705D'
HORN, HORN_SHADE, HORN_TIP, HOOF, EYE_GLOW = '#E2D3B3', '#B9A781', '#6E6044', '#1C1410', '#FFB23E'
STONE, STONE_LIGHT = '#6B625A', '#9A8F84'
FRONT_X = 46.0          # where the front hooves strike
HIND = (88.0, 100.0)    # the ox rears up around its hind hooves


def leg(x, top, hoof, color, bend=0.0, knee=None):
    """A sturdy leg from shoulder/hip [top] to the ground, with a fetlock and a split hoof."""
    knee = knee or (top + 100) / 2
    shape = [path(f'M{x - 4.6:.1f},{top:.1f} C{x - 5.4:.1f},{knee:.1f} {x - 3.6:.1f},{knee + 4:.1f} {x - 3.4:.1f},96 '
                  f'L{x + 3.6:.1f},96 C{x + 3.8:.1f},{knee + 4:.1f} {x + 5.4:.1f},{knee:.1f} {x + 4.8:.1f},{top:.1f} Z', fill=color),
             path(rrect(x - 4.2, 95.2, 8.4, 4.8, 1.6), fill=HOOF),
             path(f'M{x:.1f},95.6 L{x:.1f},100', stroke='#3A2C24', width=.8)]
    return group(shape, px=x, py=top + 2, rot=bend) if bend else group(shape)


def ox(t):
    rear, squash, head_rot, front_bend, breath = sample(t)
    snort = 0.565 <= t <= 0.74
    # Horns: heavy water-buffalo sweep, back and up, the far one darker.
    horn_near = 'M33.5,57.5 C35,47 44.5,40.5 56.5,42 C60.5,42.5 62,46 58.6,46.6 C49,45.4 41.5,50.5 38.6,58.6 Z'
    horn_far = 'M37,56 C40,48.5 47,44.5 55,45.2 C57.6,45.5 58.2,47.8 56,48 C49,48 43.4,52 41,57.6 Z'
    head = group([
        path(horn_far, fill=HORN_SHADE),
        path('M42,57 C37,53.5 29,54 23.5,59.5 C18.5,64.5 16.4,72 19.6,77.8 C22.4,82.6 29.4,82.8 34.6,79.4 C41.6,74.8 46,64.8 42,57 Z', fill=HIDE_DARK),
        path('M36,58 C31,58 26,61 23.5,65.5', stroke=HIDE_LIGHT, width=1.4, alpha=.7),
        path(ell(21.6, 76.4, 6.4, 5.2), fill=MUZZLE),
        path(ell(18.6, 75.2, 1.7, 1.2), fill='#1A120E'),
        path('M21,79.6 Q24.5,81 28,79.4', stroke='#2A1F18', width=.9),
        # Brow ridge and a glowing eye.
        path('M26,64.2 Q30.5,61.6 34.6,64', stroke='#1E1712', width=1.8),
        path(ell(30.6, 66.4, 2.0, 1.6), fill=EYE_GLOW),
        path(ell(30.9, 66.4, .8, .9), fill='#3A1A00'),
        path('M40.5,63 C45,62.4 47.2,65.4 45.8,68.4 C44,67.4 42,66.2 40.5,63 Z', fill=HIDE),  # ear
        path(horn_near, fill=HORN), path(horn_near, stroke=HORN_SHADE, width=.8),
        path('M56.5,42 C60.5,42.5 62,46 58.6,46.6 C58,45 57.4,43.4 56.5,42 Z', fill=HORN_TIP),
        path('M38,52.5 C40,49.5 43,47.6 46.5,46.6', stroke='#FFFFFF', width=.9, alpha=.35),
    ], px=42, py=62, rot=-head_rot)
    body = [
        # Far legs first, a shade darker.
        leg(53, 80, 100, HIDE_DARK, bend=-front_bend * .8),
        leg(93.5, 76, 100, HIDE_DARK),
        # Tail with a dark tuft, swishing with the breath.
        path(f'M100,60 C{106 + breath:.1f},66 {104 - breath:.1f},76 {105 + breath * .6:.1f},84', stroke=HIDE, width=2.0),
        path(ell(105 + breath * .6, 86, 2.6, 3.6), fill=HIDE_DARK),
        # The body: a heavy shoulder hump falling to the rump.
        path('M38,64 C37,51 47,43.5 60,45 C72,46 85,51.5 95,55.5 C103,58.5 105,70 101,78 C98,84 89,86.5 80,84.6 '
             'C70,86.6 56,86.8 46,84.6 C39.5,82.6 38.4,73.5 38,64 Z', fill=HIDE),
        path('M44,52 C50,46.6 62,45.6 73,48.6 C82,51 90,54.6 96,57.6', stroke=HIDE_LIGHT, width=3.2, alpha=.55),
        # Weathered, stony patches on the hide.
        path(ell(70, 62, 5.5, 3.4), fill=HIDE_DARK, alpha=.45), path(ell(84, 70, 4.2, 2.6), fill=HIDE_DARK, alpha=.45),
        path(ell(57, 72, 3.6, 2.2), fill=HIDE_DARK, alpha=.4),
        path('M66,58 L69,63 L66.5,66', stroke='#2A1F18', width=.8, alpha=.6),
        leg(45, 78, 100, HIDE, bend=-front_bend),
        leg(85, 74, 100, HIDE),
        head,
    ]
    rig = group([group(body, px=66, py=100, sx=1.0 / squash ** .5, sy=squash)], px=HIND[0], py=HIND[1], rot=-rear)
    return rig, rear, snort


CRACKS = [
    'M46,101.4 L40,103.4 L35,101.8 L28,104.4 L21,102.6 L14,104.8',
    'M46,101.4 L53,103.6 L59,101.8 L66,104.2 L74,102.4',
    'M35,101.8 L33,105.6', 'M59,101.8 L61.5,105.8', 'M46,101.4 L45,106',
]


def ground(t):
    grow = smoothstep(0.565, 0.66, t)
    fade = 1.0 - smoothstep(0.80, 0.985, t)
    glow = smoothstep(0.565, 0.6, t) * (1.0 - smoothstep(0.62, 0.86, t))
    shake = math.sin(t * 2 * math.pi * 24) * 1.1 * smoothstep(0.56, 0.58, t) * (1 - smoothstep(0.6, 0.68, t))
    nodes = [path(rrect(4 + shake, 99.5, 112, 8.5, 4), fill='#5C4E40', alpha=.55),
             path(rrect(4 + shake, 99.5, 112, 1.8, .9), fill='#9C8A70', alpha=.6)]
    if grow > 0 and fade > 0:
        for i, d in enumerate(CRACKS):
            g = grow if i < 2 else smoothstep(0.6, 0.68, t)
            if g <= 0:
                continue
            if glow > 0:
                nodes.append(path(d, stroke='#FF7A1A', width=2.6, alpha=.55 * glow * fade, trim=min(1.0, g)))
            nodes.append(path(d, stroke='#1E140C', width=1.3 if i < 2 else 1.0, alpha=.95 * fade, trim=min(1.0, g)))
            if glow > 0:
                nodes.append(path(d, stroke='#FFD27A', width=.6, alpha=.9 * glow * fade, trim=min(1.0, g)))
    return nodes


def stones(t):
    nodes = []
    start, end = 0.57, 0.80
    if not (start <= t <= end):
        return nodes
    u = (t - start) / (end - start)
    for i, (vx, vy, size) in enumerate([(-16, 20, 2.2), (-9, 27, 1.6), (11, 24, 2.0), (18, 16, 1.4), (-22, 13, 1.3)]):
        x = FRONT_X + vx * u
        y = 100 - (vy * u - 34 * u * u)
        if y > 100.5:
            continue
        a = 1 - smoothstep(.75, 1, u)
        r = size
        poly = f'M{x - r:.2f},{y:.2f} L{x - r * .3:.2f},{y - r:.2f} L{x + r:.2f},{y - r * .4:.2f} L{x + r * .6:.2f},{y + r * .8:.2f} Z'
        nodes.append(path(poly, fill=STONE if i % 2 else STONE_LIGHT, alpha=a))
    return nodes


def dust(t):
    nodes = []
    start, end = 0.57, 0.95
    if not (start <= t <= end):
        return nodes
    u = (t - start) / (end - start)
    e = 1 - (1 - u) ** 3
    for i in range(8):
        side = -1 if i % 2 == 0 else 1
        k = i // 2
        x = FRONT_X + side * (6 + k * 4 + (10 + 7 * k) * e)
        y = 99 - (2.5 + 2.6 * k) * e - 1.6 * math.sin(math.pi * u)
        r = 2.6 + (4 + k * 1.2) * e
        a = .72 * (1 - smoothstep(0.35, 1.0, u)) * smoothstep(0.0, 0.07, u)
        nodes.append(path(ell(x, y, r * 1.3, r), fill='#A8977F', alpha=a))
        nodes.append(path(ell(x - .7 * side, y - .8, r * .7, r * .55), fill='#D6C8B2', alpha=a * .75))
    return nodes


def breath_puffs(t, rear):
    nodes = []
    start, end = 0.585, 0.80
    if not (start <= t <= end):
        return nodes
    u = (t - start) / (end - start)
    for i in range(3):
        x = 15 - 9 * u - i * 3
        y = 76 - 3 * u + i * 1.2
        r = 1.6 + 3 * u + i * .6
        nodes.append(path(ell(x, y, r * 1.2, r), fill='#E8E4DE', alpha=.55 * (1 - u) * smoothstep(0, .15, u)))
    return nodes


def shadow(rear):
    lift = min(1.0, abs(rear) / 16.0)
    return [path(ell(68 + 6 * lift, 101, 34 * (1 - .18 * lift), 3.8), fill='#000000', alpha=.24 * (1 - .3 * lift))]


def frame(t):
    rig, rear, snort = ox(t)
    return ground(t) + shadow(rear) + dust(t) + [rig] + stones(t) + (breath_puffs(t, rear) if snort else [])


def vector(nodes):
    body = ''.join(to_vector(n) for n in nodes)
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="120dp" android:height="120dp" '
            f'android:viewportWidth="{VIEW:.0f}" android:viewportHeight="{VIEW:.0f}">\n{body}</vector>\n')


def svg(nodes, x=0, y=0):
    return f'<g transform="translate({x},{y})">' + ''.join(to_svg(n) for n in nodes) + '</g>'


def main():
    for i in range(FRAMES):
        (OUT / f'widget_bull_f{i:02d}.xml').write_text(vector(frame(i / FRAMES)))
    (OUT / 'widget_bull_still.xml').write_text(vector(frame(0.0)))
    if '--preview' in sys.argv:
        dest = Path(sys.argv[sys.argv.index('--preview') + 1])
        cols = 6
        parts = [svg(frame(i / FRAMES), (i % cols) * 130 + 5, (i // cols) * 130 + 5) for i in range(FRAMES)]
        sheet = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{cols * 130}" height="{((FRAMES + cols - 1) // cols) * 130}">'
                 f'<rect width="100%" height="100%" fill="#8FB8E6"/>' + ''.join(parts) + '</svg>')
        dest.write_text(sheet)
        subprocess.run(['rsvg-convert', '-z', '1.5', str(dest), '-o', str(dest.with_suffix('.png'))], check=True)


if __name__ == '__main__':
    main()
