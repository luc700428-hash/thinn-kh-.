#!/usr/bin/env python3
"""Original non-musical report notification: three fixed 1 kHz pulses, 44.1 kHz PCM."""
from pathlib import Path
import argparse,math,struct,wave

def generate(destination:Path):
    destination.parent.mkdir(parents=True,exist_ok=True)
    rate=44100;frames=[]
    for sample in range(round(rate*1.35)):
        time=sample/rate;value=0.0
        for start in (.12,.49,.86):
            elapsed=time-start
            if 0<=elapsed<.22:
                envelope=min(1,elapsed/.012,(.22-elapsed)/.035)
                value=.28*envelope*math.sin(2*math.pi*1000*time)
        frames.append(struct.pack('<h',round(32767*value)))
    with wave.open(str(destination),'wb') as output:
        output.setparams((1,2,rate,0,'NONE','not compressed'));output.writeframes(b''.join(frames))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output',nargs='?',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/res/raw/thinn_eq_report_signal.wav')
    generate(parser.parse_args().output)
