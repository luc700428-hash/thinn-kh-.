#!/usr/bin/env python3
"""Eight original instrumental palettes, four scores each; existing weather ambience is reused.

PCM16 mono / 32000 Hz, plus Ogg fallback. No third-party music or instrument samples.
Python/numpy/scipy/ffmpeg are required only to reproduce the bundled assets.
"""
from pathlib import Path
import hashlib,json,subprocess,wave
import numpy as np
from scipy.signal import butter,sosfilt

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
RAW=ROOT/'app/src/main/res/raw'
SR=32000
PROFILES={
 'guitar':dict(label='晨光吉他',voice='guitar',bpm=94,meter=4,root=60,chords=[0,7,9,5,0,4,5,7],
   motif=[(0,4,.8),(.75,7,.8),(1.5,12,1.2),(3,11,.6),(3.75,7,1.0),(5,9,.8),(6,7,.7),(7,4,.8)]),
 'flute':dict(label='竹林笛語',voice='flute',bpm=88,meter=3,root=62,chords=[0,5,7,0,9,5,7,0],
   motif=[(0,7,1.2),(1.5,9,.7),(2.5,12,1.1),(4,14,.6),(5,12,.8)]),
 'rhodes':dict(label='雨窗爵士',voice='rhodes',bpm=76,meter=4,root=58,chords=[0,5,2,7,0,9,2,7],
   motif=[(.5,7,1.3),(2,10,.6),(2.75,14,1.2),(4.5,12,1.0),(6,9,.5),(6.75,7,.9)]),
 'kalimba':dict(label='雨滴拇指琴',voice='kalimba',bpm=102,meter=6,root=60,chords=[0,5,9,7],
   motif=[(0,12,.7),(.75,7,.6),(1.5,4,.7),(2.75,9,.6),(3.5,7,.6),(4.25,12,.7),(5.25,16,.6)]),
 'strings':dict(label='山嵐弦樂',voice='strings',bpm=82,meter=3,root=57,chords=[0,5,9,7,0,5,2,7],
   motif=[(0,7,1.6),(2,12,1.1),(3.5,14,.9),(5,11,.8)]),
 'musicbox':dict(label='星露八音盒',voice='musicbox',bpm=108,meter=4,root=60,chords=[0,9,5,7,4,9,5,0],
   motif=[(0,12,.9),(1,16,.7),(2,19,1.1),(3.5,16,.7),(4.5,14,.7),(5.5,11,.7),(6.5,7,1.0)]),
 'synth':dict(label='極光合成',voice='synth',bpm=84,meter=4,root=62,chords=[0,5,9,7,0,2,5,7],
   motif=[(0,7,1.7),(2,12,1.5),(4,14,1.3),(5.75,9,1.0),(7,7,.8)]),
 'groove':dict(label='晴日律動',voice='groove',bpm=118,meter=4,root=60,chords=[0,7,9,5,0,5,9,7],
   motif=[(0,7,.45),(.75,12,.45),(1.5,7,.45),(2.5,16,.7),(3.5,14,.45),(4.25,12,.45),(5,9,.7),(6.5,7,.45),(7.25,4,.6)]),
}
VARIANTS=('neutral','clear','rain','snow')

def seed(text):return int.from_bytes(hashlib.sha256(text.encode()).digest()[:4],'little')

