#!/usr/bin/env python3
"""Synthesises res/raw/breeze_leaves.ogg (晴天背景音): wind bed plus leaf crackles, 60 s seamless loop.
Writes breeze.wav; then: ffmpeg -i breeze.wav -af volume=-3dB -c:a libvorbis -q:a 3 breeze_leaves.ogg"""
import numpy as np, scipy.signal as ss, wave
sr=32000; T=60; X=3
rng=np.random.default_rng(7)
n=(T+X+2)*sr
def smooth_env(rate_hz, lo, hi, seed):
    r=np.random.default_rng(seed)
    k=int(n/sr*rate_hz)+4
    pts=r.uniform(0,1,k)
    x=np.linspace(0,k-3.001,n)
    i=np.floor(x).astype(int); f=x-i
    # Catmull-Rom through random points: slow, organic swells
    p0,p1,p2,p3=pts[i],pts[i+1],pts[i+2],pts[i+3]
    v=.5*((2*p1)+(-p0+p2)*f+(2*p0-5*p1+4*p2-p3)*f*f+(-p0+3*p1-3*p2+p3)*f**3)
    v=np.clip(v,0,1)
    return lo+(hi-lo)*v
gust=smooth_env(.09,0,1,11)**1.6          # slow gusts, mostly calm
chans=[]
for ch in range(2):
    # 1) Wind bed: brown-ish noise, low passed, breathing with the gusts.
    w=ss.lfilter([1],[1,-.995],rng.standard_normal(n))
    w=ss.lfilter([1,-1],[1,-.995],w)          # remove drift
    b,a=ss.butter(2,[140/(sr/2),800/(sr/2)],'band'); bed=ss.lfilter(b,a,w)
    bed/=np.sqrt(np.mean(bed**2)); bed*=(.35+.65*gust)*smooth_env(.4,.85,1,20+ch)
    # 2) Leaves: many tiny crackles, denser and brighter in a gust.
    rate=250+1400*gust                       # grains per second
    hits=rng.random(n)<rate/sr
    amp=np.where(hits,rng.lognormal(0,.6,n),0)
    b,a=ss.butter(2,[1800/(sr/2),7500/(sr/2)],'band'); crack=ss.lfilter(b,a,amp)
    # a soft hiss under the crackles, shaped by the same density
    env=ss.lfilter([1],[1,-.996],amp); env/=env.mean()
    b,a=ss.butter(2,[1200/(sr/2),9000/(sr/2)],'band'); hiss=ss.lfilter(b,a,rng.standard_normal(n))*env
    leaves=crack/np.sqrt(np.mean(crack**2))*.55+hiss/np.sqrt(np.mean(hiss**2))*.45
    leaves*=(.25+.75*gust)
    b,a=ss.butter(1,10000/(sr/2),'low'); leaves=ss.lfilter(b,a,leaves)
    mix=bed*.4+leaves*.5
    chans.append(mix[2*sr:])
s=np.stack(chans,1)
L=T*sr; xf=X*sr
body=s[:L].copy(); tail=s[L:L+xf]; t=np.linspace(0,1,xf)[:,None]
body[:xf]=body[:xf]*np.sqrt(t)+tail*np.sqrt(1-t)
body*=10**(-45/20)/np.sqrt(np.mean(body**2))
pk=np.max(abs(body)); print('peak dB',20*np.log10(pk))
pcm=(np.clip(body,-1,1)*32767).astype('<i2')
with wave.open('breeze.wav','wb') as f: f.setnchannels(2); f.setsampwidth(2); f.setframerate(sr); f.writeframes(pcm.tobytes())
r=[np.sqrt(np.mean(body[i:i+sr,0]**2)) for i in range(0,L-sr,sr)]
print('1s rms range dB', 20*np.log10(min(r)/np.sqrt(np.mean(body**2))), 20*np.log10(max(r)/np.sqrt(np.mean(body**2))))
