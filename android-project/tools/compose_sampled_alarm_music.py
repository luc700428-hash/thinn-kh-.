#!/usr/bin/env python3
"""Compose and render every alarm music loop with sampled instruments.

All twenty themes keep their ids, files and manifests; only the music changes. Each theme has
its own instruments, tempo, meter, progression and motif. Each weather scene re-arranges the
same piece: clear skies get brighter voicings and a bell doubling, rain drops the drums and
softens, snow moves up into celesta and music box, storms lean on the relative minor, fog and
haze let a pad carry a thinner melody. Real weather recordings are mixed in separately at
playback time, so the music never fights the rain or the birds.

Instruments come from FluidR3 GM (MIT licence) rendered with FluidSynth. Loops are made
seamless by folding the reverb tail back onto the start; the last sample equals the first and
the slope across the join is preserved, as the asset tests require.

Usage: python3 tools/compose_sampled_alarm_music.py [--only THEME] [--preview DIR]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import mido
import numpy as np
from scipy import signal

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
RAW = ROOT / 'app/src/main/res/raw'
SOUNDFONT = Path('/usr/share/sounds/sf2/FluidR3_GM.sf2')
PPQ = 480

ORIGINAL_SCENES = ['sunny', 'drizzle', 'light_rain', 'moderate_rain', 'heavy_rain', 'thunderstorm',
                   'severe_thunderstorm', 'haze', 'fog', 'dense_fog', 'dust_storm', 'light_snow',
                   'moderate_snow', 'heavy_snow', 'neutral']
VARIANTS = ['neutral', 'clear', 'rain', 'snow']

# GM programs (0-based)
PIANO, BRIGHT_PIANO, EPIANO, DX_EPIANO, CELESTA, GLOCK, MUSICBOX, VIBES, MARIMBA, XYLO = 0, 1, 4, 5, 8, 9, 10, 11, 12, 13
NYLON, STEEL, CLEAN_GTR = 24, 25, 27
AC_BASS, FINGER_BASS, SLAP_BASS, SYNTH_BASS = 32, 33, 36, 38
CELLO, PIZZ, HARP, STRINGS, SLOW_STRINGS, SYNTH_STRINGS = 42, 45, 46, 48, 49, 50
BRASS, FLUTE, PAN_FLUTE, SHAKUHACHI, OCARINA = 61, 73, 75, 77, 79
SQUARE_LEAD, CHIFF_LEAD = 80, 83
WARM_PAD, POLY_PAD, CHOIR_PAD, HALO_PAD, SWEEP_PAD = 89, 90, 91, 94, 95
CRYSTAL, STEEL_DRUMS, KALIMBA = 98, 114, 108

MAJOR = [0, 2, 4, 5, 7, 9, 11]


def scale_note(root: int, degree: int) -> int:
    octave, step = divmod(degree, 7)
    return root + 12 * octave + MAJOR[step]


def chord_tones(root: int, degree: int, seventh: bool) -> list[int]:
    tones = [scale_note(root, degree + k) for k in (0, 2, 4)]
    if seventh:
        tones.append(scale_note(root, degree + 6))
    return tones


# ---------------------------------------------------------------- themes
# progression: scale degree per bar (0 = I). A loop always ends away from I so bar 1 resolves it.
THEMES: dict[str, dict] = {
    # six weather-adaptive originals: 44.1 kHz, fifteen scene arrangements each
    'dawn':   dict(bpm=96,  meter=4, key=62, bars=8, lead=PIANO,       comp=PIANO,      bass=AC_BASS,     pad=STRINGS,      drums='none',   style='flowing',
                   prog=[0, 4, 5, 3, 0, 4, 3, 4], motif=[0, 2, 4, 7, 4, 2], label='晨光序曲'),
    'rain':   dict(bpm=88,  meter=4, key=60, bars=8, lead=PIANO,       comp=EPIANO,     bass=FINGER_BASS, pad=WARM_PAD,     drums='soft',   style='ballad',
                   prog=[5, 3, 0, 4, 5, 3, 1, 4], motif=[4, 2, 0, 2, 4, 5], label='雨光節拍'),
    'wood':   dict(bpm=100, meter=4, key=65, bars=8, lead=MARIMBA,     comp=NYLON,      bass=AC_BASS,     pad=None,         drums='hand',   style='bouncy',
                   prog=[0, 3, 4, 0, 5, 3, 1, 4], motif=[0, 4, 2, 4, 5, 4], label='森間晨行'),
    'water':  dict(bpm=78,  meter=3, key=63, bars=8, lead=HARP,        comp=HARP,       bass=None,        pad=HALO_PAD,     drums='none',   style='waltz',
                   prog=[0, 5, 3, 4, 0, 2, 3, 4], motif=[4, 5, 7, 5, 4, 2], label='水漣初醒'),
    'fire':   dict(bpm=108, meter=4, key=67, bars=8, lead=STEEL,       comp=STEEL,      bass=FINGER_BASS, pad=None,         drums='light',  style='strum',
                   prog=[0, 4, 5, 3, 0, 4, 3, 4], motif=[2, 4, 5, 4, 2, 0], label='晴日躍動'),
    'metal':  dict(bpm=92,  meter=4, key=69, bars=8, lead=GLOCK,       comp=VIBES,      bass=None,        pad=POLY_PAD,     drums='none',   style='bells',
                   prog=[0, 3, 5, 4, 0, 3, 1, 4], motif=[7, 4, 5, 2, 4, 0], label='霜光鈴奏'),
    # eight palettes: 32 kHz, four arrangements, 8–30 s
    'guitar':   dict(bpm=94,  meter=4, key=64, bars=8, lead=STEEL,  comp=NYLON,   bass=None,        pad=None,       drums='none',  style='fingerpick',
                     prog=[0, 4, 5, 3, 0, 2, 3, 4], motif=[4, 2, 4, 5, 7, 4], label='晨光吉他'),
    'flute':    dict(bpm=88,  meter=3, key=62, bars=8, lead=FLUTE,  comp=HARP,    bass=None,        pad=STRINGS,    drums='none',  style='waltz',
                     prog=[0, 3, 4, 0, 5, 1, 3, 4], motif=[4, 5, 4, 2, 0, 2], label='竹林笛語'),
    'rhodes':   dict(bpm=76,  meter=4, key=65, bars=8, lead=EPIANO, comp=EPIANO,  bass=AC_BASS,     pad=None,       drums='brush', style='jazz', seventh=True,
                     prog=[1, 4, 0, 5, 1, 4, 2, 4], motif=[6, 4, 2, 4, 3, 1], label='雨窗爵士'),
    'kalimba':  dict(bpm=102, meter=6, key=67, bars=8, lead=KALIMBA, comp=KALIMBA, bass=None,       pad=WARM_PAD,   drums='none',  style='sixeight',
                     prog=[0, 5, 3, 4, 0, 5, 3, 4], motif=[0, 2, 4, 7, 4, 2], label='雨滴拇指琴'),
    'strings':  dict(bpm=82,  meter=3, key=60, bars=8, lead=STRINGS, comp=PIZZ,   bass=CELLO,       pad=SLOW_STRINGS, drums='none', style='waltz',
                     prog=[0, 4, 5, 2, 3, 0, 1, 4], motif=[2, 4, 7, 6, 4, 2], label='山嵐弦樂'),
    'musicbox': dict(bpm=108, meter=4, key=72, bars=8, lead=MUSICBOX, comp=CELESTA, bass=None,      pad=None,       drums='none',  style='bells',
                     prog=[0, 3, 4, 0, 5, 3, 4, 4], motif=[4, 7, 4, 2, 0, 2], label='星露八音盒'),
    'synth':    dict(bpm=84,  meter=4, key=61, bars=8, lead=CRYSTAL, comp=POLY_PAD, bass=SYNTH_BASS, pad=HALO_PAD,  drums='none',  style='ambient', seventh=True,
                     prog=[0, 5, 3, 4, 0, 5, 1, 4], motif=[4, 6, 7, 4, 2, 4], label='極光合成'),
    'groove':   dict(bpm=118, meter=4, key=66, bars=8, lead=MARIMBA, comp=PIZZ,   bass=FINGER_BASS, pad=None,       drums='light', style='bouncy',
                     prog=[0, 3, 0, 4, 5, 3, 1, 4], motif=[0, 2, 4, 2, 5, 4], label='晴日律動'),
    # six pop songs: 32 kHz, sixteen bars, over 30 s
    'citypop':   dict(bpm=104, meter=4, key=63, bars=16, lead=BRASS,   comp=DX_EPIANO, bass=SLAP_BASS,  pad=STRINGS,   drums='pop',   style='pop', seventh=True,
                      prog=[3, 4, 2, 5, 1, 4, 0, 0, 3, 4, 2, 5, 1, 4, 3, 4], motif=[4, 6, 7, 6, 4, 2], label='城市晨光'),
    'tropical':  dict(bpm=110, meter=4, key=65, bars=16, lead=STEEL_DRUMS, comp=MARIMBA, bass=FINGER_BASS, pad=None, drums='tropical', style='pop',
                      prog=[0, 4, 5, 3, 0, 4, 3, 4, 0, 4, 5, 3, 1, 4, 3, 4], motif=[4, 5, 7, 5, 4, 2], label='海風假期'),
    'indiepop':  dict(bpm=116, meter=4, key=64, bars=16, lead=CLEAN_GTR, comp=STEEL, bass=FINGER_BASS, pad=None,   drums='pop',   style='pop',
                      prog=[0, 5, 3, 4, 0, 5, 3, 4, 3, 4, 5, 4, 3, 4, 0, 4], motif=[2, 4, 5, 4, 2, 0], label='晴空出走'),
    'rnb':       dict(bpm=86,  meter=4, key=62, bars=16, lead=EPIANO,  comp=EPIANO,   bass=FINGER_BASS, pad=WARM_PAD,  drums='brush', style='pop', seventh=True,
                      prog=[3, 2, 1, 4, 3, 2, 1, 0, 3, 2, 5, 4, 3, 2, 1, 4], motif=[6, 4, 3, 1, 3, 4], label='雨後慢拍'),
    'futurepop': dict(bpm=120, meter=4, key=66, bars=16, lead=CRYSTAL, comp=POLY_PAD, bass=SYNTH_BASS, pad=HALO_PAD,  drums='pop',   style='pop',
                      prog=[5, 3, 0, 4, 5, 3, 0, 4, 5, 3, 0, 4, 3, 4, 5, 4], motif=[7, 4, 5, 4, 2, 4], label='極光派對'),
    'daybreak':  dict(bpm=92,  meter=4, key=60, bars=16, lead=PIANO,   comp=PIANO,    bass=AC_BASS,    pad=STRINGS,   drums='soft',  style='pop',
                      prog=[0, 4, 5, 3, 0, 4, 3, 4, 5, 3, 0, 4, 5, 3, 1, 4], motif=[4, 2, 0, 2, 4, 7], label='日出情歌'),
}

# ---------------------------------------------------------------- arrangements
SCENE_MOODS = {
    # mood, density (0..1), register shift, velocity scale, extra layer, drums on
    'neutral':             dict(mood='plain',  density=.85, shift=0,   vel=1.00, layer=None,     drums=True),
    'clear':               dict(mood='bright', density=1.0, shift=0,   vel=1.06, layer=GLOCK,    drums=True),
    'sunny':               dict(mood='bright', density=1.0, shift=0,   vel=1.06, layer=GLOCK,    drums=True),
    'rain':                dict(mood='rain',   density=.70, shift=-12, vel=.90,  layer=None,     drums=False),
    'drizzle':             dict(mood='rain',   density=.80, shift=0,   vel=.94,  layer=CELESTA,  drums=False),
    'light_rain':          dict(mood='rain',   density=.72, shift=0,   vel=.92,  layer=None,     drums=False),
    'moderate_rain':       dict(mood='rain',   density=.62, shift=-12, vel=.90,  layer=None,     drums=False),
    'heavy_rain':          dict(mood='rain',   density=.50, shift=-12, vel=.88,  layer=CELLO,    drums=False),
    'thunderstorm':        dict(mood='storm',  density=.60, shift=-12, vel=.92,  layer=CELLO,    drums=False),
    'severe_thunderstorm': dict(mood='storm',  density=.48, shift=-12, vel=.90,  layer=CHOIR_PAD, drums=False),
    'haze':                dict(mood='mist',   density=.55, shift=0,   vel=.88,  layer=WARM_PAD, drums=False),
    'fog':                 dict(mood='mist',   density=.48, shift=0,   vel=.86,  layer=HALO_PAD, drums=False),
    'dense_fog':           dict(mood='mist',   density=.40, shift=-12, vel=.84,  layer=CHOIR_PAD, drums=False),
    'dust_storm':          dict(mood='mist',   density=.55, shift=-12, vel=.86,  layer=SWEEP_PAD, drums=False),
    'snow':                dict(mood='snow',   density=.60, shift=12,  vel=.90,  layer=CELESTA,  drums=False),
    'light_snow':          dict(mood='snow',   density=.68, shift=12,  vel=.90,  layer=CELESTA,  drums=False),
    'moderate_snow':       dict(mood='snow',   density=.58, shift=12,  vel=.88,  layer=MUSICBOX, drums=False),
    'heavy_snow':          dict(mood='snow',   density=.46, shift=0,   vel=.86,  layer=MUSICBOX, drums=False),
}


def storm_progression(prog: list[int]) -> list[int]:
    """Lean toward the relative minor without leaving the key: I→vi, IV→ii, keep V."""
    table = {0: 5, 3: 1, 2: 5}
    return [table.get(d, d) if i % 2 == 0 else d for i, d in enumerate(prog)]


class Score:
    def __init__(self, bpm: int):
        self.bpm = bpm
        self.tracks: dict[int, list[tuple[float, float, int, int]]] = {}
        self.programs: dict[int, int] = {}
        self.volumes: dict[int, int] = {}

    def channel(self, ch: int, program: int, volume: int = 100):
        self.programs[ch] = program
        self.volumes[ch] = volume
        self.tracks.setdefault(ch, [])

    def note(self, ch: int, beat: float, length: float, pitch: int, vel: float):
        pitch = int(min(max(pitch, 24), 108))
        self.tracks[ch].append((beat, max(length, .05), pitch, int(min(max(vel, 1), 127))))

    def to_midi(self, path: Path, tail_beats: float):
        mid = mido.MidiFile(ticks_per_beat=PPQ)
        meta = mido.MidiTrack(); mid.tracks.append(meta)
        meta.append(mido.MetaMessage('set_tempo', tempo=mido.bpm2tempo(self.bpm)))
        for ch, notes in self.tracks.items():
            tr = mido.MidiTrack(); mid.tracks.append(tr)
            events = []
            if ch != 9:
                events.append((0, 0, mido.Message('program_change', channel=ch, program=self.programs[ch])))
            events.append((0, 1, mido.Message('control_change', channel=ch, control=7, value=self.volumes[ch])))
            events.append((0, 1, mido.Message('control_change', channel=ch, control=91, value=70 if ch != 9 else 30)))
            for beat, length, pitch, vel in notes:
                on = int(round(beat * PPQ)); off = int(round((beat + length) * PPQ))
                events.append((on, 3, mido.Message('note_on', channel=ch, note=pitch, velocity=vel)))
                events.append((off, 2, mido.Message('note_off', channel=ch, note=pitch, velocity=0)))
            events.sort(key=lambda e: (e[0], e[1]))
            now = 0
            for tick, _, msg in events:
                tr.append(msg.copy(time=tick - now)); now = tick
            tr.append(mido.MetaMessage('end_of_track', time=int(tail_beats * PPQ)))
        mid.save(str(path))


def compose(theme: str, scene: str, seed: int) -> tuple[Score, float, dict]:
    t = THEMES[theme]; m = SCENE_MOODS[scene]
    rng = random.Random(seed)
    meter, bars, key = t['meter'], t['bars'], t['key']
    beats_per_bar = meter if meter != 6 else 3  # 6/8 is felt as two dotted beats; we count in quarter notes
    bar_len = beats_per_bar if meter != 6 else 3.0
    prog = list(t['prog'])
    if m['mood'] == 'storm':
        prog = storm_progression(prog)
    seventh = t.get('seventh', False) or m['mood'] in ('mist', 'storm')
    s = Score(t['bpm'])
    vel = m['vel']
    lead_prog = t['lead']
    if m['mood'] == 'snow' and lead_prog in (PIANO, EPIANO, DX_EPIANO, STEEL, CLEAN_GTR, BRASS):
        lead_prog = CELESTA if theme != 'musicbox' else MUSICBOX
    if m['mood'] == 'rain' and lead_prog in (BRASS, STEEL_DRUMS, CRYSTAL, CLEAN_GTR):
        lead_prog = EPIANO
    s.channel(0, lead_prog, 104)
    s.channel(1, t['comp'], 82)
    if t['bass'] is not None:
        s.channel(2, t['bass'], 84)
    pad = t['pad'] if m['mood'] not in ('mist',) else (m['layer'] or t['pad'] or WARM_PAD)
    if pad is not None:
        s.channel(3, pad, 62 if m['mood'] != 'mist' else 80)
    layer = m['layer'] if m['layer'] not in (None, pad) else None
    if layer is not None:
        s.channel(4, layer, 58)
    drums = t['drums'] if (m['drums'] and t['drums'] != 'none') else ('shaker' if m['mood'] == 'bright' and t['drums'] == 'none' and t['style'] in ('bouncy', 'strum', 'fingerpick') else None)
    if drums:
        s.channel(9, 0, 70)

    # ---- melody: motif-driven, chord tones on strong beats, stepwise elsewhere
    lead_root = key + m['shift'] + (12 if t['lead'] in (GLOCK, MUSICBOX, CELESTA, KALIMBA, CRYSTAL) else 0)
    motif = t['motif']
    rhythms = {
        'flowing': [(0, 1.5), (1.5, .5), (2, 1), (3, 1)],
        'ballad': [(0, 1), (1, .5), (1.5, 1.5), (3, 1)],
        'bouncy': [(0, .5), (.5, .5), (1, 1), (2, .5), (2.5, .5), (3, 1)],
        'waltz': [(0, 1.5), (1.5, .5), (2, 1)],
        'strum': [(0, .75), (.75, .75), (1.5, .5), (2, 1), (3, 1)],
        'bells': [(0, .5), (.5, .5), (1, .5), (1.5, .5), (2, 1), (3, 1)],
        'fingerpick': [(0, 1), (1, .5), (1.5, .5), (2, 1.5), (3.5, .5)],
        'jazz': [(0, .67), (.67, .33), (1, 1.5), (2.5, .5), (3, 1)],
        'sixeight': [(0, 1), (1, .5), (1.5, 1), (2.5, .5)],
        'ambient': [(0, 2), (2, 1), (3, 1)],
        'pop': [(0, .75), (.75, .75), (1.5, 1), (2.5, .5), (3, 1)],
    }[t['style']]
    hooks: list[list[int]] = []
    prev = scale_note(lead_root, motif[0])
    for bar in range(bars):
        start = bar * bar_len
        deg = prog[bar]
        tones = chord_tones(lead_root, deg, seventh)
        phrase_pos = bar % 4
        cadence = phrase_pos == 3
        pattern = rhythms if not cadence else [(0, 1), (1, bar_len - 1)]
        figure = motif if (bar // 4) % 2 == 0 else [x + (1 if i % 2 else 0) for i, x in enumerate(motif)]
        bar_pitches = []
        for i, (pos, length) in enumerate(pattern):
            if pos >= bar_len:
                continue
            length = min(length, bar_len - pos)
            if not cadence and i > 0 and rng.random() > m['density']:
                continue
            strong = pos in (0, 2) or (meter == 3 and pos == 0)
            target = scale_note(lead_root, figure[i % len(figure)] + (deg if phrase_pos in (1, 2) else 0) % 3)
            if strong or cadence:
                choices = tones + [x + 12 for x in tones] + [x - 12 for x in tones]
                pitch = min(choices, key=lambda x: abs(x - target) + .6 * abs(x - prev))
            else:
                choices = [scale_note(lead_root, d) for d in range(-7, 15)]
                pitch = min(choices, key=lambda x: abs(x - target) + .9 * abs(x - prev))
            pitch = min(max(pitch, lead_root - 5), lead_root + 19)
            v = (88 if strong else 74) * vel + rng.randint(-5, 5)
            s.note(0, start + pos, length * .95, pitch, v)
            bar_pitches.append(pitch - key)
            prev = pitch
        hooks.append(bar_pitches[:4])

    # ---- accompaniment
    comp_root = key - 12 + (12 if t['comp'] in (GLOCK, CELESTA, MUSICBOX, KALIMBA) else 0) + (12 if m['mood'] == 'snow' else 0)
    for bar in range(bars):
        start = bar * bar_len; deg = prog[bar]
        tones = sorted(chord_tones(comp_root, deg, seventh))
        tones = [x if x >= comp_root - 2 else x + 12 for x in tones]
        style = t['style']
        cv = 62 * vel
        if style in ('flowing', 'fingerpick', 'waltz', 'sixeight', 'ballad', 'bells') or m['mood'] in ('rain', 'snow', 'mist'):
            step = .5 if meter != 3 else .5
            if m['mood'] in ('mist',):
                step = 1.0
            seq = [tones[0], tones[1], tones[2], tones[1] + 12 if len(tones) < 4 else tones[3], tones[2], tones[1]]
            i = 0; b = 0.0
            while b < bar_len - 1e-6:
                s.note(1, start + b, step * 1.8, seq[i % len(seq)], cv - (8 if i % 2 else 0) + rng.randint(-4, 4))
                b += step; i += 1
        elif style in ('strum', 'pop', 'bouncy'):
            hits = [0, 1, 1.5, 2.5, 3] if meter == 4 else [0, 1, 2]
            for h in hits:
                if h >= bar_len:
                    continue
                for k, p in enumerate(tones):
                    s.note(1, start + h + k * .018, .45 if style != 'pop' else .9, p, cv - 6 + rng.randint(-4, 4))
        elif style == 'jazz':
            for h in (0, 1.5, 2.5):
                for k, p in enumerate(tones):
                    s.note(1, start + h + k * .01, .9, p, cv - 4)
        else:  # ambient
            for k, p in enumerate(tones):
                s.note(1, start, bar_len, p, cv - 10)
        # bass
        if 2 in s.programs:
            bass_root = key - 24 + MAJOR[deg % 7]
            if style in ('pop', 'bouncy', 'strum', 'jazz'):
                pattern = [(0, .9), (1.5, .4), (2, .9), (3.5, .4)] if meter == 4 else [(0, 1.8), (2, .9)]
            else:
                pattern = [(0, bar_len * .95)] if meter != 4 else [(0, 1.8), (2, 1.8)]
            for pos, ln in pattern:
                if pos < bar_len:
                    p = bass_root + (7 if pos in (1.5, 3.5) else 0)
                    s.note(2, start + pos, ln, p, 76 * vel)
        # pad
        if 3 in s.programs:
            for k, p in enumerate(sorted(chord_tones(key - 12, deg, seventh))):
                s.note(3, start, bar_len, p + (12 if m['mood'] == 'snow' else 0), 52 * vel)
        # weather layer
        if layer is not None:
            if layer in (GLOCK, CELESTA, MUSICBOX):
                hi = sorted(chord_tones(key + 12 + (12 if m['mood'] == 'snow' else 0), deg, False))
                for j, pos in enumerate([0, 1.5, 2.5] if meter == 4 else [0, 1.5]):
                    if pos < bar_len and rng.random() < .8:
                        s.note(4, start + pos, .8, hi[(j + bar) % len(hi)], 46 * vel)
            else:
                for p in sorted(chord_tones(key - 12 - (12 if layer == CELLO else 0), deg, False))[:2 if layer == CELLO else 3]:
                    s.note(4, start, bar_len, p, 50 * vel)
        # drums (GM channel 10)
        if drums:
            if drums in ('soft', 'brush'):
                kit = [(0, 36, 50), (1, 37 if drums == 'soft' else 38, 34), (2, 36, 44), (3, 37 if drums == 'soft' else 38, 34)]
                kit += [(x * .5, 42 if drums == 'soft' else 44, 26) for x in range(int(bar_len * 2))]
            elif drums == 'hand':
                kit = [(0, 64, 46), (1, 63, 40), (1.5, 63, 32), (2, 64, 44), (3, 62, 40), (3.5, 63, 32)]
            elif drums == 'shaker':
                kit = [(x * .5, 70, 30 if x % 2 else 40) for x in range(int(bar_len * 2))]
            elif drums == 'tropical':
                kit = [(0, 36, 58), (1.5, 36, 46), (2, 39, 44), (3, 36, 52), (.75, 75, 40), (2.75, 75, 40)] + [(y * .5, 70, 30) for y in range(int(bar_len * 2))]
            else:  # light / pop
                kit = [(0, 36, 62), (1, 38, 48), (2, 36, 56), (2.5, 36, 40), (3, 38, 50)] + [(y * .5, 42, 30 if y % 2 else 38) for y in range(int(bar_len * 2))]
            for pos, n, v in kit:
                if pos < bar_len:
                    s.note(9, start + pos, .2, n, v * vel + rng.randint(-4, 4))
    total_beats = bars * bar_len
    score_info = dict(bpm=t['bpm'], meter=meter, key=key, chords=prog, hooks=hooks[:8], label=t['label'])
    return s, total_beats, score_info


# ---------------------------------------------------------------- render
def render(score: Score, total_beats: float, rate: int) -> np.ndarray:
    seconds = total_beats * 60.0 / score.bpm
    tail = 4.0
    with tempfile.TemporaryDirectory() as tmp:
        mid = Path(tmp) / 'x.mid'; wav = Path(tmp) / 'x.wav'
        score.to_midi(mid, tail_beats=tail * score.bpm / 60.0)
        subprocess.run(['fluidsynth', '-ni', '-g', '0.5', '-r', str(rate), '-R', '1', '-C', '1',
                        '-o', 'synth.reverb.room-size=0.62', '-o', 'synth.reverb.damp=0.35',
                        '-o', 'synth.reverb.width=0.9', '-o', 'synth.reverb.level=0.55',
                        '-F', str(wav), str(SOUNDFONT), str(mid)], check=True, capture_output=True)
        with wave.open(str(wav)) as w:
            ch = w.getnchannels(); data = np.frombuffer(w.readframes(w.getnframes()), dtype='<i2').astype(np.float64) / 32768
        if ch == 2:
            data = data.reshape(-1, 2).mean(axis=1)
    loop = int(round(seconds * rate))
    if len(data) < loop:
        data = np.pad(data, (0, loop - len(data)))
    body = data[:loop].copy()
    rest = data[loop:]
    body[:len(rest)] += rest[:loop]  # fold reverb and release tails onto the start
    return body


def master(x: np.ndarray, rate: int, target_rms: float, peak: float) -> np.ndarray:
    b, a = signal.butter(2, 55 / (rate / 2), 'highpass')
    # circular filtering keeps the loop seamless
    x = np.concatenate([x[-rate:], x, x[:rate]])
    x = signal.lfilter(b, a, x)[rate:-rate]
    # gentle glue compression on the envelope
    env = np.sqrt(signal.lfilter([0.002], [1, -0.998], np.concatenate([x[-rate:], x])**2)[rate:]) + 1e-9
    gain = np.minimum(1.0, (0.18 / env) ** 0.35)
    x = x * gain
    rms = math.sqrt(float(np.mean(x * x))) + 1e-12
    x = x * (target_rms / rms)
    # soft limiter: transparent below 70 % of the ceiling
    knee = peak * .7
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (peak - knee) * np.tanh((np.abs(x[over]) - knee) / (peak - knee)))
    return x


def write_loop(path: Path, x: np.ndarray, rate: int) -> dict:
    pcm = np.clip(np.round(x * 32767), -32767, 32767).astype(np.int64)
    loop = list(pcm)
    # Close the loop exactly: last == first and the slope across the join is preserved.
    loop.append(loop[0])
    loop[-2] = loop[0] - (loop[1] - loop[0])
    arr = np.array(loop, dtype='<i2')
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(arr.tobytes())
    f = arr.astype(np.float64) / 32768
    return dict(sha256=hashlib.sha256(path.read_bytes()).hexdigest(), sample_rate=rate, frames=len(arr),
                seconds=round(len(arr) / rate, 5), peak_dbfs=round(20 * math.log10(max(np.max(np.abs(f)), 1e-9)), 3),
                rms_dbfs=round(20 * math.log10(math.sqrt(float(np.mean(f * f))) + 1e-12), 3), loop_jump_pcm=0)


def ogg(src: Path, dst: Path):
    dst.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(src), '-c:a', 'libvorbis', '-q:a', '4', '-ac', '1', str(dst)], check=True)
    return hashlib.sha256(dst.read_bytes()).hexdigest()


def seed_for(theme: str, scene: str) -> int:
    return int(hashlib.sha256(f'{theme}/{scene}'.encode()).hexdigest()[:8], 16)


def build(only: str | None, preview: Path | None):
    suite = json.loads((ASSETS / 'next_weather/wake_suite_manifest.json').read_text(encoding='utf-8'))
    palette = json.loads((ASSETS / 'next_weather/wake_palette_manifest.json').read_text(encoding='utf-8'))
    pop = json.loads((ASSETS / 'next_weather/pop_weather_manifest.json').read_text(encoding='utf-8'))
    done = 0
    # originals: 44.1 kHz, fifteen scenes, peak under -1 dBFS
    for a in suite['assets']:
        if a.get('category') != 'alarm' or (only and a['theme'] != only):
            continue
        score, beats, info = compose(a['theme'], a['scene'], seed_for(a['theme'], a['scene']))
        x = master(render(score, beats, 44100), 44100, .085, .80)
        meta = write_loop(ASSETS / a['path'], x, 44100)
        a.update(meta); a['bpm'] = info['bpm']; a['authorship'] = 'original composition, sampled instruments (FluidR3 GM, MIT)'
        done += 1
    for theme in suite['themes']:
        if only and theme != only:
            continue
        for scene, suffix in (('neutral', ''), ('sunny', '_clear'), ('moderate_rain', '_rain')):
            ogg(ASSETS / f'next_weather/music/{theme}/{scene}.wav', RAW / f'thinn_alarm_{theme}{suffix}.ogg')
    # palettes and pop: 32 kHz, peak ≤ 0.70, RMS 0.04–0.13
    for manifest, kind in ((palette, 'palette'), (pop, 'pop')):
        for a in manifest['assets']:
            if only and a['theme'] != only:
                continue
            score, beats, info = compose(a['theme'], a['variant'], seed_for(a['theme'], a['variant']))
            x = master(render(score, beats, 32000), 32000, .085, .68)
            a.update(write_loop(ASSETS / a['path'], x, 32000))
            a['fallback_sha256'] = ogg(ASSETS / a['path'], ROOT / a['fallback'])
            if kind == 'pop':
                a['score_sha256'] = hashlib.sha256(json.dumps(info, sort_keys=True).encode()).hexdigest()
            prof = manifest['profiles'][a['theme']]
            prof['bpm'] = info['bpm']
            if kind == 'palette':
                prof['meter'] = info['meter']; prof['root'] = info['key']; prof['chords'] = info['chords']; prof['voice'] = prof.get('voice')
            else:
                prof['key'] = info['key']; prof['chords'] = info['chords']
                if a['variant'] == 'neutral':
                    prof['hooks'] = info['hooks']
            done += 1
    palette['authorship'] = 'Original compositions rendered with sampled instruments (FluidR3 GM soundfont, MIT licence)'
    pop['authorship'] = 'Original scores rendered with sampled instruments (FluidR3 GM soundfont, MIT licence); no third-party melodies'
    for name, data in (('wake_suite_manifest.json', suite), ('wake_palette_manifest.json', palette), ('pop_weather_manifest.json', pop)):
        (ASSETS / 'next_weather' / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'rendered {done} loops')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--only'); ap.add_argument('--preview', type=Path)
    args = ap.parse_args()
    if not SOUNDFONT.is_file():
        sys.exit('FluidR3_GM.sf2 is required: apt-get install fluid-soundfont-gm fluidsynth')
    build(args.only, args.preview)
