#!/usr/bin/env python3
"""Original seamless breeze, leaf, bird and cricket synthesis; no third party samples.
Reproduction uses numpy. Android consumes committed PCM16 WAV assets.
"""
from pathlib import Path
import hashlib,json,wave
import numpy as np
ROOT=Path(__file__).resolve().parents[1]/'app/src/main/assets/next_weather'
SR=44100;N=SR*12

def compose(name):
 rng=np.random.default_rng(int.from_bytes(hashlib.sha256(name.encode()).digest()[:4],'little'))
 t=np.arange(N)/SR;f=np.fft.rfftfreq(N,1/SR)
 shape=(f/(f+210))**2/(1+(f/2300)**3)/(np.sqrt(f+80))
 wind=np.fft.irfft(np.fft.rfft(rng.normal(size=N))*shape,n=N)
 wind/=np.sqrt(np.mean(wind**2))
 night='night' in name
 y=wind*(.050 if night else .065)*(1+.28*np.cos(2*np.pi*t/12)+.17*np.sin(2*np.pi*t/4))
 def add(part,start):
  nonlocal y
  start=round(start*SR)%N;first=min(N-start,len(part));y[start:start+first]+=part[:first]
  if first<len(part):y[:len(part)-first]+=part[first:]
 if name=='sunny':
  for at in (1.2,1.57,4.4,7.6,8.0,10.2):
   x=np.arange(round(.31*SR))/SR;freq=2600+950*np.sin(np.pi*x/.31)
   chirp=np.sin(2*np.pi*np.cumsum(freq)/SR)*np.sin(np.pi*x/.31)**2*.045
   add(chirp,at)
 elif night:
  for at in np.arange(.35,12,1.45 if name=='clear_night' else 2.25):
   x=np.arange(round(.65*SR))/SR
   crickets=(np.sin(2*np.pi*3750*x)+.22*np.sin(2*np.pi*4500*x))
   crickets*=np.sin(np.pi*x/.65)**2*(.5+.5*np.sin(2*np.pi*37*x))**3*.024
   add(crickets,at)
 else:
  # Wind passing through leaves, with changing midrange rather than sub-bass only.
  leaves=np.fft.irfft(np.fft.rfft(rng.normal(size=N))*(f/(f+700))**3/(1+(f/4800)**4),n=N)
  leaves/=np.sqrt(np.mean(leaves**2));y+=leaves*.020*(.6+.4*np.cos(2*np.pi*t/3))
 y-=y.mean();y*=.079/np.sqrt(np.mean(y*y));y=np.tanh(y/.78)*.78
 # C1 closed loop join. The texture is periodic already; this corrects quantisation.
 edge=512;target=(y[0]+y[-1])/2
 y[:edge]+=(target-y[0])*(1-np.arange(edge)/(edge-1))**2
 y[-edge:]+=(target-y[-1])*(np.arange(edge)/(edge-1))**2
 pcm=np.round(np.clip(y,-.7,.7)*32767).astype('<i2');pcm[-1]=pcm[0];pcm[-2]=int(pcm[-1])-(int(pcm[1])-int(pcm[0]))
 return pcm

def main():
 assets=[]
 for name in ('sunny','clear_night','cloudy_day','cloudy_night'):
  pcm=compose(name);path=ROOT/'ambience'/f'{name}.wav';path.parent.mkdir(parents=True,exist_ok=True)
  with wave.open(str(path),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR);w.writeframes(pcm.tobytes())
  a=dict(path='next_weather/ambience/'+path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),sample_rate=SR,channels=1,frames=N,seconds=12,peak_dbfs=round(20*np.log10(np.max(np.abs(pcm.astype(float)))/32768),3),rms_dbfs=round(20*np.log10(np.sqrt(np.mean(pcm.astype(float)**2))/32768),3),loop_jump_pcm=0,scene=name,category='ambience',authorship='original procedural nature sound synthesis')
  assets.append(a)
 manifest=dict(schema=1,description='Original breeze, leaf, birds and crickets for non-rain weather.',human_listening_or_wake_efficacy_verified=False,assets=assets)
 (ROOT/'non_rain_ambience_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
 suite=ROOT/'wake_suite_manifest.json';old=json.loads(suite.read_text())
 for i,a in enumerate(old['assets']):
  if a['path']==assets[0]['path']:old['assets'][i]=assets[0]
 suite.write_text(json.dumps(old,ensure_ascii=False,indent=2)+'\n')
 print('Generated',len(assets),'original non-rain ambience loops')
if __name__=='__main__':main()