def voice(midi,seconds,kind,velocity=1):
 t=np.arange(round(seconds*SR))/SR
 f=440*2**((midi-69)/12)
 phase=2*np.pi*f*t
 rng=np.random.default_rng(seed(f'{midi}:{seconds}:{kind}'))
 if kind=='guitar':
  y=sum(np.sin(phase*h+.12*h)*np.exp(-t*(1.35+h*.50))/h**1.5 for h in range(1,8))
  y+=rng.normal(0,.04,len(t))*np.exp(-t*70)
  attack=.004
 elif kind=='flute':
  p=phase+.045*np.sin(2*np.pi*5.1*t)
  y=(np.sin(p)+.17*np.sin(2*p)+.045*np.sin(3*p))*np.exp(-t*.6)
  y+=sosfilt(butter(2,6500,fs=SR,output='sos'),rng.normal(0,.023,len(t)))
  attack=.055
 elif kind=='rhodes':
  y=np.sin(phase+1.9*np.exp(-t*3.5)*np.sin(phase*2))*.84*np.exp(-t*1.8)
  y+=.10*np.sin(phase*3)*np.exp(-t*4)
  attack=.009
 elif kind=='kalimba':
  y=sum(a*np.sin(phase*r)*np.exp(-t*d) for r,a,d in [(1,1,3),(2.76,.35,6),(5.4,.12,12),(8.93,.03,20)])
  attack=.003
 elif kind=='strings':
  p=phase+.065*np.sin(2*np.pi*4.8*t)
  y=sum(np.sin(p*h)/h**1.6 for h in range(1,9))*np.exp(-t*.65)
  attack=.16
 elif kind=='musicbox':
  y=sum(a*np.sin(phase*r)*np.exp(-t*d) for r,a,d in [(1,1,2),(2.01,.30,3.5),(3.98,.10,6),(5.99,.025,12)])
  attack=.006
 elif kind=='synth':
  y=(.50*np.sin(phase)+.24*np.sin(phase*1.003)+.24*np.sin(phase*.997)+.12*np.sin(phase*2))*np.exp(-t*.7)
  attack=.14
 else:
  y=(np.sin(phase)+.28*np.sin(2*phase)+.15*np.sin(3*phase)+.06*np.sin(5*phase))*np.exp(-t*4.5)
  attack=.006
 return y*(1-np.exp(-t/attack))*np.clip((seconds-t)/.10,0,1)*velocity

def add(target,part,start):
 indexes=(np.arange(len(part))+round(start*SR))%len(target)
 np.add.at(target,indexes,part)

def percussion(seconds,rng,kind):
 t=np.arange(round(seconds*SR))/SR
 if kind=='kick':return np.sin(2*np.pi*(92*t+2.4*(1-np.exp(-t*35))))*np.exp(-t*25)
 noise=rng.normal(0,1,len(t))
 return sosfilt(butter(2,2200,btype='highpass',fs=SR,output='sos'),noise)*np.exp(-t*(48 if kind=='hat' else 19))

