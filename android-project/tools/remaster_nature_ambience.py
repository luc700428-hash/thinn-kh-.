#!/usr/bin/env python3
"""Reproducible 24-second weather soundscapes and thunder claps from credited field recordings.

r27: every scene is now a real recording: rain on a rural roof and leaves (light and heavy), a
city drizzle, a storm with distant thunder, Alishan dawn birds, wind in leaves, crickets, winter
wind. Thunder claps are five separate real recordings, played at random after each flash.

No generated bird/cricket oscillators, bells, melodies, or synthesized rainfall. The CC0 MP3
excerpts are committed in tools/audio_sources with SOURCES.json; nothing is downloaded here.
Requires numpy, scipy and ffmpeg only when deliberately regenerating the assets.
"""
from pathlib import Path
import hashlib, json, subprocess, wave
import numpy as np
from scipy.signal import butter, sosfilt

PROJECT = Path(__file__).resolve().parents[1]
ASSETS = PROJECT / 'app/src/main/assets'; DEST = ASSETS / 'next_weather'
RAW = PROJECT / 'app/src/main/res/raw'
SOURCES = Path(__file__).parent / 'audio_sources'; SR = 44100; SECONDS = 24; N = SR * SECONDS


def decode(name):
    data = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(SOURCES / (name + '.mp3')),
                                    '-ar', str(SR), '-ac', '1', '-f', 'f32le', 'pipe:1'])
    return np.frombuffer(data, dtype='<f4').astype(np.float64)


def filt(x, kind, f, order=4):
    return sosfilt(butter(order, f, kind, fs=SR, output='sos'), x)


def db(x):
    return 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)


