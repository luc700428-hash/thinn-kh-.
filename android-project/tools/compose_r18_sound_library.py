#!/usr/bin/env python3
"""thinn-khì r18 sound library: three styles the user can pick from everywhere.

  bright  清亮  – marimba, glockenspiel, crystal plucks (iOS-like, clean and short)
  warm    溫暖  – felt piano, kalimba, nylon guitar (Karplus–Strong), soft pads
  modern  電子  – FM bells, detuned saw pads, soft side-chained pulse

Outputs (mono, 44.1 kHz, Ogg Vorbis) into app/src/main/res/raw:
  thinn_ring_<style>_<n>.ogg      4 alarm loops per style (seamless, ~24 s)
  thinn_tone_<style>_<n>.ogg      4 notification tones per style (~1–1.6 s)
  thinn_quake_<style>_<kind>.ogg  earthquake set per style: first / urgent / update / report

Everything is original synthesis (numpy + scipy + ffmpeg). Not needed for normal builds.
"""
from pathlib import Path
import subprocess, tempfile, wave, hashlib, json
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'app/src/main/res/raw'
SR = 44100
rng = np.random.default_rng(1818)

def hz(m): return 440.0 * 2 ** ((m - 69) / 12)
def env(n, a=.004, d=None, tail=.04):
    t = np.arange(n) / SR
    e = 1 - np.exp(-t / max(a, 1e-4))
    if d: e *= np.exp(-t / d)
    k = int(tail * SR)
    if k and n > k: e[-k:] *= np.linspace(1, 0, k) ** 2
    return e

# ---------- instruments ----------
def marimba(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t)*np.exp(-t*3.2) + .35*np.sin(2*np.pi*f*3.93*t)*np.exp(-t*11) + .12*np.sin(2*np.pi*f*9.2*t)*np.exp(-t*30)
    return v * y * env(n, .002)
def glock(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t)*np.exp(-t*1.6) + .4*np.sin(2*np.pi*f*2.76*t)*np.exp(-t*4) + .2*np.sin(2*np.pi*f*5.4*t)*np.exp(-t*9)
    return v * y * env(n, .001)
