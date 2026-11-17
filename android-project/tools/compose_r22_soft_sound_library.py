#!/usr/bin/env python3
"""thinn-khì r22 soft sound library — replaces every app tone with one gentle palette.

Users found the r18 set shrill: it sat at C6–C7 with bright inharmonic partials, hard attacks and
saturated, loud mastering. r22 keeps every file name (so existing notification channels and saved
choices keep working) and re-voices everything:

  * fundamentals mostly G3–E5, nothing sustained above G5
  * 6–25 ms attacks, every voice low-passed (2.4–4 kHz), no inharmonic "ping" partials
  * clean mastering without saturation; everyday tones about 5 dB quieter than r18, alarms near r18 level
  * earthquake cues stay insistent through rhythm and repetition, not through pitch

Styles: bright 輕柔 (felt celesta, soft marimba, vibraphone) · warm 溫暖 (felt piano, kalimba,
harp) · modern 空靈 (soft Rhodes, glass pads, airy pulse) · classic 經典 (the eight legacy tones).

Outputs (mono, 44.1 kHz, Ogg Vorbis) into app/src/main/res/raw — see main().
Everything is original synthesis (numpy + scipy + ffmpeg). Not needed for normal builds.
"""
from pathlib import Path
import subprocess, tempfile, wave, hashlib, json
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'app/src/main/res/raw'
SR = 44100
rng = np.random.default_rng(2222)


def hz(m): return 440.0 * 2 ** ((m - 69) / 12)


def lp(x, cut, order=2): return sosfilt(butter(order, cut, fs=SR, output='sos'), x)


def hp(x, cut, order=2): return sosfilt(butter(order, cut, btype='high', fs=SR, output='sos'), x)


def env(n, a=.012, d=None, tail=.05):
    t = np.arange(n) / SR
    e = 1 - np.exp(-t / max(a, 1e-4))
    if d: e = e * np.exp(-t / d)
    k = int(tail * SR)
    if k and n > k: e[-k:] *= np.linspace(1, 0, k) ** 2
    return e


# ---------- soft instruments: few partials, quick decay of the upper ones, always low-passed ----------
def celesta(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t)*np.exp(-t*2.2) + .14*np.sin(2*np.pi*2*f*t)*np.exp(-t*6) + .05*np.sin(2*np.pi*4*f*t)*np.exp(-t*14)
    return v * lp(y, 3200) * env(n, .006)


def marimba(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t)*np.exp(-t*3.0) + .12*np.sin(2*np.pi*f*3.93*t)*np.exp(-t*16)
    return v * lp(y, 2600) * env(n, .008)


