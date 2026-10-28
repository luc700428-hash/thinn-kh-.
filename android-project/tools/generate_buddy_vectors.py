#!/usr/bin/env python3
"""Original scalable widget art. Small animated layer viewports avoid full-card bitmap caches."""
from pathlib import Path
from math import sin,cos,pi
import random
ROOT=Path(__file__).resolve().parents[1];DRAW=ROOT/'app/src/main/res/drawable'
KINDS=['sun','moon','cloud','rain','snow','storm','mist','unknown'];FRAMES=24
def path(d,fill='#00000000',stroke=None,width=1):
    a='' if stroke is None else f' android:strokeColor="{stroke}" android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round"'
    return f'<path android:pathData="{d}" android:fillColor="{fill}"{a}/>'
def oval(x,y,rx,ry,color):return path(f'M{x-rx:.3f},{y:.3f}a{rx:.3f},{ry:.3f} 0,1 0,{2*rx:.3f},0a{rx:.3f},{ry:.3f} 0,1 0,{-2*rx:.3f},0',color)
def circle(x,y,r):return f'M{x-r},{y}a{r},{r} 0,1 0,{2*r},0a{r},{r} 0,1 0,{-2*r},0'
def grad(d,colors,radial=False):
    a='android:type="radial" android:centerX="43" android:centerY="35" android:gradientRadius="48"' if radial else 'android:startX="28" android:startY="18" android:endX="78" android:endY="80"'
    items=''.join(f'<item android:offset="{i/(len(colors)-1):.3f}" android:color="{c}"/>' for i,c in enumerate(colors))
    return f'<path android:pathData="{d}"><aapt:attr name="android:fillColor"><gradient {a}>{items}</gradient></aapt:attr></path>'
def body(k,day):
    s=grad(circle(50,47,45),['#40FFD879' if k=='sun' else '#34B7E6FF' if day else '#35B5B6FF','#00000000'],True)+oval(50,91,26,2.6,'#220C2445')
    if k=='sun':
        rays=' '.join(f'M{50+29*cos(i*pi/6):.3f},{45+29*sin(i*pi/6):.3f}L{50+35*cos(i*pi/6):.3f},{45+35*sin(i*pi/6):.3f}' for i in range(12))
        s+=path(rays,stroke='#FFFFD16A',width=3.6)+grad(circle(50,45,24),['#FFFFF7CC','#FFFFCE65','#FFF4944D'],True)
    elif k=='moon':
        s+=grad('M58,16C26,13 19,66 52,73C67,77 78,67 82,57C49,68 39,34 58,16Z',['#FFFFF6C5','#FFD9E8EC','#FFB9D5FF'])+oval(31.5,37.5,3.5,3.5,'#387D99C2')+oval(45.5,63.5,2.5,2.5,'#387D99C2')
    elif k=='unknown':
        s+=path('M34,26H66Q79,26 79,40V54Q79,68 65,68H35Q21,68 21,54V40Q21,26 34,26Z','#FFE4EAF1')+path('M44,40C44,31 60,31 60,41C60,47 50,47 50,53M50,59L50,59.5',stroke='#FF526B87',width=4)
    else:
        if not day:s+=oval(71,27,13,13,'#FFF0DDAC')
        colors=['#FFB9C8E3','#FF728BAA','#FF3A5379'] if k in ['rain','storm'] else ['#FFFFFFFF','#FFDFF2FF','#FFA6CFEA']
        s+=grad('M24,67C7,66 7,43 24,42C23,20 51,15 61,34C78,25 90,41 84,51C99,64 80,73 68,69L24,69Z',colors)
        if k=='mist':s+=path('M20,77H80M25,83H73',stroke='#AAB8D5E8',width=2)
        if k=='storm':s+=path('M65,69L56,81L64,80L60,90L74,75L65,76Z','#FFFFD97B')
    if k!='unknown':
        y=52 if k=='sun' else 51 if k=='moon' else 59
        s+=oval(35.5,y+1,4.5,2,'#65F0A6B5')+oval(66.5,y+1,4.5,2,'#65F0A6B5')+path('M28,39C36,25 47,25 54,28',stroke='#70FFFFFF',width=1.2)
    return s
def eyes(k,f,v=0):
    if k=='unknown':return ''
    t=f/(FRAMES-1);blink=.24+random.Random(271+v*179).random()*.47;o=min(1,abs(t-blink)/.065)
    if v==3:o=min(o,min(1,abs(t-.82)/.045))
    gx=sin(t*2*pi)*(1+v*.5);gy=sin(t*4*pi)*.65;y=44 if k=='sun' else 46 if k=='moon' else 52;l,r=(37,52) if k=='moon' else (40,62);s=''
    for x in [l,r]:
        s+=path(f'M{x-2.2},{y}L{x+2.2},{y}',stroke='#FF283E5C',width=2.3) if o<.23 else oval(x+gx,y+gy,2.6,3.8*o,'#FF283E5C')+oval(x-.5+gx,y-1.6+gy,.85*o,.85*o,'#FFF8FCFF')
    return s+path(f'M{l+5},{y+5}Q{(l+r)/2},{y+13} {r-5},{y+5}',stroke='#FF283E5C',width=1.5)
