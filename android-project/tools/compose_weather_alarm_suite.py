#!/usr/bin/env python3
"""Original Weather Wake: 6 themes x 14 weather arrangements, 6 neutral, 14 ambience.
PCM16 / mono / 44100 Hz. Rain textures retain the project's attributed public-domain
recordings; other nature textures and all musical compositions are original synthesis.
Reproduction only: Python + numpy + scipy + ffmpeg. Not required for normal builds.
"""
from pathlib import Path
import hashlib,json,subprocess,wave,os,sys
import numpy as np
from scipy.signal import butter,sosfilt
R=Path(__file__).resolve().parents[1];A=R/'app/src/main/assets/next_weather';RAW=R/'app/src/main/res/raw';SR=44100
SCENES=['sunny','drizzle','light_rain','moderate_rain','heavy_rain','thunderstorm','severe_thunderstorm','haze','fog','dense_fog','dust_storm','light_snow','moderate_snow','heavy_snow']
THEMES={'dawn':(104,'piano',[76,79,81,79,76,74,72,74]),'rain':(102,'felt',[72,76,79,76,74,77,76,72]),'wood':(108,'marimba',[79,76,72,74,76,79,81,79]),'water':(106,'harp',[76,79,84,81,79,76,74,72]),'fire':(112,'pluck',[72,76,79,81,79,76,74,79]),'metal':(110,'bell',[79,84,81,79,76,74,76,72])}
def seed(s):return int.from_bytes(hashlib.sha256(s.encode()).digest()[:4],'little')
def add(x,y,start):np.add.at(x,(np.arange(len(y))+int(start*SR))%len(x),y)
def note(midi,duration,kind,bright=1):
 t=np.arange(int(duration*SR))/SR;f=440*2**((midi-69)/12);y=np.zeros_like(t)
 harmonics={'piano':[1,.43,.22,.12,.065,.035],'felt':[1,.25,.11,.045],'marimba':[1,.13,.08,.02],'harp':[1,.48,.19,.08,.03],'pluck':[1,.44,.22,.13,.06],'bell':[1,.36,.12,.055]}[kind]
 for h,a in enumerate(harmonics,1):
  ratio=h if kind!='bell' else [1,2.006,3.01,4.02][h-1]
  y+=a*bright**((h-1)*.22)*np.sin(2*np.pi*f*ratio*t)*np.exp(-t*(1.3+h*.45))
 return y*(1-np.exp(-t/(.013 if kind in ('piano','pluck') else .025)))*np.clip((duration-t)/.06,0,1)
def master(x,rms_db):
 sos=butter(2,[110,7600],btype='bandpass',fs=SR,output='sos')
 # Filter a repeated loop and retain the middle: the filter state is periodic too.
 x=sosfilt(sos,np.tile(x,3))[len(x):2*len(x)];x-=x.mean();x*=10**(rms_db/20)/(np.sqrt(np.mean(x*x))+1e-12)
 x=np.tanh(x*1.12)/1.12;x*=min(1,.79/(max(abs(x))+1e-12))
 # A very short equal-slope closure removes residual quantisation/rounding seams.
 k=48;b=np.linspace(0,1,k)**2;target=x[0]-np.arange(k-1,-1,-1)*(x[1]-x[0]);x[-k:]=x[-k:]*(1-b)+target*b
 return x
records=[]
def save(path,x,**extra):
 path.parent.mkdir(parents=True,exist_ok=True);pcm=np.round(np.clip(x,-.999,.999)*32767).astype('<i2');pcm[-1]=pcm[0];pcm[-2]=np.clip(2*int(pcm[0])-int(pcm[1]),-32768,32767)
 with wave.open(str(path),'wb') as w:w.setparams((1,2,SR,0,'NONE','not compressed'));w.writeframes(pcm.tobytes())
 records.append({'path':path.relative_to(R/'app/src/main/assets').as_posix(),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'sample_rate':SR,'channels':1,'frames':len(pcm),'seconds':round(len(pcm)/SR,4),'peak_dbfs':round(float(20*np.log10(max(abs(pcm.astype(float)))/32768+1e-12)),3),'rms_dbfs':round(float(20*np.log10(np.sqrt(np.mean((pcm.astype(float)/32768)**2))+1e-12)),3),'loop_jump_pcm':abs(int(pcm[-1])-int(pcm[0])),**extra})
def encode_fallback(theme,suffix,scene):
 target=RAW/f'thinn_alarm_{theme}{suffix}.ogg';temporary=target.with_name(target.stem+'.pending.ogg')
 subprocess.run(['ffmpeg','-v','error','-y','-i',str(A/f'music/{theme}/{scene}.wav'),'-c:a','libvorbis','-q:a','5',str(temporary)],check=True)
 info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration:stream=codec_name,sample_rate,channels','-of','json',str(temporary)]))
 stream=info['streams'][0]
 if float(info['format']['duration'])<=8 or stream['codec_name']!='vorbis' or int(stream['sample_rate'])!=SR or stream['channels']!=1:raise ValueError('Invalid alarm fallback '+str(target))
 os.replace(temporary,target)
