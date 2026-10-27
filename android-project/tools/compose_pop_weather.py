#!/usr/bin/env python3
"""Six original 16-bar pop miniatures with weather-specific arrangements.

No recordings, third-party samples, or melodies are used. Reproduction requires
numpy/scipy/ffmpeg; the Android build only consumes the committed WAV/Ogg files.
"""
from pathlib import Path
import argparse, hashlib, json, subprocess, wave
import numpy as np
from scipy.signal import butter, sosfilt

ROOT=Path(__file__).resolve().parents[1]
SR=32000
SCALE=[0,2,4,5,7,9,11]
PROFILES={
 'citypop':dict(label='城市晨光',genre='City Pop',bpm=104,key=60,lead='brass',chords=[3,4,2,5,1,4,0,0],
    hooks=[[9,7,16,14],[14,11,9,7],[11,7,16,14],[12,11,9,16],[17,16,14,9],[11,9,7,5],[16,14,12,7],[16,19,14,12]],rhythm=[0,.75,1.5,2.75]),
 'tropical':dict(label='海風假期',genre='Tropical Pop',bpm=110,key=62,lead='marimba',chords=[0,4,5,3,0,4,5,3],
    hooks=[[16,19,16,14],[11,14,19,14],[12,16,21,19],[9,12,17,16],[16,19,24,19],[14,19,23,19],[16,21,19,16],[17,16,12,9]],rhythm=[.25,1,1.75,3]),
 'indiepop':dict(label='晴空出走',genre='Indie Pop',bpm=116,key=57,lead='guitar',chords=[3,0,4,5,3,0,4,0],
    hooks=[[9,12,14,16],[16,14,12,7],[11,14,19,14],[12,16,14,9],[9,12,17,16],[19,16,14,12],[14,11,9,7],[16,14,12,12]],rhythm=[0,.5,1.5,3]),
 'rnb':dict(label='雨後慢拍',genre='Neo Soul / R&B',bpm=86,key=58,lead='rhodes',chords=[0,2,5,3,1,4,0,0],
    hooks=[[16,14,11,7],[11,14,16,19],[12,16,14,9],[9,12,16,14],[17,16,14,9],[14,11,9,7],[16,14,12,7],[19,16,14,12]],rhythm=[.5,1.25,2.5,3.25]),
 'futurepop':dict(label='極光派對',genre='Future Pop',bpm=120,key=60,lead='bell',chords=[0,5,3,4,0,5,3,4],
    hooks=[[19,16,14,12],[21,19,16,12],[17,16,12,9],[19,14,11,7],[24,19,16,14],[21,19,16,12],[17,21,19,17],[19,14,11,12]],rhythm=[0,.75,2,2.75]),
 'daybreak':dict(label='日出情歌',genre='Piano Pop',bpm=92,key=60,lead='piano',chords=[0,4,5,3,1,4,0,0],
    hooks=[[16,19,14,12],[14,19,11,7],[16,21,19,12],[17,16,12,9],[17,16,14,9],[14,11,9,7],[16,19,14,12],[19,16,14,12]],rhythm=[0,1,2.5,3.25]),
}
VARIANTS=('neutral','clear','rain','snow')