def crystal(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = sum(a*np.sin(2*np.pi*f*r*t + p)*np.exp(-t*dk) for a, r, dk, p in [(1,1,2.4,0),(.45,2.0,4,.3),(.22,3.01,7,.9),(.1,4.1,12,.2)])
    return v * y * env(n, .0015)
def piano(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.zeros(n)
    for h, a in enumerate([1, .5, .28, .16, .09, .05, .03], 1):
        fh = f * h * np.sqrt(1 + .0004 * h * h)
        y += a * np.sin(2*np.pi*fh*t) * np.exp(-t * (1.1 + .55*h))
    y = sosfilt(butter(1, 5200, fs=SR, output='sos'), y)
    return v * y * env(n, .003)
def kalimba(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t)*np.exp(-t*2.6) + .25*np.sin(2*np.pi*f*5.95*t)*np.exp(-t*14) + .08*np.sin(2*np.pi*f*12.1*t)*np.exp(-t*40)
    return v * y * env(n, .0015)
def guitar(m, dur, v=1.):
    n = int(dur * SR); f = hz(m); p = max(2, int(SR / f))
    buf = rng.uniform(-1, 1, p); buf = sosfilt(butter(1, 3000, fs=SR, output='sos'), buf)
    y = np.empty(n)
    for i in range(n):
        y[i] = buf[i % p]
        buf[i % p] = .996 * .5 * (buf[i % p] + buf[(i + 1) % p])
    return v * .9 * y * env(n, .001)
def fm_bell(m, dur, v=1., ratio=3.5, index=2.2):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    i = index * np.exp(-t * 3)
    y = np.sin(2*np.pi*f*t + i*np.sin(2*np.pi*f*ratio*t)) * np.exp(-t * 1.9)
    return v * y * env(n, .002)
def saw_pad(m, dur, v=1., cutoff=1800):
    n = int(dur * SR); t = np.arange(n) / SR; y = np.zeros(n)
    for det in (-.11, -.04, 0, .05, .12):
        f = hz(m + det); ph = (f * t) % 1
        y += 2 * ph - 1
    y = sosfilt(butter(2, cutoff, fs=SR, output='sos'), y / 5)
    a = np.clip(t / .35, 0, 1); r = np.clip((dur - t) / .45, 0, 1)
    return v * y * a * r
def soft_pad(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t) + .3*np.sin(2*np.pi*2*f*t + .4) + .12*np.sin(2*np.pi*3*f*t)
    y *= 1 + .08 * np.sin(2*np.pi*5.1*t)
    a = np.clip(t / .5, 0, 1); r = np.clip((dur - t) / .6, 0, 1)
    return v * y * a * r
def kick(v=1.):
    n = int(.32 * SR); t = np.arange(n) / SR
    f = 46 + 90 * np.exp(-t * 28)
    return v * np.sin(2*np.pi*np.cumsum(f)/SR) * np.exp(-t * 9)
def hat(v=1.):
    n = int(.06 * SR)
    y = sosfilt(butter(2, 7000, btype='high', fs=SR, output='sos'), rng.uniform(-1, 1, n))
    return v * y * np.exp(-np.arange(n) / SR * 70)
def shaker(v=1.):
    n = int(.09 * SR)
    y = sosfilt(butter(2, [4000, 11000], btype='band', fs=SR, output='sos'), rng.uniform(-1, 1, n))
    t = np.arange(n) / SR
    return v * y * np.sin(np.pi * t / t[-1]) ** 2

INSTR = {'marimba': marimba, 'glock': glock, 'crystal': crystal, 'piano': piano, 'kalimba': kalimba,
         'guitar': guitar, 'fm': fm_bell, 'saw': saw_pad, 'pad': soft_pad}

# ---------- mixing ----------
def place(buf, y, at, loop=False):
    s = int(at * SR)
    if loop:
        idx = (np.arange(len(y)) + s) % len(buf); np.add.at(buf, idx, y)
    else:
        e = min(len(buf), s + len(y)); buf[s:e] += y[:e - s]
_ir_cache = {}
def reverb(x, size=1.6, wet=.22, loop=False):
    key = size
    if key not in _ir_cache:
        n = int(size * SR); t = np.arange(n) / SR
        ir = rng.normal(0, 1, n) * np.exp(-t * 6.9 / size)
        ir = sosfilt(butter(1, 6000, fs=SR, output='sos'), ir); ir[0] = 0
        _ir_cache[key] = ir / np.sqrt(np.sum(ir ** 2))
    ir = _ir_cache[key]
    if loop:
        tiled = np.concatenate([x, x, x]); wetsig = fftconvolve(tiled, ir)[len(x):2 * len(x)]
    else:
        wetsig = fftconvolve(x, ir)[:len(x)]
    return x * (1 - wet) + wetsig * wet * 1.4
def master(x, peak=.89, loop=False, lufs_rms=-17.0):
    x = sosfilt(butter(2, 70, btype='high', fs=SR, output='sos'), np.concatenate([x, x]) if loop else x)
    if loop: x = x[len(x) // 2:]
    x = x - x.mean()
    x *= 10 ** (lufs_rms / 20) / (np.sqrt(np.mean(x * x)) + 1e-12)
    x = np.tanh(x * 1.15) / 1.15
    return x * (peak / (np.max(np.abs(x)) + 1e-12))

def write_ogg(name, x, quality=4):
    RAW.mkdir(parents=True, exist_ok=True)
    pcm = np.round(np.clip(x, -.999, .999) * 32767).astype('<i2')
    with tempfile.TemporaryDirectory() as d:
        w = Path(d) / 'a.wav'
        with wave.open(str(w), 'wb') as f:
            f.setparams((1, 2, SR, 0, 'NONE', 'not compressed')); f.writeframes(pcm.tobytes())
        out = RAW / f'{name}.ogg'
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(w), '-c:a', 'libvorbis', '-q:a', str(quality), str(out)], check=True)
    return RAW / f'{name}.ogg'

# ---------- music ----------
# Chord progressions as (root midi, quality) per bar; melodies as (beat, midi, beats).
MAJ = [0, 4, 7]; MIN = [0, 3, 7]; SUS = [0, 5, 7]; ADD9 = [0, 4, 7, 14]; M7 = [0, 4, 7, 11]; MI7 = [0, 3, 7, 10]

RINGS = {
  'bright': [
    dict(title='晨露', bpm=112, lead='marimba', pad='pad', chords=[(60, ADD9), (67, MAJ), (69, MIN), (65, M7)] * 2,
         mel=[0, 4, 7, 12, 11, 7, 4, 7]),
    dict(title='晴光', bpm=118, lead='glock', pad='pad', chords=[(65, M7), (67, MAJ), (64, MI7), (69, MIN)] * 2,
         mel=[7, 12, 14, 12, 9, 7, 4, 2]),
    dict(title='水晶', bpm=104, lead='crystal', pad='pad', chords=[(62, MI7), (67, MAJ), (60, M7), (65, MAJ)] * 2,
         mel=[2, 5, 9, 12, 9, 5, 7, 4]),
    dict(title='雷達', bpm=124, lead='marimba', pad=None, chords=[(60, MAJ), (65, MAJ), (67, SUS), (67, MAJ)] * 2,
         mel=[12, 7, 12, 16, 14, 12, 7, 9]),
  ],
  'warm': [
    dict(title='日出鋼琴', bpm=86, lead='piano', pad='pad', chords=[(60, ADD9), (64, MI7), (65, M7), (67, SUS)] * 2,
         mel=[4, 7, 11, 12, 11, 7, 9, 7]),
    dict(title='木屋拇指琴', bpm=100, lead='kalimba', pad='pad', chords=[(62, MI7), (67, MAJ), (60, ADD9), (69, MI7)] * 2,
         mel=[2, 9, 7, 5, 4, 7, 9, 12]),
    dict(title='午後吉他', bpm=92, lead='guitar', pad='pad', chords=[(64, MIN), (60, MAJ), (67, MAJ), (62, MAJ)] * 2,
         mel=[7, 4, 7, 11, 12, 11, 7, 4]),
    dict(title='森林晨光', bpm=96, lead='kalimba', pad='pad', chords=[(65, M7), (60, ADD9), (62, MI7), (67, SUS)] * 2,
         mel=[0, 4, 9, 7, 5, 4, 2, 4]),
  ],
  'modern': [
    dict(title='極光', bpm=120, lead='fm', pad='saw', drums=True, chords=[(57, MI7), (65, M7), (60, ADD9), (67, MAJ)] * 2,
         mel=[12, 15, 19, 17, 15, 12, 10, 12]),
    dict(title='霓虹城市', bpm=116, lead='fm', pad='saw', drums=True, chords=[(62, MI7), (67, MAJ), (64, MI7), (69, MI7)] * 2,
         mel=[7, 10, 14, 12, 10, 7, 5, 7]),
    dict(title='脈衝', bpm=126, lead='fm', pad='saw', drums=True, chords=[(60, SUS), (60, MAJ), (65, MAJ), (67, MAJ)] * 2,
         mel=[12, 12, 19, 16, 14, 12, 7, 12]),
    dict(title='星際', bpm=108, lead='fm', pad='saw', drums=True, chords=[(64, MI7), (60, M7), (67, MAJ), (62, ADD9)] * 2,
         mel=[4, 11, 7, 14, 12, 7, 11, 4]),
  ],
}

def compose_ring(spec):
    beat = 60 / spec['bpm']; bars = len(spec['chords']); dur = bars * 4 * beat
    buf = np.zeros(int(dur * SR)); lead = INSTR[spec['lead']]
    for b, (root, q) in enumerate(spec['chords']):
        t0 = b * 4 * beat
        # pad / sustained harmony
        if spec.get('pad'):
            pad = INSTR[spec['pad']]
            for iv in q[:3]:
                place(buf, pad(root - 12 + iv, 4 * beat + .3, .10 if spec['pad'] == 'pad' else .16), t0, loop=True)
        # bass
        bass = saw_pad(root - 24, 2 * beat, .22, cutoff=600) if spec.get('drums') else soft_pad(root - 24, 4 * beat, .16)
        place(buf, bass, t0, loop=True)
        if spec.get('drums'): place(buf, bass, t0 + 2 * beat, loop=True)
        # arpeggio in eighths
        for k in range(8):
            iv = q[k % len(q)] + (12 if k >= 4 else 0)
            place(buf, lead(root + iv, 1.2, .16 + (.04 if k % 2 == 0 else 0)), t0 + k * beat / 2, loop=True)
        # melody: one phrase note per beat on bars, answered on the second half
        m = spec['mel']
        for k in range(4):
            note = root + m[(b * 2 + k) % len(m)] + 12
            if (b + k) % 3 != 2:
                place(buf, lead(note, 1.6, .34), t0 + k * beat, loop=True)
        if spec.get('drums'):
            for k in range(4):
                place(buf, kick(.55), t0 + k * beat, loop=True)
                place(buf, hat(.18), t0 + k * beat + beat / 2, loop=True)
        elif spec['lead'] in ('marimba', 'kalimba', 'guitar'):
            for k in range(8):
                place(buf, shaker(.05 + (.03 if k % 2 else 0)), t0 + k * beat / 2, loop=True)
    if spec.get('drums'):
        # gentle side-chain pulse so the pad breathes with the kick
        t = (np.arange(len(buf)) / SR) % beat
        buf *= 1 - .35 * np.exp(-t / .09)
    buf = reverb(buf, 1.8 if spec.get('drums') else 2.2, .26, loop=True)
    return master(buf, .86, loop=True, lufs_rms=-16.5)

TONES = {
  'bright': [('清鈴', 'glock', [(0, 84), (.14, 91)]), ('水滴', 'marimba', [(0, 88), (.1, 93), (.2, 96)]),
             ('晨光', 'crystal', [(0, 79), (.12, 84), (.24, 88)]), ('漣漪', 'glock', [(0, 91), (.16, 86)])],
  'warm':   [('木鈴', 'kalimba', [(0, 76), (.14, 83)]), ('琴音', 'piano', [(0, 72), (.12, 76), (.24, 79)]),
             ('撥弦', 'guitar', [(0, 67), (.09, 71), (.18, 74), (.27, 79)]), ('微風', 'kalimba', [(0, 81), (.18, 76)])],
  'modern': [('光點', 'fm', [(0, 84), (.11, 88)]), ('脈衝', 'fm', [(0, 79), (.08, 79), (.16, 86)]),
             ('霓虹', 'fm', [(0, 88), (.12, 83), (.24, 91)]), ('星環', 'fm', [(0, 76), (.15, 83), (.3, 88)])],
}

def compose_tone(inst, notes):
    dur = notes[-1][0] + 1.3; buf = np.zeros(int(dur * SR))
    for at, m in notes:
        place(buf, INSTR[inst](m, 1.25, .8), at)
        if inst in ('glock', 'crystal', 'fm'): place(buf, INSTR[inst](m + 12, .8, .18), at + .004)
    buf = reverb(buf, 1.1, .2)
    buf *= env(len(buf), .001, tail=.25)
    return master(buf, .9, lufs_rms=-15)

# Earthquake set: deliberately insistent but musical; urgent is the most attention-grabbing.
QUAKE = {
  'bright': dict(inst='glock', first=[84, 88, 91, 96], urgent=[(96, 91)] * 4, update=[91, 96], report=[79, 84, 88]),
  'warm':   dict(inst='kalimba', first=[76, 81, 84, 88], urgent=[(88, 81)] * 4, update=[84, 88], report=[72, 76, 79]),
  'modern': dict(inst='fm', first=[81, 86, 88, 93], urgent=[(93, 86)] * 4, update=[88, 93], report=[76, 81, 84]),
}

def compose_quake(style, kind):
    s = QUAKE[style]; inst = INSTR[s['inst']]
    if kind == 'urgent':
        dur = 3.2; buf = np.zeros(int(dur * SR)); t = 0
        for hi, lo in s['urgent']:
            for k, m in enumerate((hi, lo)):
                place(buf, inst(m, .5, 1.), t); place(buf, fm_bell(m, .4, .5, ratio=1.0, index=1.2), t); t += .19
            t += .03
        # a low pulse underneath gives weight on small phone speakers
        n = len(buf); tt = np.arange(n) / SR
        buf += .25 * np.sin(2*np.pi*hz(52)*tt) * (np.sin(2*np.pi*3.3*tt) > 0) * np.clip((dur - tt) / .4, 0, 1)
        buf = reverb(buf, .9, .14); return master(buf, .86, lufs_rms=-11.5)
    notes = s[kind]; gap = {'first': .13, 'update': .12, 'report': .16}[kind]
    reps = 2 if kind == 'first' else 1
    dur = reps * (len(notes) * gap + .35) + 1.0; buf = np.zeros(int(dur * SR)); t = 0
    for _ in range(reps):
        for m in notes:
            place(buf, inst(m, .9, .9), t); place(buf, inst(m + 12, .6, .25), t); t += gap
        t += .35
    buf = reverb(buf, 1.0, .18); buf *= env(len(buf), .001, tail=.3)
    return master(buf, .93, lufs_rms=-12 if kind != 'report' else -15)

def main():
    manifest = {'rings': {}, 'tones': {}, 'quake': {}}
    for style, specs in RINGS.items():
        for i, spec in enumerate(specs, 1):
            name = f'thinn_ring_{style}_{i}'; write_ogg(name, compose_ring(spec), 3)
            manifest['rings'][name] = spec['title']
    for style, specs in TONES.items():
        for i, (title, inst, notes) in enumerate(specs, 1):
            name = f'thinn_tone_{style}_{i}'; write_ogg(name, compose_tone(inst, notes), 5)
            manifest['tones'][name] = title
    for style in QUAKE:
        for kind in ('first', 'urgent', 'update', 'report'):
            name = f'thinn_quake_{style}_{kind}'; write_ogg(name, compose_quake(style, kind), 5)
            manifest['quake'][name] = kind
    for kind in manifest.values():
        for name in kind:
            p = RAW / f'{name}.ogg'
            kind[name] = {'title': kind[name], 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
    (ROOT / 'tools/audio_sources/R18_SOUND_LIBRARY.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=1))
    print(sum(len(v) for v in manifest.values()), 'files')

if __name__ == '__main__':
    main()
