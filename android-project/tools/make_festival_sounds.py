#!/usr/bin/env python3
"""Festival easter-egg sounds, made from scratch here (no samples, no recordings, no voices).

Fireworks (launch whistle, two booms, crackle), a firecracker string, sleigh bells, a music-box
"Jingle Bells" (J. L. Pierpont, 1857 — public domain), a countdown tick and a midnight chime, a
friendly ghost "woo" and a dragon-boat drum. Deterministic: the same seed gives the same files.
Requires numpy, scipy and ffmpeg (libvorbis) only when regenerating.
"""
from pathlib import Path
import subprocess, wave
import numpy as np
from scipy.signal import butter, sosfilt

RAW = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
SR = 44100
rng = np.random.default_rng(20261225)


def t(sec):
    return np.arange(int(sec * SR)) / SR


def filt(x, kind, f, order=4):
    return sosfilt(butter(order, f, kind, fs=SR, output='sos'), x)


def noise(sec):
    return rng.standard_normal(int(sec * SR))


def place(out, x, at):
    a = int(at * SR); b = min(len(out), a + len(x))
    if b > a: out[a:b] += x[:b - a]


def bell(freq, sec, decay, partials=((1, 1), (2.01, .32), (3.02, .12), (4.17, .06))):
    tt = t(sec); y = np.zeros_like(tt)
    for k, a in partials:
        y += a * np.sin(2 * np.pi * freq * k * tt) * np.exp(-tt * decay * (0.7 + 0.5 * k))
    attack = np.minimum(1, tt / 0.004)
    return y * attack


def jingle(sec=.22):
    """One shake of a sleigh bell: bright inharmonic partials, very short."""
    tt = t(sec); y = np.zeros_like(tt)
    for f in (2650, 3980, 5230, 6870, 8120):
        f *= rng.uniform(.97, 1.03)
        y += np.sin(2 * np.pi * f * tt + rng.uniform(0, 6.28)) * np.exp(-tt * rng.uniform(18, 30))
    y += filt(noise(sec), 'highpass', 5000) * np.exp(-tt * 60) * .4
    return y


def write(name, x, peak=.85, fade=.05):
    x = x - np.mean(x)
    f = int(fade * SR)
    if f and len(x) > 2 * f: x[-f:] *= np.linspace(1, 0, f)
    x[:64] *= np.linspace(0, 1, 64)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    tmp = RAW / f'{name}.tmp.wav'
    with wave.open(str(tmp), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-i', str(tmp), '-c:a', 'libvorbis', '-q:a', '4',
                    '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', str(RAW / f'{name}.ogg')], check=True)
    tmp.unlink()


def launch():
    tt = t(1.3); f = 700 + 1300 * (tt / 1.3) ** 1.5
    whistle = np.sin(2 * np.pi * np.cumsum(f) / SR) * .35 * np.minimum(1, tt / .08) * np.exp(-tt * .6)
    hiss = filt(noise(1.3), 'bandpass', [2500, 7000]) * .25 * np.exp(-tt * 2.2)
    return whistle + hiss


def boom(sec, low, decay, crack):
    tt = t(sec)
    body = filt(noise(sec), 'lowpass', low) * np.exp(-tt * decay) * 3.0
    thump = np.sin(2 * np.pi * (55 + 40 * np.exp(-tt * 9)) * tt) * np.exp(-tt * 4) * .9
    snap = filt(noise(sec), 'highpass', 1800) * np.exp(-tt * 40) * crack
    y = body + thump + snap
    echo = np.zeros(len(y) + int(.35 * SR)); place(echo, y, 0); place(echo, filt(y, 'lowpass', 900) * .28, .33)
    return echo


def crackle():
    out = np.zeros(int(2.4 * SR))
    for i in range(170):
        at = rng.uniform(0, 2.1) ** 1.3 / 2.1 ** .3
        pop = filt(noise(.012), 'highpass', 2500) * np.exp(-t(.012) * 300) * rng.uniform(.3, 1) * (1 - at / 2.4)
        place(out, pop, at)
    return out


def firecrackers():
    out = np.zeros(int(4.6 * SR)); at = .02
    while at < 4.3:
        pop = filt(noise(.05), 'highpass', 900) * np.exp(-t(.05) * 120) * rng.uniform(.45, 1)
        pop += np.sin(2 * np.pi * 180 * t(.05)) * np.exp(-t(.05) * 90) * .4
        place(out, pop, at); at += rng.uniform(.028, .095)
    return out


def sleigh(sec=5.0, beat=.3):
    out = np.zeros(int(sec * SR))
    for i in range(int(sec / beat * 2)):
        at = i * beat / 2 + rng.uniform(-.008, .008)
        if at < 0 or at > sec - .3: continue
        place(out, jingle() * (1 if i % 2 == 0 else .55), at)
    return out * np.minimum(1, np.linspace(0, sec, len(out)) / .3)