def score(name,variant):
 p=PROFILES[name];beat=60/p['bpm'];meter=p['meter'];bars=len(p['chords']);beats=meter*bars
 out=np.zeros(round(beats*beat*SR));rng=np.random.default_rng(seed(name+variant))
 chord_voice='rhodes' if name=='rhodes' else 'synth' if name in ('strings','synth') else 'guitar'
 for bar,root_offset in enumerate(p['chords']):
  root=p['root']-12+root_offset
  chord=[root,root+7,root+12,root+(15 if root_offset in (2,9) else 16)]
  if name=='rhodes':chord=[root,root+10,root+14,root+19]
  for j,note in enumerate(chord):
   position=bar*meter+(j*.09 if name=='guitar' else 0)
   add(out,voice(note,beat*meter*1.25,chord_voice,.040),position*beat)
  # Each family has its own accompaniment and pulse rather than a shared tick.
  if name in ('guitar','kalimba','musicbox'):
   for j in range(meter):add(out,voice(chord[(j+bar)%4]+12,beat*.95,p['voice'],.065),(bar*meter+j+.5)*beat)
  if name in ('groove','rhodes'):
   for j in range(meter):
    if name=='groove' or j%2==0:add(out,percussion(.18,rng,'kick')*.045,(bar*meter+j)*beat)
    add(out,percussion(.15,rng,'brush')*(.014 if name=='rhodes' else .022),(bar*meter+j+.58)*beat)
  if name=='groove':
   for j in (0,1.5,2.5):add(out,voice(root-12,beat*.65,'groove',.12),(bar*meter+j)*beat)
 phrase_beats=6 if meter in (3,6) else 8
 phrases=beats//phrase_beats
 for phrase in range(phrases):
  for i,(position,pitch,duration) in enumerate(p['motif']):
   if variant=='snow' and i%5==4:continue
   answer=(5 if phrase==2 and i%3==1 else 0)
   note=p['root']+pitch+answer
   if variant=='snow' and name not in ('flute','musicbox') and i==len(p['motif'])-1:note+=12
   if phrase==phrases-1 and i==len(p['motif'])-1:note=p['root']+12
   add(out,voice(note,beat*(duration+.35),p['voice'],.23 if i%3==0 else .19),(phrase*phrase_beats+position)*beat)
  if variant=='clear':add(out,voice(p['root']+24,beat*1.6,'musicbox',.045),(phrase*phrase_beats+phrase_beats-.4)*beat)
  if variant=='rain':
   for offset in (1.25,3.25,4.75):add(out,voice(p['root']+19,beat*.7,'kalimba',.034),(phrase*phrase_beats+offset)*beat)
  if variant=='snow':add(out,voice(p['root']+19,beat*2.2,'musicbox',.035),(phrase*phrase_beats+2.5)*beat)
 dry=out.copy()
 for delay,gain in ((.047,.10),(.119,.075),(.211,.045),(.383,.03)):
  out+=np.roll(dry,round(delay*SR))*gain*(1.3 if variant=='snow' else 1)
 high=5600 if variant=='rain' else 6800 if variant=='snow' else 10000
 sos=butter(2,[90,high],btype='bandpass',fs=SR,output='sos')
 out=sosfilt(sos,np.tile(out,3))[len(out):len(out)*2];out-=out.mean()
 out*=10**(-21/20)/(np.sqrt(np.mean(out*out))+1e-12)
 out=np.tanh(out*1.08)/1.08
 out*=min(1,.70/(np.max(np.abs(out))+1e-12))
 k=64;blend=np.linspace(0,1,k)**2
 target=out[0]-np.arange(k-1,-1,-1)*(out[1]-out[0])
 out[-k:]=out[-k:]*(1-blend)+target*blend
 pcm=np.round(out*32767).astype('<i2')
 pcm[-1]=pcm[0];pcm[-2]=np.clip(2*int(pcm[0])-int(pcm[1]),-32768,32767)
 return pcm

def generate():
 records=[]
 for name,p in PROFILES.items():
  for variant in VARIANTS:
   pcm=score(name,variant)
   wav=ASSETS/f'next_weather/music/{name}/{variant}.wav';wav.parent.mkdir(parents=True,exist_ok=True)
   with wave.open(str(wav),'wb') as w:w.setparams((1,2,SR,0,'NONE','not compressed'));w.writeframes(pcm.tobytes())
   suffix='' if variant=='neutral' else '_'+variant
   ogg=RAW/f'thinn_alarm_{name}{suffix}.ogg'
   subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-ar','44100','-c:a','libvorbis','-q:a','4',str(ogg)],check=True)
   samples=pcm.astype(float)/32768
   records.append(dict(theme=name,variant=variant,path=str(wav.relative_to(ASSETS)),fallback=str(ogg.relative_to(ROOT)),
     sha256=hashlib.sha256(wav.read_bytes()).hexdigest(),fallback_sha256=hashlib.sha256(ogg.read_bytes()).hexdigest(),
     sample_rate=SR,frames=len(pcm),seconds=len(pcm)/SR,peak_dbfs=float(20*np.log10(np.max(abs(samples))+1e-12)),
     rms_dbfs=float(20*np.log10(np.sqrt(np.mean(samples*samples))+1e-12)),loop_jump_pcm=abs(int(pcm[-1])-int(pcm[0]))))
  print('Composed:',name,p['label'],flush=True)
 manifest=dict(schema=1,added_themes=list(PROFILES),total_selectable_themes=14,variants=list(VARIANTS),
   ambience_scene_count=14,profiles={k:{x:v for x,v in p.items() if x!='motif'} for k,p in PROFILES.items()},
   authorship='Original procedural compositions and instrument-inspired synthesis; no sampled music',
   human_listening_or_wake_efficacy_verified=False,assets=records)
 (ASSETS/'next_weather/wake_palette_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
 print('Generated',len(records),'PCM loops and',len(records),'Ogg fallback recordings',flush=True)

if __name__=='__main__':generate()
