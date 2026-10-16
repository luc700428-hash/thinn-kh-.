#!/usr/bin/env python3
"""Original, place-specific Chiayi miniature sketches; no scraped geometry or photos.

These are NOT geodetically accurate reconstructions. Visible labels are mandatory.
"""
import importlib.util
import math
from pathlib import Path
import numpy as np
import trimesh
ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('prototype',ROOT/'generate_landmark_prototypes.py')
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
ASSET=m.BASE

def station_and_train(b,rail_elev=1):
    # Pair of steel rails + sleepers gives scale and context, not a generic cottage.
    for x in range(-39,39,5):
        b.box(x,-18,rail_elev,3,19,1.2,'rust')
    for y in (-23,-13): b.box(0,y,rail_elev+1.0,78,1.5,.9,'deep')
    # Red forestry locomotive, boiler, cowcatcher, smoke stack and couplings.
    b.box(-12,-18,rail_elev+6,23,12,13,'brick')
    b.cylinder(-19,-18,rail_elev+11,6,12,'rust')
    b.box(-1,-18,rail_elev+13,11,12,12,'dark')
    b.cylinder(-19,-18,rail_elev+23,2.3,9,'dark')
    b.cone(-19,-18,rail_elev+30,4,4,'gold')
    b.cylinder(-32,-18,rail_elev+7,4,13,'deep')
    b.box(15,-18,rail_elev+8,23,12,13,'rust')
    for x in (-29,-21,-10,0,10,21,29):
        for y in (-24,-12):
            b.cylinder(x,y,rail_elev+2.2,4,3,'dark')
            b.cylinder(x,y,rail_elev+5,2.4,1,'light')
    for x in (10,17,24):b.box(x,-24.4,rail_elev+14,4,1.4,6,'gold')

def alishan_railway():
    b=m.Builder();b.ground()
    b.box(0,-18,-.1,82,21,4,'rock')
    station_and_train(b,rail_elev=4)
    # Trees kept at the far edges; never hide the locomotive's defining silhouette.
    for x,y in [(-38,26),(-23,27),(30,25),(39,10)]:
        b.cylinder(x,y,0,1.1,6,'rust');b.cone(x,y,6,3.5,12,'green')
        b.cone(x,y,13,2.3,7,'green')
    return b

def tropic_cancer_solar():
    b=m.Builder();b.ground()
    b.box(-18,0,0,36,28,10,'cream')
    b.cylinder(12,3,0,14,9,'light')
    b.cylinder(12,3,9,12,4,'blue')
    # Large astronomy ring, vertical meridian arc and spherical sun sculpture.
    cx,cy,cz=12,3,38
    for i in range(84):
        a=i*2*math.pi/84; an=(i+1)*2*math.pi/84
        b.beam((cx+17*math.cos(a),cy,cz+17*math.sin(a)),
               (cx+17*math.cos(an),cy,cz+17*math.sin(an)),.67,'gold')
    b.rock(cx,cy,cz,6,6,6,'gold')
    for x,y in ((-23,-22),(-3,-22),(32,21)):
        b.cylinder(x,y,0,2.2,18,'light')
        b.cone(x,y,18,3,3,'gold')
    b.box(-19,-19,0,24,7,3,'deep')
    return b

def museum_south():
    b=m.Builder();b.ground(sea=True)
    b.box(0,1,0,67,36,4,'dark')
    b.box(-19,0,4,31,26,22,'light')
    b.box(17,7,3,38,32,17,'glass')
    # Curving pedestrian bridge and interlaced museum facade.
    for i in range(34):
        t=i/33; x=-32+t*64
        y=-13+8*math.sin(t*math.pi)
        z=13+24*math.sin(t*math.pi)
        if i>0:
            b.beam((px,py,pz),(x,y,z),1.6,'light')
        px,py,pz=x,y,z
        b.beam((x,y,z),(x,y,5),.35,'blue')
    for i in range(12):
        x=-26+i*4.5
        b.box(x,19,5,1.0,1.4,18,'deep')
    b.cylinder(-10,-20,0,13,1,'sea')
    b.box(33,-17,0,14,23,3,'sand')
    return b

def fenqihu_station():
    b=m.Builder();b.ground()
    b.box(1,13,0,67,24,2,'rock')
    # Long wooden station and covered platform, visually unlike the generic house.
    b.box(-7,11,2,55,18,14,'rust')
    b.box(-7,11,17,61,23,2,'deep')
    for x in (-28,-15,-2,11):b.box(x,-0.5,3,6,1.4,10,'cream')
    for x in (-28,-10,10,28):b.box(x,25,2,2,2,15,'cream')
    b.box(0,-18,0,80,15,2,'rock')
    station_and_train(b,rail_elev=1)
    for x,y in ((-33,29),(35,28)):
        b.cylinder(x,y,0,1.2,8,'rust');b.cone(x,y,8,6,18,'green')
    return b

def chiayi_fountain():
    b=m.Builder();b.ground()
    b.cylinder(0,0,0,22,2,'rock',sections=64)
    b.cylinder(0,0,2,17,1.7,'sea',sections=64)
    b.cylinder(0,0,4,5,13,'cream')
    b.cylinder(0,0,17,3.4,2,'gold')
    b.rock(0,0,21,3.5,3.5,7,'gold')
    for i in range(12):
        a=i*2*math.pi/12
        x=12*math.cos(a);y=12*math.sin(a)
        b.beam((x,y,5),(x*.6,y*.6,18),.6,'light')
    for i in range(6):
        a=i*2*math.pi/6
        x=32*math.cos(a);y=26*math.sin(a)
        b.cylinder(x,y,0,1.5,3,'rust');b.cone(x,y,3,4,8,'green')
    return b

SCENES={
    'alishan_railway':alishan_railway,
    'tropic_cancer_solar':tropic_cancer_solar,
    'npm_south':museum_south,
    'fenqihu_station':fenqihu_station,
    'chiayi_fountain':chiayi_fountain,
}
if __name__=='__main__':
    for name,fn in SCENES.items():
        b=fn()
        b.scene.export(ASSET/f'{name}.glb')
        m.render_preview(b.scene,ASSET/'previews'/f'{name}.png')
        print(name,len(b.scene.geometry))