def fx(k,f,ground=False):
    t=f/FRAMES;s=''
    if k in ['rain','storm','snow']:
        for i in range(6):
            x=23+i*10;q=(t+i/6)%1
            if ground:
                age=(q-.82)/.18
                if k=='snow':s+=oval(x,92,1.7,1,'#99EAF8FF')
                elif 0<=age<=1:
                    col=f'#{int((1-age)*180):02X}96DFFF'
                    s+=path(f'M{x-6*age:.3f},91a{max(.01,6*age):.3f},{max(.01,2*age):.3f} 0,1 0,{12*age:.3f},0',stroke=col,width=.8)
                    for sign in [-1,1]:s+=oval(x+sign*age*5,91-sin(age*pi)*4,.65,.65,col)
            elif q<.82:
                y=69+q/.82*22
                s+=oval(x+sin(q*2*pi)*2,y,1.4,1.4,'#F2EAF8FF') if k=='snow' else path(f'M{x+1},{y:.3f}L{x},{y+3:.3f}',stroke='#D9A9E8FF',width=1.5)
    elif not ground and k!='unknown':
        for i,(x,y) in enumerate([(76,23),(20,60),(79,74)]):
            a=int(75+140*(.5+.5*sin((t+i/3)*2*pi)));col=f'#{a:02X}FFF4CC' if k in ['sun','moon'] else f'#{a:02X}D9F5FF';r=1.1+.6*sin((t+i/3)*2*pi)
            s+=path(f'M{x-r:.3f},{y}L{x+r:.3f},{y}M{x},{y-r:.3f}L{x},{y+r:.3f}',stroke=col,width=.9)
        if k in ['cloud','mist']:s+=path(f'M{22+8*sin(t*2*pi):.3f},79Q45,75 69,79',stroke='#70C4E6F9',width=1)
    return s
def write(name,s,w=100,h=100,offset=None):
    if not s:s=path('M0,0L0.01,0.01')
    if offset:s=f'<group android:translateX="{-offset[0]}" android:translateY="{-offset[1]}">{s}</group>'
    (DRAW/(name+'.xml')).write_text(f'<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="{w}dp" android:height="{h}dp" android:viewportWidth="{w}" android:viewportHeight="{h}">{s}</vector>\n')
def main():
    for k in KINDS:
        for day in [True,False]:
            n=f'{k}_{"day" if day else "night"}';write('buddy_vector_body_'+n,body(k,day));write('buddy_vector_still_'+n,body(k,day)+eyes(k,0)+fx(k,5)+fx(k,10,True))
        for f in range(FRAMES):
            n=f'{k}_{f:02}';wet=k in ['rain','storm','snow'];write('buddy_vector_fx_'+n,fx(k,f),100,34,(0,64)) if wet else write('buddy_vector_fx_'+n,fx(k,f))
            if wet:write('buddy_vector_ground_'+n,fx(k,f,True),100,10,(0,86))
    for face in ['sun','moon','cloud']:
        for v in range(4):
            for f in range(FRAMES):write(f'buddy_vector_eye_{face}_{v}_{f:02}',eyes(face,f,v),47,28,(28,34 if face=='sun' else 36 if face=='moon' else 42))
    out=['package io.atmos.weather.widgets','import io.atmos.weather.R','internal object BuddyVectorResources {']
    def arr(n,vals):out.append(f'    val {n}=intArrayOf('+','.join('R.drawable.'+v for v in vals)+')')
    for k in KINDS:
        for pre in ['body','still']:arr(pre+'_'+k,[f'buddy_vector_{pre}_{k}_{d}' for d in ['day','night']])
        arr('fx_'+k,[f'buddy_vector_fx_{k}_{f:02}' for f in range(FRAMES)])
        if k in ['rain','storm','snow']:arr('ground_'+k,[f'buddy_vector_ground_{k}_{f:02}' for f in range(FRAMES)])
    for face in ['sun','moon','cloud']:
        for v in range(4):arr(f'eye_{face}_{v}',[f'buddy_vector_eye_{face}_{v}_{f:02}' for f in range(FRAMES)])
    for fun,pre,day,wet in [('body','body',True,False),('still','still',True,False),('effects','fx',False,False),('ground','ground',False,True)]:
        out.append(f'    fun {fun}(kind:WeatherBuddyArt.Kind'+(',day:Boolean' if day else '')+')=when(kind) {')
        for k in (['rain','storm','snow'] if wet else KINDS):out.append(f'        WeatherBuddyArt.Kind.{k.upper()}->{pre}_{k}'+('[if(day)0 else 1]' if day else ''))
        if wet:out.append('        else->intArrayOf()')
        out.append('    }')
    out+=['    fun eyes(kind:WeatherBuddyArt.Kind,variant:Int):IntArray {','        val v=Math.floorMod(variant,4)','        return when(kind) {']
    for k in ['sun','moon']:out.append(f'            WeatherBuddyArt.Kind.{k.upper()}->arrayOf('+','.join(f'eye_{k}_{v}' for v in range(4))+')[v]')
    out+=['            else->arrayOf(eye_cloud_0,eye_cloud_1,eye_cloud_2,eye_cloud_3)[v]','        }','    }','}']
    (ROOT/'app/src/main/java/io/atmos/weather/widgets/BuddyVectorResources.kt').write_text('\n'.join(out)+'\n')
    print('Native vector resources:',len(list(DRAW.glob('buddy_vector_*.xml'))))
if __name__=='__main__':main()
