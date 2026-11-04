#!/usr/bin/env python3
"""Reproducible 24-second foreground soundscapes from credited field recordings.

No generated bird/cricket oscillators, bells, melodies, or synthesized rainfall.
The three CC0 MP3 source previews are committed alongside SOURCES.json; existing
public-domain rain stems retain docs/WEATHER_AUDIO_SOURCES.md attribution.
Requires numpy, scipy, ffmpeg only when deliberately regenerating the assets.
"""
from pathlib import Path
import hashlib,json,subprocess,wave
import numpy as np
from scipy.signal import butter,sosfilt

PROJECT=Path(__file__).resolve().parents[1]
ASSETS=PROJECT/'app/src/main/assets';DEST=ASSETS/'next_weather'
SOURCES=Path(__file__).parent/'audio_sources';SR=44100;SECONDS=24;N=SR*SECONDS

def decode(path):
    data=subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-ar',str(SR),'-ac','1','-f','f32le','pipe:1'])
    return np.frombuffer(data,dtype='<f4').astype(np.float64)

def periodic(source,offset):
    fade=3*SR
    index=(np.arange(N+fade)+round(offset*SR))%len(source)
    chunk=source[index];out=chunk[fade:].copy()
    phase=np.linspace(0,np.pi/2,fade)
    out[-fade:]=chunk[N:]*np.cos(phase)+chunk[:fade]*np.sin(phase)
    return out

def filtered(x,low,high):
    sos=butter(3,[low,high],btype='bandpass',fs=SR,output='sos')
    x=sosfilt(sos,np.tile(x,3))[N:2*N];x-=x.mean()
    return x/(np.sqrt(np.mean(x*x))+1e-12)

def close_loop(x):
    # Smooth endpoint and slope correction, then exact PCM quantisation closure.
    k=128;u=np.linspace(0,1,k);weight=3*u*u-2*u*u*u
    target=2*x[0]-x[:k][::-1]
    x[-k:]=x[-k:]*(1-weight)+target*weight
    return x

def main():
    credits=json.loads((SOURCES/'SOURCES.json').read_text())
    for source in credits:
        assert hashlib.sha256((SOURCES/(source['id']+'.mp3')).read_bytes()).hexdigest()==source['sha256']
    stems={key:decode(SOURCES/(key+'.mp3')) for key in ('birds','leaves','night')}
    stems.update({key:decode(PROJECT/'app/src/main/res/raw'/name) for key,name in [('rain','light_rain.ogg'),('downpour','heavy_rain.ogg')]})
    # (target RMS dBFS, source, offset seconds, relative bed level, HP Hz, LP Hz).
    recipes={
      'sunny':(-27.5,[('leaves',2,.66,170,5200),('birds',0,.34,250,6200)]),
      'clear_night':(-31,[('leaves',9,.70,180,3500),('night',18,.30,350,4700)]),
      'cloudy_day':(-29,[('leaves',7,.88,150,4800),('birds',8,.12,300,5300)]),
      'cloudy_night':(-31.5,[('leaves',13,.82,180,3200),('night',73,.18,400,4200)]),
      'drizzle':(-30,[('rain',7,.86,170,4800),('leaves',3,.14,180,3800)]),
      'light_rain':(-28.5,[('rain',20,1,140,5600)]),
      'moderate_rain':(-27,[('rain',31,.7,125,6000),('downpour',3,.3,130,5200)]),
      'heavy_rain':(-25.5,[('downpour',8,1,110,5900)]),
      'thunderstorm':(-26,[('downpour',16,.92,110,5600),('leaves',5,.08,140,3000)]),
      'severe_thunderstorm':(-25,[('downpour',23,.84,110,5700),('leaves',11,.16,120,3400)]),
      'haze':(-30,[('leaves',4,1,180,3500)]),
      'fog':(-31,[('leaves',12,1,210,3100)]),
      'dense_fog':(-32,[('leaves',16,1,240,2700)]),
      'dust_storm':(-28,[('leaves',1,1,120,4600)]),
      'light_snow':(-31,[('leaves',6,1,200,2900)]),
      'moderate_snow':(-30,[('leaves',10,1,170,3200)]),
      'heavy_snow':(-29,[('leaves',17,1,140,3500)]),
    }
    records=[]
    for scene,(rms_db,parts) in recipes.items():
        y=sum(filtered(periodic(stems[key],offset),low,high)*gain for key,offset,gain,low,high in parts)
        # Restrained transient compression without clipping, followed by DC removal.
        rms=np.sqrt(np.mean(y*y));threshold=rms*3.3
        magnitude=np.abs(y);y=np.sign(y)*np.where(magnitude>threshold,threshold+threshold*.4*np.tanh((magnitude-threshold)/(threshold*.4)),magnitude)
        y-=y.mean();y*=10**(rms_db/20)/(np.sqrt(np.mean(y*y))+1e-12)
        y=close_loop(y);y*=min(1,.40/(np.max(np.abs(y))+1e-12))
        pcm=np.round(y*32767).astype('<i2');pcm[-1]=pcm[0];pcm[-2]=2*int(pcm[0])-int(pcm[1])
        path=DEST/'ambience'/f'{scene}.wav'
        with wave.open(str(path),'wb') as wav:
            wav.setparams((1,2,SR,0,'NONE','not compressed'));wav.writeframes(pcm.tobytes())
        records.append(dict(path=path.relative_to(ASSETS).as_posix(),scene=scene,category='ambience',
            authorship='Remixed CC0 / public-domain field recordings; source credits in docs/NATURE_AMBIENCE_REMASTER.md',
            sources=sorted({part[0] for part in parts}),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
            sample_rate=SR,channels=1,frames=N,seconds=SECONDS,
            rms_dbfs=round(float(20*np.log10(np.sqrt(np.mean(pcm.astype(float)**2))/32768)),3),
            peak_dbfs=round(float(20*np.log10(np.max(np.abs(pcm.astype(float)))/32768)),3),loop_jump_pcm=0))
    manifest=dict(schema=1,description='Natural field-recording soundscapes, 24 s crossfaded loops; no musical notes or generated animal calls.',
        human_listening_or_wake_efficacy_verified=False,source_recordings=credits,assets=records)
    (DEST/'nature_ambience_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    nonrain=dict(schema=2,description='Quiet CC0 leaves, birds and night field recordings.',human_listening_or_wake_efficacy_verified=False,
        assets=[r for r in records if r['scene'] in ('sunny','clear_night','cloudy_day','cloudy_night')])
    (DEST/'non_rain_ambience_manifest.json').write_text(json.dumps(nonrain,ensure_ascii=False,indent=2)+'\n')
    suite=DEST/'wake_suite_manifest.json';old=json.loads(suite.read_text());by_path={r['path']:r for r in records}
    old['assets']=[by_path.get(r['path'],r) for r in old['assets']]
    suite.write_text(json.dumps(old,ensure_ascii=False,indent=2)+'\n')
    for r in records:print(r['scene'],r['rms_dbfs'],'dBFS RMS',r['peak_dbfs'],'dBFS peak')

if __name__=='__main__':main()
