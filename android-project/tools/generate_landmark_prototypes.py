#!/usr/bin/env python3
"""Build ORIGINAL schematic glTF landmarks and corresponding preview images.

These are recognizable *prototypes*, not photogrammetry or surveyed architecture.
No copyrighted photos/maps/building textures are embedded. Geometry built with primitives.
"""
import math
from pathlib import Path
import trimesh
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BASE=Path(__file__).resolve().parents[1]/'app/src/main/assets/landmarks/individual'
BASE.mkdir(parents=True,exist_ok=True);(BASE/'previews').mkdir(exist_ok=True)
PALETTE={'ice':(163,225,243,255),'deep':(34,103,136,255),'cream':(235,228,213,255),
 'brick':(164,95,81,255),'rust':(167,119,81,255),'teal':(45,171,165,255),
 'lilac':(126,135,172,255),'green':(91,161,114,255),'rock':(110,139,139,255),
 'sand':(228,190,147,255),'glass':(80,186,197,255),'light':(228,246,246,255),
 'gold':(236,195,106,255),'blue':(72,147,190,255),'dark':(55,86,117,255),'sea':(62,174,195,255),
 'rose':(214,147,138,255)}
class Builder:
 def __init__(self):self.scene=trimesh.Scene();self.count=0
 def add(self,mesh,color):
  mesh.visual.face_colors=PALETTE.get(color,color)
  self.scene.add_geometry(mesh,node_name=f'g{self.count}');self.count+=1
 def box(self,x,y,z,w,d,h,c):
  m=trimesh.creation.box(extents=[w,d,h]);m.apply_translation([x,y,z+h/2]);self.add(m,c)
 def cylinder(self,x,y,z,r,h,c,sections=16):
  m=trimesh.creation.cylinder(radius=r,height=h,sections=sections);m.apply_translation([x,y,z+h/2]);self.add(m,c)
 def cone(self,x,y,z,r,h,c):
  m=trimesh.creation.cone(radius=r,height=h,sections=12);m.apply_translation([x,y,z]);self.add(m,c)
 def beam(self,a,b,r,c):
  a=np.array(a,dtype=float);b=np.array(b,dtype=float);v=b-a;l=np.linalg.norm(v)
  if l<.001:return
  m=trimesh.creation.cylinder(radius=r,height=l,sections=8)
  m.apply_transform(trimesh.geometry.align_vectors([0,0,1],v/l));m.apply_translation((a+b)*.5);self.add(m,c)
 def trees(self,n=6):
  for i in range(n):
   x=(-30+(i%3)*30);y=(-23+(i//3)*45)
   self.cylinder(x,y,0,1.1,4,'rust');self.cone(x,y,3,5,10,'green')
 def ground(self,sea=False):
  self.box(0,0,-4,82,64,4,'sea' if sea else 'cream')
  self.box(0,0,-.3,81,63,.3,'sand' if sea else 'green')
 def island(self):
  self.box(0,0,-4,85,70,4,'sea')
 def rock(self,x,y,z,sx,sy,sz,c='rock'):
  m=trimesh.creation.icosphere(subdivisions=1,radius=1);m.apply_scale([sx,sy,sz]);m.apply_translation([x,y,z]);self.add(m,c)

def make(kind):
 b=Builder()
 if kind in ['guishan_island','xiaoliuqiu_flower','lanyu_bay']:
  b.island()
  if kind=='guishan_island':
   b.rock(-8,0,3,23,14,10,'green');b.rock(12,3,2,17,11,7,'rock');b.rock(-15,-3,9,14,10,7,'green')
   for x,y in [(-28,16),(7,-17),(26,3)]:b.rock(x,y,1,4,3,2,'sand')
  elif kind=='xiaoliuqiu_flower':
   b.rock(-13,7,2,23,17,4,'green')
   b.cylinder(20,-12,2,3.2,22,'rock');b.rock(22,-12,22,8,6,5,'rock')
   b.rock(10,-15,2,9,6,2,'sand')
  else:
   for x,y,r,h in [(-23,5,17,8),(-5,8,19,16),(18,4,14,7)]:b.rock(x,y,3,r,r*.7,h,'green')
   b.box(0,-19,-.5,48,10,.8,'sand')
   for x in [-22,-8,7,22]:b.box(x,-19,0,6,3,6,'cream');b.cone(x,-19,6,4,4,'rust')
 else:
  b.ground()
  if kind=='taipei101':
   for i in range(8):
    z=i*12.2
    b.box(0,1,z,21-i*.25,20-i*.22,11.7,'glass' if i%2==0 else 'teal')
    b.box(0,1,z+11.3,23-i*.2,22-i*.2,1.5,'dark')
   b.cone(0,1,98,2.8,25,'dark');b.beam((0,1,114),(0,1,134),1,'gold')
   for x in [-14,14]:b.box(x,1,0,6,21,25,'deep')
  elif kind=='kaohsiung85':
   for x in [-12,12]:
    b.box(x,0,0,18,23,73,'glass')
    for z in [10,25,40,55,70]:b.box(x,-12,z,19,1.1,1,'deep')
   b.box(0,0,70,34,25,16,'glass');b.box(0,0,86,15,21,29,'teal')
   b.beam((0,0,114),(0,0,130),1.5,'gold')
  elif kind=='dreammall':
   b.box(-4,2,0,65,38,29,'cream');b.box(-4,-18,2,65,3,13,'teal')
   for z in [8,17,25]:b.box(-4,-19.6,z,57,1,2,'gold')
   b.box(25,5,0,10,10,52,'glass')
   cx,cy,cz=22,-16,48
   for i in range(16):
    a=i*2*math.pi/16;bb=(i+1)*2*math.pi/16
    b.beam((cx+15*math.cos(a),cy,cz+15*math.sin(a)),(cx+15*math.cos(bb),cy,cz+15*math.sin(bb)),.9,'rose')
    if i%2==0:b.beam((cx,cy,cz),(cx+15*math.cos(a),cy,cz+15*math.sin(a)),.5,'light')
   b.beam((cx,cy,25),(cx,cy,cz),1.5,'deep')
  elif kind=='tamkang_bridge':
   b.box(0,0,0,82,23,3,'deep');b.box(0,-4,0,82,2,2,'gold')
   b.beam((10,3,3),(4,3,68),2.2,'light')
   for x in range(-34,39,8):
    ht=3+(68-3)*(x+41)/50 if x<=9 else 3+(68-3)*(41-x)/32
    b.beam((4,3,68),(x,3,3),.5,'light')
   for x in [-34,0,34]:b.box(x,0,-4,4,20,5,'deep')
  elif kind=='music_center':
   for i in range(4):
    x=-24+i*16;h=[34,47,26,43][i]
    b.box(x,0,0,15,29,h,'glass' if i%2==0 else 'teal')
    b.cone(x,0,h,10,9,'light')
    b.box(x, -16, 3,15,2,7,'deep')
  elif kind=='miyahara':
   b.box(0,0,0,58,35,40,'brick')
   b.box(0,0,40,60,36,3,'rust')
   for x in [-23,-12,-1,10,21]:
    for z in [10,22,34]:
     b.box(x,-18,z,5,1.2,10,'deep');b.cone(x,-19,z+9,3.2,2,'rust')
   b.box(0,-18,0,9,3,14,'gold');b.trees(3)
  elif kind=='chihkan':
   for x in [-16,16]:
    b.box(x,0,0,24,25,13,'brick')
    b.box(x,0,13,30,30,2,'cream')
    b.cone(x,0,14,21,12,'rust')
    b.box(x,0,26,22,18,10,'brick');b.cone(x,0,36,16,8,'rust')
  elif kind=='hsinchu_gate':
   b.box(0,0,0,38,22,24,'brick')
   b.box(0,-12,0,12,2,18,'dark');b.box(0,0,24,45,28,4,'cream')
   b.cone(0,0,28,28,15,'rust')
   for x in [-23,23]:b.cylinder(x,-10,0,2,25,'brick')
  elif kind=='dongyin_lighthouse':
   b.box(0,0,0,40,40,3,'rock')
   b.cylinder(0,0,3,9,57,'light');b.cylinder(0,0,61,13,8,'dark')
   b.cone(0,0,69,13,10,'rust');b.cylinder(0,0,60,11,2,'gold')
   b.rock(-20,10,2,16,15,8,'rock');b.rock(22,5,2,14,14,5,'green')
 return b

def render_preview(scene, file):
 # Native geometric mesh preview: independent original 3D shapes, no external screenshot or watermarks.
 W,H=900,520
 im=Image.new('RGBA',(W,H));px=im.load()
 for y in range(H):
  t=y/H;col=(int(15+46*t),int(52+89*t),int(85+105*t),255)
  ImageDraw.Draw(im).line((0,y,W,y),fill=col)
 dr=ImageDraw.Draw(im,'RGBA')
 dr.ellipse((130,430,770,512),fill=(38,65,82,95))
 geoms=list(scene.geometry.values())
 vertices=np.vstack([g.vertices for g in geoms]);
 # Ortho isometric camera, x horizontal, y depth; z up. 
 theta=.64;co=math.cos(theta);si=math.sin(theta)
 def proj(v):
  x,y,z=v.T
  return np.stack([(co*x-si*y)*1.15,(si*x+co*y)*.45-z],axis=-1)
 ps=proj(vertices);mins=ps.min(axis=0);maxs=ps.max(axis=0)
 scale=min((W-185)/max(1,(maxs[0]-mins[0])),(H-105)/max(1,(maxs[1]-mins[1])))
 cen=(mins+maxs)*.5
 polys=[]
 for g in geoms:
  arr=np.asarray(g.vertices);p=proj(arr)
  cols=np.asarray(g.visual.face_colors)
  for i,f in enumerate(g.faces):
   tri=arr[f];normal=np.cross(tri[1]-tri[0],tri[2]-tri[0]);size=np.linalg.norm(normal)
   if size<1e-7:continue
   normal=normal/size
   # painter sort by camera-space depth; not exact z buffer but stable for opaque simple buildings
   depth=float(np.dot(tri.mean(axis=0),np.array([.57,.76,.24])))
   shade=max(.57,min(1.06,.73+.22*np.dot(normal,np.array([.36,-.45,.81]))))
   col=tuple(int(min(255,max(0,v*shade))) for v in cols[i,:3])+(255,)
   xy=[(float(W*.5+(a-cen[0])*scale),float(H*.53+(b-cen[1])*scale)) for a,b in p[f]]
   polys.append((depth,xy,col))
 for _,xy,c in sorted(polys,key=lambda t:t[0]):
  dr.polygon(xy,fill=c)
  # Omit the per-triangle wireframe. Form should read as one miniature, not a mesh preview.
 im.save(file)

if __name__=='__main__':
 for name in ['taipei101','kaohsiung85','dreammall','tamkang_bridge','music_center','miyahara',
              'chihkan','xiaoliuqiu_flower','lanyu_bay','guishan_island','hsinchu_gate','dongyin_lighthouse']:
  b=make(name)
  b.scene.export(BASE/f'{name}.glb')
  render_preview(b.scene,BASE/'previews'/f'{name}.png')
  print(name,len(b.scene.geometry))