def vibes(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    trem = 1 + .18 * np.sin(2*np.pi*4.6*t)
    y = (np.sin(2*np.pi*f*t) + .1*np.sin(2*np.pi*4*f*t)*np.exp(-t*8)) * np.exp(-t*1.2) * trem
    return v * lp(y, 3400) * env(n, .01)


def piano(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m); y = np.zeros(n)
    for h, a in enumerate([1, .42, .18, .08, .035], 1):
        fh = f * h * np.sqrt(1 + .0003 * h * h)
        y += a * np.sin(2*np.pi*fh*t) * np.exp(-t * (1.0 + .9*h))
    return v * lp(y, 2400) * env(n, .009)


def kalimba(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t)*np.exp(-t*2.3) + .08*np.sin(2*np.pi*f*5.95*t)*np.exp(-t*22)
    return v * lp(y, 2800) * env(n, .007)


def harp(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m); y = np.zeros(n)
    for h, a in enumerate([1, .5, .25, .12, .05], 1):
        y += a * np.sin(2*np.pi*f*h*t) * np.exp(-t * (1.4 + 1.2*h))
    return v * lp(y, 2600) * env(n, .006)


def rhodes(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    idx = .7 * np.exp(-t * 4)
    y = np.sin(2*np.pi*f*t + idx*np.sin(2*np.pi*f*t)) * np.exp(-t*1.5)
    y *= 1 + .1 * np.sin(2*np.pi*5*t)
    return v * lp(y, 2500) * env(n, .01)


def glass(m, dur, v=1.):
    """A soft FM glass tone (integer ratio, low index): bell-like without the clang."""
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    idx = 1.0 * np.exp(-t * 5)
    y = np.sin(2*np.pi*f*t + idx*np.sin(2*np.pi*2*f*t)) * np.exp(-t*1.7)
    return v * lp(y, 3000) * env(n, .012)


def flute(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    vib = np.sin(2*np.pi*f*t + .9*np.sin(2*np.pi*5.2*t)*np.clip(t/.4, 0, 1)*.02*f/5.2)
    breath = lp(rng.normal(0, 1, n), 1800) * .04
    a = np.clip(t / .09, 0, 1); r = np.clip((dur - t) / .2, 0, 1)
    return v * lp(vib + .12*np.sin(4*np.pi*f*t) + breath, 2600) * a * r


def pad(m, dur, v=1., cut=1400):
    n = int(dur * SR); t = np.arange(n) / SR; y = np.zeros(n)
    for det in (-.07, 0, .06):
        f = hz(m + det)
        y += np.sin(2*np.pi*f*t) + .25*np.sin(2*np.pi*2*f*t + .4)
    a = np.clip(t / .6, 0, 1); r = np.clip((dur - t) / .7, 0, 1)
    return v * lp(y / 3, cut) * a * r


def strings(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; y = np.zeros(n)
    for det in (-.09, -.03, .04, .1):
        f = hz(m + det); y += 2 * ((f * t) % 1) - 1
    a = np.clip(t / .5, 0, 1); r = np.clip((dur - t) / .6, 0, 1)
    return v * lp(y / 4, 1100, 3) * a * r


def bass(m, dur, v=1.):
    n = int(dur * SR); t = np.arange(n) / SR; f = hz(m)
    y = np.sin(2*np.pi*f*t) + .2*np.sin(4*np.pi*f*t)
    return v * lp(y, 500) * env(n, .015, d=dur * .9, tail=.08)


def soft_kick(v=1.):
    n = int(.28 * SR); t = np.arange(n) / SR
    f = 52 + 50 * np.exp(-t * 30)
    return v * np.sin(2*np.pi*np.cumsum(f)/SR) * np.exp(-t * 11) * env(n, .003)


def brush(v=1.):
    n = int(.12 * SR); t = np.arange(n) / SR
    y = sosfilt(butter(2, [1800, 6000], btype='band', fs=SR, output='sos'), rng.normal(0, 1, n))
    return v * y * np.sin(np.pi * t / t[-1]) ** 2


def rain_bed(n, v=.05):
    y = lp(hp(rng.normal(0, 1, n), 400), 3500)
    return v * y * (1 + .25 * np.sin(2*np.pi*np.arange(n)/SR*.13))


INSTR = {'celesta': celesta, 'marimba': marimba, 'vibes': vibes, 'piano': piano, 'kalimba': kalimba,
         'harp': harp, 'rhodes': rhodes, 'glass': glass, 'flute': flute}


# ---------- mixing ----------
def place(buf, y, at, loop=False):
    s = int(at * SR)
    if loop:
        idx = (np.arange(len(y)) + s) % len(buf); np.add.at(buf, idx, y)
    else:
        e = min(len(buf), s + len(y)); buf[s:e] += y[:e - s]


_ir = {}


def reverb(x, size=2.0, wet=.24, loop=False):
    if size not in _ir:
        n = int(size * SR); t = np.arange(n) / SR
        ir = rng.normal(0, 1, n) * np.exp(-t * 6.9 / size)
        ir = lp(ir, 4200, 1); ir[0] = 0
        _ir[size] = ir / np.sqrt(np.sum(ir ** 2))
    ir = _ir[size]
    if loop:
        tiled = np.concatenate([x, x, x]); w = fftconvolve(tiled, ir)[len(x):2 * len(x)]
    else:
        w = fftconvolve(x, ir)[:len(x)]
    return x * (1 - wet) + w * wet * 1.3


def master(x, rms_db=-19.0, peak=.8, loop=False, top=6500):
    if loop:
        y = lp(hp(np.concatenate([x, x]), 80), top); y = y[len(y) // 2:]
    else:
        y = lp(hp(x, 80), top)
    y = y - y.mean()
    y *= 10 ** (rms_db / 20) / (np.sqrt(np.mean(y * y)) + 1e-12)
    m = np.max(np.abs(y)) + 1e-12
    if m > peak:  # clean gain, never saturation
        y *= peak / m
    return y


def write_ogg(name, x, quality=4):
    RAW.mkdir(parents=True, exist_ok=True)
    pcm = np.round(np.clip(x, -.999, .999) * 32767).astype('<i2')
    with tempfile.TemporaryDirectory() as d:
        w = Path(d) / 'a.wav'
        with wave.open(str(w), 'wb') as f:
            f.setparams((1, 2, SR, 0, 'NONE', 'not compressed')); f.writeframes(pcm.tobytes())
        out = RAW / f'{name}.ogg'
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(w), '-c:a', 'libvorbis', '-q:a', str(quality), str(out)], check=True)
    return out


# ---------- music ----------
MAJ = [0, 4, 7]; MIN = [0, 3, 7]; SUS = [0, 5, 7]; ADD9 = [0, 4, 7, 14]; M7 = [0, 4, 7, 11]; MI7 = [0, 3, 7, 10]; M6 = [0, 4, 7, 9]


def compose_loop(spec, mood=''):
    """One seamless loop: pad, bass, an arpeggio and a sparse melody; mood colours it."""
    bpm = spec['bpm'] * (.9 if mood == '_snow' else 1.0)
    beat = 60 / bpm; chords = spec['chords']; dur = len(chords) * 4 * beat
    buf = np.zeros(int(dur * SR)); lead = INSTR[spec['lead']]
    shift = {'_clear': 2, '_rain': -3, '_snow': 0}.get(mood, 0)
    for b, (root, q) in enumerate(chords):
        root += shift if mood != '_rain' else 0
        quality = MIN if (mood == '_rain' and q in (MAJ, ADD9)) else q
        t0 = b * 4 * beat
        voice = strings if spec.get('strings') else pad
        for iv in quality[:3]:
            place(buf, voice(root - 12 + iv, 4 * beat + .4, .09), t0, loop=True)
        place(buf, bass(root - 24, 2 * beat, .22), t0, loop=True)
        place(buf, bass(root - 24 + (7 if b % 2 else 0), 2 * beat, .16), t0 + 2 * beat, loop=True)
        steps = spec.get('arp', 8)
        for k in range(steps):
            iv = quality[k % len(quality)] + (12 if k >= steps // 2 else 0)
            accent = .14 if k % 2 == 0 else .1
            place(buf, lead(root + iv, 1.4, accent), t0 + k * 4 * beat / steps, loop=True)
        mel = spec['mel']
        for k in range(4):
            if (b + k) % 3 == 2: continue
            note = root + mel[(b * 2 + k) % len(mel)]
            place(buf, lead(min(note + 12, 79), 1.8, .26), t0 + k * beat, loop=True)
        if spec.get('groove'):
            for k in range(4):
                place(buf, soft_kick(.32), t0 + k * beat, loop=True)
                place(buf, brush(.05), t0 + k * beat + beat / 2, loop=True)
        elif spec.get('brush', True):
            for k in range(8):
                place(buf, brush(.025 + (.012 if k % 2 else 0)), t0 + k * beat / 2, loop=True)
        if mood == '_snow':
            for k in range(0, 8, 3):
                place(buf, celesta(root + 19 + quality[k % len(quality)] % 12, 1.6, .07), t0 + k * beat / 2 + beat / 4, loop=True)
    if mood == '_rain':
        buf += rain_bed(len(buf), .035)
    buf = reverb(buf, 2.4, .28, loop=True)
    return master(buf, -15.5, .86, loop=True, top=5200)


# ringtones r18 ids (titles stay meaningful; voicing is new)
RINGS = {
    'bright': [
        dict(title='晨露', bpm=96, lead='celesta', chords=[(60, ADD9), (67, MAJ), (69, MIN), (65, M7)] * 2, mel=[0, 4, 7, 12, 11, 7, 4, 7]),
        dict(title='晴光', bpm=100, lead='vibes', chords=[(65, M7), (67, MAJ), (64, MI7), (69, MIN)] * 2, mel=[7, 12, 9, 7, 4, 2, 4, 7]),
        dict(title='水晶', bpm=92, lead='marimba', chords=[(62, MI7), (67, MAJ), (60, M7), (65, MAJ)] * 2, mel=[2, 5, 9, 7, 5, 4, 2, 0]),
        dict(title='雷達', bpm=104, lead='marimba', chords=[(60, MAJ), (65, M6), (67, SUS), (67, MAJ)] * 2, mel=[7, 4, 7, 9, 7, 4, 2, 4]),
    ],
    'warm': [
        dict(title='日出鋼琴', bpm=80, lead='piano', chords=[(60, ADD9), (64, MI7), (65, M7), (67, SUS)] * 2, mel=[4, 7, 11, 12, 11, 7, 9, 7]),
        dict(title='木屋拇指琴', bpm=90, lead='kalimba', chords=[(62, MI7), (67, MAJ), (60, ADD9), (69, MI7)] * 2, mel=[2, 9, 7, 5, 4, 7, 9, 7]),
        dict(title='午後吉他', bpm=84, lead='harp', chords=[(64, MIN), (60, MAJ), (67, MAJ), (62, MAJ)] * 2, mel=[7, 4, 7, 11, 12, 11, 7, 4]),
        dict(title='森林晨光', bpm=88, lead='kalimba', chords=[(65, M7), (60, ADD9), (62, MI7), (67, SUS)] * 2, mel=[0, 4, 9, 7, 5, 4, 2, 4]),
    ],
    'modern': [
        dict(title='極光', bpm=96, lead='rhodes', groove=True, chords=[(57, MI7), (65, M7), (60, ADD9), (67, MAJ)] * 2, mel=[12, 10, 7, 5, 7, 10, 12, 7]),
        dict(title='霓虹城市', bpm=100, lead='rhodes', groove=True, chords=[(62, MI7), (67, MAJ), (64, MI7), (69, MI7)] * 2, mel=[7, 10, 12, 10, 7, 5, 3, 5]),
        dict(title='脈衝', bpm=104, lead='glass', groove=True, chords=[(60, SUS), (60, MAJ), (65, MAJ), (67, MAJ)] * 2, mel=[7, 7, 12, 11, 9, 7, 4, 7]),
        dict(title='星際', bpm=90, lead='glass', strings=True, brush=False, chords=[(64, MI7), (60, M7), (67, MAJ), (62, ADD9)] * 2, mel=[4, 11, 7, 12, 11, 7, 4, 2]),
    ],
}

# r10 alarm themes (each with mood variants): same names, gentle arrangements
ALARMS = {
    'dawn':      dict(bpm=78, lead='piano', chords=[(60, ADD9), (67, SUS), (69, MI7), (65, M7)] * 2, mel=[4, 7, 9, 7, 4, 2, 4, 0], moods=['', '_clear', '_rain']),
    'daybreak':  dict(bpm=86, lead='celesta', chords=[(62, ADD9), (69, MI7), (67, MAJ), (64, MI7)] * 2, mel=[2, 7, 9, 12, 9, 7, 4, 2], moods=['', '_clear', '_rain', '_snow']),
    'rain':      dict(bpm=76, lead='piano', chords=[(57, MI7), (65, M7), (60, ADD9), (64, MI7)] * 2, mel=[3, 7, 10, 7, 5, 3, 2, 0], moods=['', '_clear', '_rain']),
    'wood':      dict(bpm=92, lead='marimba', chords=[(60, MAJ), (65, ADD9), (62, MI7), (67, SUS)] * 2, mel=[0, 4, 7, 9, 7, 4, 2, 4], moods=['', '_clear', '_rain']),
    'water':     dict(bpm=84, lead='vibes', chords=[(64, MI7), (69, MI7), (62, M7), (67, ADD9)] * 2, mel=[7, 10, 12, 10, 7, 5, 3, 5], moods=['', '_clear', '_rain']),
    'fire':      dict(bpm=94, lead='harp', chords=[(62, MAJ), (69, MAJ), (71, MIN), (67, MAJ)] * 2, mel=[4, 7, 9, 11, 9, 7, 4, 2], moods=['', '_clear', '_rain']),
    'metal':     dict(bpm=88, lead='glass', chords=[(60, M7), (65, M7), (67, SUS), (64, MI7)] * 2, mel=[7, 11, 12, 11, 7, 4, 7, 9], moods=['', '_clear', '_rain']),
    'citypop':   dict(bpm=100, lead='rhodes', groove=True, chords=[(65, M7), (64, MI7), (62, MI7), (67, SUS)] * 2, mel=[9, 7, 4, 7, 9, 12, 11, 9], moods=['', '_clear', '_rain', '_snow']),
    'flute':     dict(bpm=80, lead='flute', arp=4, chords=[(62, ADD9), (67, MAJ), (64, MI7), (69, SUS)] * 2, mel=[2, 4, 7, 9, 7, 4, 2, 0], moods=['', '_clear', '_rain', '_snow']),
    'futurepop': dict(bpm=104, lead='glass', groove=True, chords=[(57, MI7), (65, MAJ), (60, ADD9), (67, MAJ)] * 2, mel=[12, 10, 7, 10, 12, 14, 12, 10], moods=['', '_clear', '_rain', '_snow']),
    'groove':    dict(bpm=96, lead='rhodes', groove=True, chords=[(62, MI7), (67, MAJ), (60, M7), (65, M7)] * 2, mel=[5, 7, 10, 7, 5, 3, 5, 7], moods=['', '_clear', '_rain', '_snow']),
    'guitar':    dict(bpm=86, lead='harp', chords=[(64, MIN), (60, MAJ), (67, MAJ), (62, MAJ)] * 2, mel=[7, 4, 7, 11, 12, 11, 7, 4], moods=['', '_clear', '_rain', '_snow']),
    'indiepop':  dict(bpm=98, lead='marimba', groove=True, chords=[(60, MAJ), (67, MAJ), (69, MIN), (65, MAJ)] * 2, mel=[4, 7, 9, 7, 4, 2, 0, 2], moods=['', '_clear', '_rain', '_snow']),
    'kalimba':   dict(bpm=90, lead='kalimba', chords=[(62, MI7), (67, MAJ), (60, ADD9), (69, MI7)] * 2, mel=[2, 9, 7, 5, 4, 7, 9, 7], moods=['', '_clear', '_rain', '_snow']),
    'musicbox':  dict(bpm=84, lead='celesta', brush=False, chords=[(60, MAJ), (65, MAJ), (67, MAJ), (60, MAJ)] * 2, mel=[0, 4, 7, 12, 7, 4, 5, 2], moods=['', '_clear', '_rain', '_snow']),
    'rhodes':    dict(bpm=82, lead='rhodes', chords=[(62, MI7), (67, M7), (60, M7), (69, MI7)] * 2, mel=[5, 9, 12, 9, 7, 5, 4, 2], moods=['', '_clear', '_rain', '_snow']),
    'rnb':       dict(bpm=88, lead='rhodes', groove=True, chords=[(64, MI7), (69, MI7), (62, M7), (67, M7)] * 2, mel=[7, 10, 7, 5, 3, 5, 7, 10], moods=['', '_clear', '_rain', '_snow']),
    'strings':   dict(bpm=74, lead='harp', strings=True, brush=False, chords=[(60, ADD9), (65, M7), (57, MI7), (67, SUS)] * 2, mel=[4, 7, 11, 9, 7, 4, 2, 0], moods=['', '_clear', '_rain', '_snow']),
    'synth':     dict(bpm=94, lead='glass', strings=True, chords=[(57, MI7), (60, M7), (65, M7), (67, SUS)] * 2, mel=[12, 10, 7, 5, 7, 10, 12, 14], moods=['', '_clear', '_rain', '_snow']),
    'tropical':  dict(bpm=100, lead='marimba', groove=True, chords=[(65, MAJ), (67, MAJ), (64, MI7), (69, MIN)] * 2, mel=[9, 12, 9, 7, 4, 7, 9, 12], moods=['', '_clear', '_rain', '_snow']),
}

# notification tones: (title, instrument, [(time, midi)])
TONES = {
    'bright': [('清鈴', 'celesta', [(0, 76), (.16, 72)]), ('水滴', 'marimba', [(0, 72), (.12, 76), (.24, 79)]),
               ('晨光', 'vibes', [(0, 67), (.14, 72), (.28, 76)]), ('漣漪', 'celesta', [(0, 79), (.18, 74)])],
    'warm':   [('木鈴', 'kalimba', [(0, 69), (.16, 76)]), ('琴音', 'piano', [(0, 60), (.12, 64), (.24, 67)]),
               ('撥弦', 'harp', [(0, 62), (.1, 66), (.2, 69), (.3, 74)]), ('微風', 'kalimba', [(0, 74), (.2, 69)])],
    'modern': [('光點', 'glass', [(0, 72), (.13, 76)]), ('脈衝', 'rhodes', [(0, 67), (.1, 67), (.2, 74)]),
               ('霓虹', 'glass', [(0, 76), (.14, 71), (.28, 79)]), ('星環', 'rhodes', [(0, 64), (.16, 71), (.32, 76)])],
}
CLASSIC = {
    'clear_bell': ('celesta', [(0, 76), (.15, 79)]), 'crystal': ('glass', [(0, 74)]),
    'morning': ('kalimba', [(0, 69), (.14, 74)]), 'raindrop': ('marimba', [(0, 76), (.13, 72)]),
    'starlight': ('celesta', [(0, 72), (.1, 76), (.2, 79)]), 'breeze': ('vibes', [(0, 64), (.16, 69), (.32, 72)]),
    'clear_sky': ('marimba', [(0, 67), (.14, 74)]), 'ripple': ('vibes', [(0, 67), (.12, 74), (.24, 69)]),
}


def compose_tone(inst, notes, tail=1.2):
    dur = notes[-1][0] + tail; buf = np.zeros(int(dur * SR))
    for at, m in notes:
        place(buf, INSTR[inst](m, tail, .8), at)
        place(buf, INSTR[inst](m - 12, tail * .8, .14), at)  # a soft octave below rounds it out
    buf = reverb(buf, 1.3, .22)
    buf *= env(len(buf), .002, tail=.3)
    return master(buf, -18.0, .8)


# Earthquake cues: insistent but round. Mid register, repeated rhythm, a low body underneath.
QUAKE = {
    'bright': dict(inst='vibes', first=[69, 72, 76, 72], urgent=(76, 69), update=[72, 76], report=[64, 69, 72]),
    'warm':   dict(inst='kalimba', first=[67, 71, 74, 71], urgent=(74, 67), update=[71, 74], report=[62, 67, 71]),
    'modern': dict(inst='rhodes', first=[65, 69, 72, 69], urgent=(72, 65), update=[69, 72], report=[60, 65, 69]),
}


def body(m, dur, v):
    n = int(dur * SR); t = np.arange(n) / SR
    return v * np.sin(2*np.pi*hz(m)*t) * env(n, .01, d=dur * .6, tail=.05)


def compose_quake(style, kind):
    s = QUAKE[style]; inst = INSTR[s['inst']]
    if kind == 'urgent':
        dur = 3.2; buf = np.zeros(int(dur * SR)); t = 0.
        hi, lo = s['urgent']
        while t < dur - .5:
            for m in (hi, lo):
                place(buf, inst(m, .55, 1.), t); place(buf, body(m - 24, .3, .35), t); t += .2
            t += .08
        buf = reverb(buf, 1.0, .14)
        return master(buf, -12.5, .9, top=4200)
    notes = s[kind]; gap = {'first': .15, 'update': .14, 'report': .18}[kind]
    reps = 2 if kind == 'first' else 1
    dur = reps * (len(notes) * gap + .4) + 1.0; buf = np.zeros(int(dur * SR)); t = 0.
    for _ in range(reps):
        for m in notes:
            place(buf, inst(m, 1.0, .9), t); place(buf, body(m - 24, .35, .25), t); t += gap
        t += .4
    buf = reverb(buf, 1.2, .18); buf *= env(len(buf), .002, tail=.3)
    return master(buf, -14.0 if kind != 'report' else -17.0, .88, top=4500)


def compose_eq_cue(name):
    """Legacy seismic cues (thinn_eq_*, thinn_eew_warning, thinn_seismic_*), re-voiced."""
    spec = {
        'thinn_eq_detect_weak':   ('vibes', [(0, 69)], .35, -20),
        'thinn_eq_detect_medium': ('vibes', [(0, 69), (.16, 72)], .5, -18),
        'thinn_eq_detect_strong': ('vibes', [(0, 72), (.14, 69), (.28, 72)], .7, -16),
        'thinn_eq_eew_primary':   ('rhodes', [(0, 69), (.15, 74)], .6, -15),
        'thinn_eq_eew_update':    ('rhodes', [(0, 74), (.14, 69)], .5, -16),
        'thinn_eq_eew_urgent':    ('rhodes', [(0, 74), (.13, 67), (.26, 74), (.39, 67), (.52, 74), (.65, 67)], 1.1, -14),
        'thinn_eq_gal_medium':    ('marimba', [(0, 67), (.14, 71)], .5, -18),
        'thinn_eq_gal_strong':    ('marimba', [(0, 71), (.13, 67), (.26, 71), (.39, 74)], .8, -16),
        'thinn_eq_report':        ('kalimba', [(0, 64), (.15, 69), (.3, 72)], .6, -19),
        'thinn_eew_warning':      ('rhodes', [(t * .17, 74 if i % 2 == 0 else 67) for i, t in enumerate(range(12))], 2.1, -14),
        'thinn_seismic_drill_alert': ('vibes', [(0, 67), (.18, 72), (.36, 76), (.62, 72), (.8, 76)], 1.7, -16),
        'thinn_seismic_step':     ('kalimba', [(0, 72)], .5, -20),
        'thinn_seismic_allclear': ('celesta', [(0, 67), (.16, 72), (.32, 76)], 1.2, -19),
    }[name]
    inst, notes, dur, level = spec
    buf = np.zeros(int(dur * SR))
    for at, m in notes:
        if at < dur:
            place(buf, INSTR[inst](m, max(.35, dur - at), .9), at)
            place(buf, body(m - 24, .3, .22), at)
    buf = reverb(buf, .9, .14); buf *= env(len(buf), .002, tail=min(.25, dur * .3))
    return master(buf, level, .86, top=4500)


def main():
    manifest = {'rings': {}, 'tones': {}, 'quake': {}, 'alarms': {}, 'classic': {}, 'cues': {}}
    for style, specs in RINGS.items():
        for i, spec in enumerate(specs, 1):
            name = f'thinn_ring_{style}_{i}'; write_ogg(name, compose_loop(spec), 3); manifest['rings'][name] = spec['title']
    for theme, spec in ALARMS.items():
        for mood in spec['moods']:
            name = f'thinn_alarm_{theme}{mood}'; write_ogg(name, compose_loop(spec, mood), 3); manifest['alarms'][name] = theme + mood
    for style, specs in TONES.items():
        for i, (title, inst, notes) in enumerate(specs, 1):
            name = f'thinn_tone_{style}_{i}'; write_ogg(name, compose_tone(inst, notes), 5); manifest['tones'][name] = title
    for key, (inst, notes) in CLASSIC.items():
        name = f'thinn_notify_{key}'; write_ogg(name, compose_tone(inst, notes, 1.0), 5); manifest['classic'][name] = key
    for style in QUAKE:
        for kind in ('first', 'urgent', 'update', 'report'):
            name = f'thinn_quake_{style}_{kind}'; write_ogg(name, compose_quake(style, kind), 5); manifest['quake'][name] = kind
    for name in ('thinn_eq_detect_weak', 'thinn_eq_detect_medium', 'thinn_eq_detect_strong', 'thinn_eq_eew_primary',
                 'thinn_eq_eew_update', 'thinn_eq_eew_urgent', 'thinn_eq_gal_medium', 'thinn_eq_gal_strong', 'thinn_eq_report',
                 'thinn_eew_warning', 'thinn_seismic_drill_alert', 'thinn_seismic_step', 'thinn_seismic_allclear'):
        write_ogg(name, compose_eq_cue(name), 5); manifest['cues'][name] = name
    for group in manifest.values():
        for name in group:
            p = RAW / f'{name}.ogg'
            group[name] = {'title': group[name], 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
    (ROOT / 'tools/audio_sources/R22_SOFT_SOUND_LIBRARY.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=1))
    print(sum(len(v) for v in manifest.values()), 'files')


if __name__ == '__main__':
    main()