if '--fallback-only' in sys.argv:
 for theme in THEMES:
  for suffix,scene in [('', 'neutral'),('_clear','sunny'),('_rain','moderate_rain')]:encode_fallback(theme,suffix,scene)
 print('Verified 18 Ogg/Vorbis fallback tones',flush=True)
 sys.exit(0)
def melody(theme,scene):
 bpm,kind,motif=THEMES[theme];i=SCENES.index(scene) if scene!='neutral' else -1
 bpm+=[0,-2,-1,0,1,1,2,-1,-2,-3,0,-2,-1,0][i] if i>=0 else 0
 beat=60/bpm;x=np.zeros(round(16*beat*SR));snow=i>=11;rain=1<=i<=6;bright=.74 if snow or scene in ('fog','dense_fog','haze') else .9 if rain else 1
 chords=[[48,55,62,64],[45,52,60,67],[41,48,57,64],[43,50,60,62]]
 for bar,chord in enumerate(chords):
  for j,m in enumerate(chord):add(x,note(m,2.6,'felt',.78)*.12,bar*4*beat+j*.035)
  add(x,note(chord[0],beat*1.8,'pluck',.7)*.15,bar*4*beat)
  for j in range(4):add(x,note(chord[(j+bar)%4]+12,beat*1.6,kind,bright)*.10,(bar*4+j+.5)*beat)
 for j,m in enumerate(motif*2):
  answer=12 if snow and j in (7,15) else -12 if scene in ('dense_fog','haze') and j in (3,11) else 0
  add(x,note(m+answer,beat*2.4,kind,bright)*(.28 if j%4==0 else .22),j*beat+(beat*.035 if j%2 else 0))
 rng=np.random.default_rng(seed(theme+scene));t=np.arange(int(.12*SR))/SR
 for b in range(16):add(x,(np.sin(2*np.pi*(190-80*t)*t)*np.exp(-t*45)+rng.normal(0,.05,len(t))*np.exp(-t*80))*.012,b*beat)
 for delay,gain in ((.073,.11),(.137,.07),(.263,.045),(.421,.026)):x+=np.roll(x,int(delay*SR))*gain
 return master(x,-20),bpm
for theme in THEMES:
 for scene in SCENES+['neutral']:
  music,bpm=melody(theme,scene);save(A/f'music/{theme}/{scene}.wav',music,theme=theme,scene=scene,bpm=bpm,category='alarm',authorship='original composition')
 for suffix,scene in [('', 'neutral'),('_clear','sunny'),('_rain','moderate_rain')]:encode_fallback(theme,suffix,scene)
 print('Composed',theme,flush=True)
for i,scene in enumerate(SCENES):
 n=SR*12;t=np.arange(n)/SR;rng=np.random.default_rng(seed(scene));f=np.fft.rfftfreq(n,1/SR);power=1/np.maximum(f,65)**.75;power[(f<65)|(f>8500)]=0
 noise=np.fft.irfft(power*np.exp(2j*np.pi*rng.random(len(f))),n);noise/=np.std(noise)
 if 1<=i<=6:
  name='light_rain.ogg' if i<4 else 'heavy_rain.ogg'
  raw=subprocess.check_output(['ffmpeg','-v','error','-stream_loop','-1','-i',str(RAW/name),'-t','12','-ar',str(SR),'-ac','1','-f','f32le','pipe:1']);y=np.frombuffer(raw,dtype='<f4').astype(float)[:n];y=y/(np.std(y)+1e-9)*(.82+.12*np.sin(2*np.pi*t/12))+.06*noise
  source='project attributed public-domain rain recording; docs/WEATHER_AUDIO_SOURCES.md'
 else:
  y=noise*(.48+.13*np.cos(2*np.pi*t/12));source='original procedural weather soundscape'
  if scene=='sunny':
   for start in (1.3,4.8,8.1):q=np.arange(int(.34*SR))/SR;add(y,np.sin(2*np.pi*(1500*q+420*q*q))*np.sin(np.pi*q/.34)**2*.11,start)
  elif i>=11:
   y=sosfilt(butter(2,3400,fs=SR,output='sos'),y)*.72
   for start in (2.,6.,9.5):add(y,note(84,1.8,'bell',.65)*.018,start)
 save(A/f'ambience/{scene}.wav',master(y,-26),scene=scene,category='ambience',authorship=source)
manifest={'schema':2,'themes':list(THEMES),'scene_count':14,'alarm_arrangements':84,'neutral_arrangements':6,'ambience_loops':14,'listening_quality_or_wake_efficacy_verified':False,'assets':records}
(A/'wake_suite_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print('Generated',len(records),'PCM loops',flush=True)
