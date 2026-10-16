#!/usr/bin/env python3
"""Download PUBLIC-DOMAIN field recordings and make crossfaded ambient loops.

Requires Python 3.10+, ffmpeg and ffprobe, internet access during build.
No white noise or synthesized rainfall is substituted when downloads fail.
"""
from pathlib import Path
import hashlib
import subprocess
import urllib.parse
import urllib.request
import time

HERE=Path(__file__).resolve().parent
PROJECT=HERE.parent
RAW=PROJECT/'app/src/main/res/raw'
TEMP=PROJECT/'build/weather_audio_sources'
RAW.mkdir(parents=True,exist_ok=True)
TEMP.mkdir(parents=True,exist_ok=True)

SOURCES={
  'light_rain': ('Rain_against_the_window.ogg','cori','https://commons.wikimedia.org/wiki/File:Rain_against_the_window.ogg',68),
  'heavy_rain': ('Rain_(1).ogg','ezwa','https://commons.wikimedia.org/wiki/File:Rain_(1).ogg',39),
  'thunder_strike': ('Rain_and_thunder.ogg','Caesar','https://commons.wikimedia.org/wiki/File:Rain_and_thunder.ogg',0),
}

def original_url(filename:str)->str:
    md5=hashlib.md5(filename.encode('utf-8')).hexdigest()
    return f'https://upload.wikimedia.org/wikipedia/commons/{md5[0]}/{md5[:2]}/{urllib.parse.quote(filename)}'

def download(filename:str)->Path:
    dst=TEMP/filename
    if dst.exists() and dst.stat().st_size>10000: return dst
    url=original_url(filename)
    for attempt in range(4):
        try:
            req=urllib.request.Request(url,headers={'User-Agent':'ThinnKhiWeather/1.0 (weather audio asset build; GitHub Actions)'})
            with urllib.request.urlopen(req,timeout=50) as stream, dst.open('wb') as file:
                while True:
                    block=stream.read(65536)
                    if not block:break
                    file.write(block)
            if dst.stat().st_size<10000:raise ValueError('Downloaded file unexpectedly small')
            print(f'Fetched: {filename} ({dst.stat().st_size} bytes)',flush=True)
            return dst
        except Exception as exc:
            dst.unlink(missing_ok=True)
            if attempt==3:raise RuntimeError(f'Recording download failed: {filename} from {url}: {exc}') from exc
            time.sleep(3*(attempt+1))
    raise AssertionError

def call(command:list[str])->None:
    subprocess.run(command,check=True)

def make_loop(src:Path,dst:Path,seconds:int,volume:float)->None:
    # Loop begins from t=2s; its end crossfades into exactly the beginning.
    # Each repeat is sample-level continuous, not just a simple MediaPlayer restart.
    filter_graph=(
        
        f'[0:a]atrim=start=2:end={seconds},asetpts=PTS-STARTPTS,aresample=32000,highpass=f=95,lowpass=f=9600[a];'
        f'[1:a]atrim=start=0:end=2,asetpts=PTS-STARTPTS,aresample=32000,highpass=f=95,lowpass=f=9600[b];'
        f'[a][b]acrossfade=d=2:c1=tri:c2=tri,volume={volume}[out]'
    )
    call(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(src),
          '-i',str(src),'-filter_complex',filter_graph,'-map','[out]','-vn','-c:a','libvorbis','-q:a','5',str(dst)])

def thunder_one_shot(src:Path,dst:Path)->None:
    # This is a genuine storm field recording. It is triggered after each visual flash.
    call(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(src),'-vn',
          '-af','highpass=f=60,lowpass=f=9000,volume=0.75,afade=t=in:st=0:d=0.12,afade=t=out:st=16.3:d=2.2',
          '-ar','32000','-c:a','libvorbis','-q:a','4',str(dst)])

for key,(filename,artist,link,end) in SOURCES.items():
    path=download(filename)
    output=RAW/(key+'.ogg')
    if end>0:make_loop(path,output,end,0.78 if key=='light_rain' else 0.87)
    else:thunder_one_shot(path,output)
    if output.stat().st_size<10000:raise RuntimeError(f'No audio produced: {output}')
    print(f'Created {output.name}: {output.stat().st_size} bytes',flush=True)
print('Rain source: real field recordings / public domain. See docs/WEATHER_AUDIO_SOURCES.md')