def seed(s):return int.from_bytes(hashlib.sha256(s.encode()).digest()[:4],'little')
def degree(n):return SCALE[n%7]+12*(n//7)

def tone(midi,seconds,kind,velocity=1):
 t=np.arange(round(SR*seconds))/SR;freq=440*2**((midi-69)/12);ph=2*np.pi*freq*t
 rng=np.random.default_rng(seed(f'{midi}:{seconds:.6f}:{kind}'))
 if kind in ('guitar','piano','marimba','bell'):
  if kind=='guitar':
   y=sum(np.sin(ph*h+.12*h)*np.exp(-t*(1.6+h*.56))/h**1.45 for h in range(1,11))
   y+=rng.normal(0,.028,len(t))*np.exp(-t*75);attack=.005
  elif kind=='piano':
   y=sum(np.sin(ph*h*np.sqrt(1+.00006*h*h))*np.exp(-t*(.75+h*.27))/h**1.7 for h in range(1,12))
   y+=.15*np.sin(ph*1.002)*np.exp(-t*1.6);attack=.009
  elif kind=='marimba':
   y=np.sin(ph)*np.exp(-t*5)+.24*np.sin(ph*4.03)*np.exp(-t*14)+.05*np.sin(ph*9.7)*np.exp(-t*25);attack=.004
  else:
   y=np.sin(ph)*np.exp(-t*2.8)+.21*np.sin(ph*2.01)*np.exp(-t*5)+.07*np.sin(ph*4.05)*np.exp(-t*10);attack=.006
 elif kind=='rhodes':
  y=np.sin(ph+1.6*np.exp(-t*4)*np.sin(2*ph))*.85*np.exp(-t*1.5)+.1*np.sin(ph*3)*np.exp(-t*5);attack=.012
 elif kind=='bass':
  y=(np.sin(ph)+.26*np.sin(ph*2)+.12*np.sin(ph*3))*np.exp(-t*2.8);attack=.006
 elif kind=='brass':
  detune=.003*np.sin(2*np.pi*4.5*t)
  y=sum(np.sin(ph*h+detune*h*2*np.pi)/h**1.5 for h in range(1,min(12,int(9500/freq))))
  y*=np.exp(-t*2.1);attack=.028
 else:
  y=(np.sin(ph)+.25*np.sin(ph*1.003)+.25*np.sin(ph*.997)+.08*np.sin(ph*2))*np.exp(-t*.45);attack=.11
 return y*(1-np.exp(-t/attack))*np.clip((seconds-t)/.09,0,1)*velocity

def add(out,part,seconds):
 start=round(seconds*SR)%len(out);first=min(len(part),len(out)-start)
 out[start:start+first]+=part[:first]
 if first<len(part):out[:len(part)-first]+=part[first:]

def drum(kind,rng):
 seconds={'kick':.26,'snare':.22,'hat':.10,'open':.19,'shaker':.10}[kind]
 t=np.arange(round(seconds*SR))/SR
 if kind=='kick':
  return np.sin(2*np.pi*(52*t+2.0*(1-np.exp(-t*34))))*np.exp(-t*18)*(1-np.exp(-t*700))
 noise=rng.normal(0,1,len(t))
 if kind=='snare':return (sosfilt(butter(2,1800,btype='highpass',fs=SR,output='sos'),noise)*.65+.16*np.sin(2*np.pi*180*t))*np.exp(-t*21)*(1-np.exp(-t*1500))
 high=sosfilt(butter(2,6500,btype='highpass',fs=SR,output='sos'),noise)
 return high*np.exp(-t*(40 if kind=='hat' else 24 if kind=='shaker' else 17))*(1-np.exp(-t*1800))

def compose(name,variant):
 p=PROFILES[name];beat=60/p['bpm'];out=np.zeros(round(64*beat*SR));rng=np.random.default_rng(seed(name+':'+variant));events=[]
 weather_level={'neutral':1,'clear':1.08,'rain':.82,'snow':.55}[variant]
 for bar in range(16):
  chord_degree=p['chords'][bar%8];root=p['key']-12+degree(chord_degree)
  harmony=[p['key']-12+degree(chord_degree+j) for j in (0,2,4,6)]
  if name in ('citypop','rnb'):harmony.append(p['key']+degree(chord_degree+1))
  # A / lift / full hook / response, rather than one repeating arpeggio.
  hook=bar>=8;intensity=.78 if bar<4 else .90 if bar<8 else 1.05
  chord_voice='guitar' if name=='indiepop' else 'piano' if name=='daybreak' else 'rhodes'
  hits=[0,1.75,3] if name=='citypop' else [.25,1.75,2.75] if name=='tropical' else [0,2.5] if name=='rnb' else [0,2]
  if name=='indiepop':hits=[0,.5,1.5,2,2.5,3.5]
  for hit in hits:
   for j,note in enumerate(harmony):
    add(out,tone(note+(12 if variant=='snow' and j==len(harmony)-1 else 0),beat*(1.8 if name=='rnb' else 1.1),chord_voice,.041*intensity),
        (bar*4+hit+j*(.018 if chord_voice=='guitar' else .004))*beat)
  if name in ('futurepop','daybreak'):
   for note in harmony:add(out,tone(note,beat*4.4,'pad',.018 if hook else .011),bar*4*beat)
  bass_hits=[0,1.5,2.75] if name in ('citypop','rnb') else [0,1.75,2.5,3.5]
  for i,hit in enumerate(bass_hits):
   bass=root-12+(7 if i==2 else 12 if i==3 else 0)
   add(out,tone(bass,beat*.9,'bass',.12*intensity),(bar*4+hit)*beat)
  melody=p['hooks'][bar%8]
  for i,(position,pitch) in enumerate(zip(p['rhythm'],melody)):
   if bar<4 and i==1:continue
   if variant=='snow' and bar<8 and i==3:continue
   duration=(1.0 if i==3 else .62 if name in ('indiepop','tropical') else .9)*beat
   note=p['key']+pitch
   if hook and bar%4==3 and i==3:duration=1.3*beat
   voice='bell' if variant=='snow' and name in ('tropical','futurepop') else p['lead']
   velocity=(.18 if i in (0,2) else .145)*intensity
   at=(bar*4+position)*beat
   add(out,tone(note,duration+.16,voice,velocity),at)
   events.append([bar,round(position,2),note,round(duration,4),voice])
   if hook and name in ('citypop','futurepop'):
    add(out,tone(note-12,duration+.16,'piano',velocity*.21),at+.009)
  if variant=='clear' and bar%2==1:
   add(out,tone(p['key']+24+degree(chord_degree+2)%12,beat*.65,'bell',.033),(bar*4+3.5)*beat)
  if variant=='rain':
   for hit in (1.25,3.25):add(out,tone(p['key']+degree(chord_degree+4),beat*.7,'marimba',.026),(bar*4+hit)*beat)
  drum_level=weather_level*(.65 if bar<4 else .82 if bar<8 else 1)
  if name=='daybreak':drum_level*=.42
  kick_hits=[0,2.5] if name=='rnb' else [0,2] if name=='daybreak' else [0,1.75,2.5] if name=='citypop' else [0,1,2,3]
  for hit in kick_hits:add(out,drum('kick',rng)*.16*drum_level,(bar*4+hit)*beat)
  for hit in ([2] if name=='rnb' else [1,3]):add(out,drum('snare',rng)*.055*drum_level,(bar*4+hit)*beat)
  for i in range(8):
   swing=.09 if name=='rnb' and i%2 else .035 if name=='citypop' and i%2 else 0
   add(out,drum('shaker' if name=='tropical' else 'hat',rng)*(.018 if i%2 else .012)*drum_level,(bar*4+i*.5+swing)*beat)
  if bar in (7,15):
   for i in range(3):add(out,drum('snare',rng)*(.014+i*.005)*drum_level,(bar*4+3.25+i*.25)*beat)
  if hook and name in ('tropical','futurepop'):
   for i in range(4):add(out,tone(harmony[i]+12,beat*.38,'marimba' if name=='tropical' else 'bell',.035),(bar*4+i+.5)*beat)
 dry=out.copy()
 for delay,gain in ((.115,.11),(.243,.08),(.379,.055),(.511,.035)):
  out+=np.roll(dry,round(delay*SR))*gain
 cutoff={'neutral':8900,'clear':9600,'rain':7800,'snow':6600}[variant]
 sos=butter(2,cutoff,fs=SR,output='sos')
 out=sosfilt(sos,np.tile(out,3))[len(out):2*len(out)]
 out-=out.mean();out=np.tanh(out*1.45)
 out*=min(.69/(np.max(abs(out))+1e-12),.105/(np.sqrt(np.mean(out*out))+1e-12))
 pcm=np.round(out*32767).astype('<i2');pcm[-1]=pcm[0];pcm[-2]=np.clip(2*int(pcm[0])-int(pcm[1]),-32768,32767)
 return pcm,events

def generate(root):
 assets=root/'app/src/main/assets';raw=root/'app/src/main/res/raw';raw.mkdir(parents=True,exist_ok=True);records=[]
 for name,p in PROFILES.items():
  for variant in VARIANTS:
   pcm,events=compose(name,variant);path=assets/f'next_weather/music/{name}/{variant}.wav';path.parent.mkdir(parents=True,exist_ok=True)
   with wave.open(str(path),'wb') as w:w.setparams((1,2,SR,0,'NONE','not compressed'));w.writeframes(pcm.tobytes())
   suffix='' if variant=='neutral' else '_'+variant;ogg=raw/f'thinn_alarm_{name}{suffix}.ogg'
   subprocess.run(['ffmpeg','-v','error','-y','-i',str(path),'-ar','44100','-c:a','libvorbis','-q:a','5',str(ogg)],check=True)
   y=pcm.astype(float)/32768
   records.append(dict(theme=name,variant=variant,path=str(path.relative_to(assets)),fallback=str(ogg.relative_to(root)),
    sha256=hashlib.sha256(path.read_bytes()).hexdigest(),fallback_sha256=hashlib.sha256(ogg.read_bytes()).hexdigest(),sample_rate=SR,frames=len(pcm),seconds=len(pcm)/SR,
    peak_dbfs=float(20*np.log10(np.max(abs(y))+1e-12)),rms_dbfs=float(20*np.log10(np.sqrt(np.mean(y*y))+1e-12)),
    loop_jump_pcm=abs(int(pcm[-1])-int(pcm[0])),score_sha256=hashlib.sha256(json.dumps(events).encode()).hexdigest()))
  print('COMPOSED '+name+' '+p['genre'],flush=True)
 manifest=dict(schema=1,added_themes=list(PROFILES),total_selectable_themes=20,variants=list(VARIANTS),bars=16,
  structure=['opening motif: bars 1–4','lift: bars 5–8','full hook: bars 9–12','answer and fill: bars 13–16'],
  profiles=PROFILES,authorship='Original scores and synthesis; no third-party music, melodies or samples',
  human_listening_or_wake_efficacy_verified=False,assets=records)
 (assets/'next_weather/pop_weather_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
 print('COMPLETE 24 original pop loops and 24 Ogg fallbacks',flush=True)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--output-root',type=Path,default=ROOT);args=parser.parse_args();generate(args.output_root)