def steady(x, secs=26):
    """The 26 s stretch whose loudness changes least, avoiding clipped peaks."""
    n = int(secs * SR); best = None
    for s in np.arange(0.0, max(0.1, len(x) / SR - secs), 0.5):
        a = int(s * SR); seg = x[a:a + n]
        if len(seg) < n: break
        if np.max(np.abs(seg)) > 0.995: continue
        r = np.sqrt((seg[:n // SR * SR].reshape(-1, SR) ** 2).mean(1)) + 1e-9
        score = np.std(20 * np.log10(r))
        if best is None or score < best[0]: best = (score, a)
    a = best[1] if best else 0
    return x[a:a + n]


def loop(seg, fade=2.0):
    """24 s with the last two seconds folded into the first: no seam when it repeats."""
    f = int(fade * SR); out = seg[:N].copy(); t = np.linspace(0, 1, f)
    out[:f] = seg[:f] * np.sqrt(t) + seg[N:N + f] * np.sqrt(1 - t)
    return out


def at(x, target_db):
    return x * 10 ** ((target_db - db(x)) / 20)


def finish(x, target_db):
    """Level, a soft limiter that keeps peaks under 0.29, no DC, and identical loop ends."""
    x = x - np.mean(x)
    for _ in range(4):
        x = at(x, target_db)
        knee = 0.2
        x = np.where(np.abs(x) > knee, np.sign(x) * (knee + 0.09 * np.tanh((np.abs(x) - knee) / 0.09)), x)
        x = x - np.mean(x)
    pcm = np.round(np.clip(x, -1, 1) * 32767).astype(np.int64)
    pcm[-1] = pcm[0]; pcm[-2] = pcm[-1] - (pcm[1] - pcm[0])
    pcm -= int(round(pcm.mean()))
    pcm[-1] = pcm[0]; pcm[-2] = pcm[-1] - (pcm[1] - pcm[0])
    return pcm.astype('<i2')


def main():
    credits = json.loads((SOURCES / 'SOURCES.json').read_text())
    for source in credits:
        assert hashlib.sha256((SOURCES / (source['id'] + '.mp3')).read_bytes()).hexdigest() == source['sha256']
    s = {k: decode(k) for k in ['rain_light', 'rain_heavy', 'rain_city', 'storm', 'birds_alishan', 'wind_leaves', 'crickets', 'snow_wind', 'gust']}
    light = loop(steady(filt(s['rain_light'], 'highpass', 60)))
    heavy = loop(steady(filt(s['rain_heavy'], 'highpass', 40)))
    city = loop(steady(filt(s['rain_city'], 'highpass', 250)))
    storm = loop(steady(filt(s['storm'], 'highpass', 30)))
    birds = loop(steady(filt(filt(s['birds_alishan'], 'highpass', 150), 'lowpass', 4500)))
    leaves = loop(steady(filt(filt(s['wind_leaves'], 'highpass', 60), 'lowpass', 4000)))
    night = loop(steady(filt(filt(s['crickets'], 'highpass', 200), 'lowpass', 3400)))
    snow = loop(steady(filt(s['snow_wind'], 'highpass', 40)))
    gust = loop(steady(filt(s['gust'], 'highpass', 40)))
    scenes = {
        'drizzle': (filt(city, 'lowpass', 7000), -31.0, ['rain_city']),
        'light_rain': (light, -28.5, ['rain_light']),
        'moderate_rain': (at(light, -20) * .7 + at(heavy, -20) * .7, -27.0, ['rain_light', 'rain_heavy']),
        'heavy_rain': (heavy, -25.5, ['rain_heavy']),
        'thunderstorm': (at(storm, -20) * .8 + at(heavy, -20) * .5, -26.0, ['storm', 'rain_heavy']),
        'severe_thunderstorm': (at(storm, -20) * .8 + at(heavy, -20) * .8, -25.2, ['storm', 'rain_heavy']),
        'sunny': (birds, -29.0, ['birds_alishan']),
        'cloudy_day': (at(leaves, -20) + at(birds, -20) * .25, -29.5, ['wind_leaves', 'birds_alishan']),
        'clear_night': (night, -31.0, ['crickets']),
        'cloudy_night': (at(filt(night, 'lowpass', 3000), -20) + at(leaves, -20) * .3, -31.5, ['crickets', 'wind_leaves']),
        'fog': (filt(leaves, 'lowpass', 1800), -31.5, ['wind_leaves']),
        'dense_fog': (filt(leaves, 'lowpass', 1100), -31.9, ['wind_leaves']),
        'haze': (filt(at(leaves, -20) + at(night, -20) * .2, 'lowpass', 2500), -31.5, ['wind_leaves', 'crickets']),
        'light_snow': (filt(snow, 'lowpass', 5000), -31.5, ['snow_wind']),
        'moderate_snow': (snow, -30.0, ['snow_wind']),
        'heavy_snow': (at(snow, -20) + at(gust, -20) * .6, -28.5, ['snow_wind', 'gust']),
        'dust_storm': (gust, -27.0, ['gust']),
    }
    records = []
    for scene, (x, target, sources) in scenes.items():
        pcm = finish(x, target)
        path = DEST / 'ambience' / f'{scene}.wav'
        with wave.open(str(path), 'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        rms = float(np.sqrt(np.mean((pcm / 32768.0) ** 2)))
        records.append(dict(path=f'next_weather/ambience/{scene}.wav', scene=scene, category='ambience',
                            authorship='CC0 field recordings (see tools/audio_sources/SOURCES.json); trimmed, filtered and looped',
                            sources=sorted(sources), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                            sample_rate=SR, channels=1, frames=N, seconds=SECONDS, rms_dbfs=round(20 * np.log10(rms), 3),
                            peak_dbfs=round(20 * np.log10(np.max(np.abs(pcm)) / 32768.0), 3), loop_jump_pcm=int(pcm[0] - pcm[-1])))
    manifest = dict(schema=2, description='Real CC0 field recordings, 24 s crossfaded loops; no generated sounds.',
                    human_listening_or_wake_efficacy_verified=False, assets=records)
    (DEST / 'nature_ambience_manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    nonrain = dict(manifest, description='Real CC0 Alishan birds, wind in leaves and cricket field recordings.',
                   assets=[r for r in records if r['scene'] in ('sunny', 'clear_night', 'cloudy_day', 'cloudy_night')])
    (DEST / 'non_rain_ambience_manifest.json').write_text(json.dumps(nonrain, ensure_ascii=False, indent=2) + '\n')
    suite = DEST / 'wake_suite_manifest.json'; old = json.loads(suite.read_text()); by_path = {r['path']: r for r in records}
    old['assets'] = [by_path.get(a['path'], a) for a in old['assets']]
    suite.write_text(json.dumps(old, ensure_ascii=False, indent=2) + '\n')
    # Thunder claps: the loudest moment of each recording, with a short lead-in and a long tail.
    for i, (name, secs) in enumerate([('thunder_1', 12), ('thunder_2', 14), ('thunder_3', 12), ('thunder_4', 11), ('thunder_5', 6)], 1):
        x = filt(decode(name), 'highpass', 25)
        env = np.convolve(x ** 2, np.ones(2205) / 2205, 'same'); peak = int(np.argmax(env))
        start = max(0, peak - int(.35 * SR)); seg = x[start:start + int(secs * SR)].copy()
        f = min(int(1.6 * SR), len(seg) // 3); seg[-f:] *= np.linspace(1, 0, f) ** 2; seg[:400] *= np.linspace(0, 1, 400)
        seg = seg / np.max(np.abs(seg)) * .89
        tmp = RAW / 'thunder.tmp.wav'
        with wave.open(str(tmp), 'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((seg * 32767).astype('<i2').tobytes())
        out = RAW / ('thunder_strike.ogg' if i == 1 else f'thunder_strike_{i}.ogg')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(tmp), '-c:a', 'libvorbis', '-q:a', '4', str(out)], check=True)
        tmp.unlink()


if __name__ == '__main__':
    main()