NOTE = dict(C3=130.81, D3=146.83, E3=164.81, F3=174.61, G3=196.0, A3=220.0, B3=246.94,
            C5=523.25, D5=587.33, E5=659.25, F5=698.46, G5=783.99, A5=880.0)


def carol():
    """Jingle Bells chorus (public domain) on a music box with soft bass and sleigh bells."""
    q = .36
    melody = [('E5', 1), ('E5', 1), ('E5', 2), ('E5', 1), ('E5', 1), ('E5', 2), ('E5', 1), ('G5', 1), ('C5', 1.5), ('D5', .5), ('E5', 4),
              ('F5', 1), ('F5', 1), ('F5', 1.5), ('F5', .5), ('F5', 1), ('E5', 1), ('E5', 1), ('E5', .5), ('E5', .5),
              ('E5', 1), ('D5', 1), ('D5', 1), ('E5', 1), ('D5', 2), ('G5', 2),
              ('E5', 1), ('E5', 1), ('E5', 2), ('E5', 1), ('E5', 1), ('E5', 2), ('E5', 1), ('G5', 1), ('C5', 1.5), ('D5', .5), ('E5', 4),
              ('F5', 1), ('F5', 1), ('F5', 1.5), ('F5', .5), ('F5', 1), ('E5', 1), ('E5', 1), ('E5', .5), ('E5', .5),
              ('G5', 1), ('G5', 1), ('F5', 1), ('D5', 1), ('C5', 4)]
    bass = ['C3', 'C3', 'C3', 'C3', 'F3', 'C3', 'G3', 'G3', 'C3', 'C3', 'C3', 'C3', 'F3', 'C3', 'G3', 'C3']
    total = sum(d for _, d in melody) * q
    out = np.zeros(int((total + 2.5) * SR)); at = 0.0
    for n, d in melody:
        place(out, bell(NOTE[n], 2.2, 2.2) * .55, at); at += d * q
    for bar, n in enumerate(bass):
        for half in (0, 2):
            place(out, bell(NOTE[n], 1.6, 3.0, ((1, 1), (2, .2))) * .32, (bar * 4 + half) * q)
            place(out, bell(NOTE[n] * 1.5, 1.4, 3.4, ((1, 1), (2, .15))) * .16, (bar * 4 + half + 1) * q)
    for beat in range(int(total / q)):
        place(out, jingle() * .16, beat * q)
    return out


def tick():
    return bell(1760, .35, 16, ((1, 1), (2.76, .25))) * .6 + filt(noise(.35), 'bandpass', [1500, 4000]) * np.exp(-t(.35) * 90) * .2


def chime():
    out = np.zeros(int(3.5 * SR))
    for i, f in enumerate((523.25, 659.25, 783.99, 1046.5)):
        place(out, bell(f, 3.2, 1.2) * .5, i * .07)
    return out


def ghost():
    tt = t(2.4)
    pitch = 330 + 90 * np.sin(np.pi * tt / 2.4) - 70 * (tt / 2.4) ** 2 + 6 * np.sin(2 * np.pi * 5.5 * tt)
    env = np.sin(np.pi * np.minimum(1, tt / 2.4)) ** 1.5
    tone = np.sin(2 * np.pi * np.cumsum(pitch) / SR) + .25 * np.sin(4 * np.pi * np.cumsum(pitch) / SR)
    breath = filt(noise(2.4), 'bandpass', [400, 1800]) * .25
    return (tone * .6 + breath) * env


def drum():
    out = np.zeros(int(4.4 * SR))
    pattern = [0, .5, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 3.25, 3.5]
    for i, at in enumerate(pattern):
        tt = t(.6)
        hit = np.sin(2 * np.pi * (70 + 60 * np.exp(-tt * 25)) * tt) * np.exp(-tt * 7)
        hit += filt(noise(.6), 'lowpass', 900) * np.exp(-tt * 30) * .35
        place(out, hit * (1 if i % 2 == 0 else .75), at)
    return out


def main():
    write('festival_launch', launch(), .5)
    write('festival_boom', boom(2.6, 260, 2.2, .5), .9)
    write('festival_boom_small', boom(1.6, 520, 3.4, .7), .75)
    write('festival_crackle', crackle(), .55)
    write('festival_firecracker', firecrackers(), .8)
    write('festival_sleigh', sleigh(), .6)
    write('festival_carol', carol(), .7, fade=1.2)
    write('festival_tick', tick(), .5)
    write('festival_chime', chime(), .7, fade=.6)
    write('festival_ghost', ghost(), .5)
    write('festival_drum', drum(), .8)


if __name__ == '__main__':
    main()
