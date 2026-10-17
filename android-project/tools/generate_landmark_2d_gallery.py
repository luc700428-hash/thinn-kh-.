#!/usr/bin/env python3
"""Reproducible original 2D *illustrative* cards for the included landmark catalog.

These are NOT photographs or architectural surveys. No external images are scraped.
The generated images are deliberately identified as conceptual silhouettes.
Usage: python tools/generate_landmark_2d_gallery.py
Requires Pillow; prebuilt WebPs ship with Android sources.
"""
import json, math, random, hashlib
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/landmarks'
CATALOG=ASSETS/'catalog_v2.json'
OUTPUT=ASSETS/'illustrations'
W,H=960,540
FONT_CANDIDATES=['/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc','/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc','/usr/share/fonts/truetype/arphic/uming.ttc']
font_path=next((f for f in FONT_CANDIDATES if Path(f).exists()),None)

def font(size,bold=False):
    candidate='/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc' if bold and Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc').exists() else font_path
    return ImageFont.truetype(candidate,size) if candidate else ImageFont.load_default()

def blend(a,b,t): return tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))
def polygon(d,pts,fill,outline=None,width=1):d.polygon(pts,fill=fill);(d.line(pts+[pts[0]],fill=outline,width=width) if outline else None)
def line(d,coords,color,width=4):d.line(coords,fill=color,width=width,joint='curve')
def circle(d,x,y,r,color):d.ellipse((x-r,y-r,x+r,y+r),fill=color)

def classify(n):
    if any(s in n for s in ['101','85大樓','晴空塔','首爾塔','艾菲爾鐵塔','大笨鐘','慈恩塔','觀景台']):return 'tower'
    if any(s in n for s in ['噴水','溫泉','噴泉']):return 'fountain'
    if any(s in n for s in ['自由女神','大佛','雕塑']):return 'statue'
    if any(s in n for s in ['橋','天空步道','步橋']):return 'bridge'
    if any(s in n for s in ['燈塔']):return 'lighthouse'
    if any(s in n for s in ['機場']):return 'airport'
    if any(s in n for s in ['車站','鐵道','火車','鐵花','車庫']):return 'rail'
    if any(s in n for s in ['宮','寺','祠','廟','樓','古堡','神社','門城','北門','城隍','古蹟']):return 'temple'
    if any(s in n for s in ['美術館','博物館','文化','藝術','劇院','歌劇','衛武營','表演']):return 'museum'
    if any(s in n for s in ['洞','坑道']):return 'cave'
    if any(s in n for s in ['港','灣','沙灘','潭','礁','海','石滬']):return 'seaside'
    if any(s in n for s in ['濱海灣金沙']):return 'hotel'
    if any(s in n for s in ['老街','聚落','森活村','勝利星村','二崁','花宅']):return 'village'
    if any(s in n for s in ['峽','山','谷','島','岩','濕地','草原','高地','太魯閣','公園','瀑布','農場','森林']):return 'nature'
    if any(s in n for s in ['夢時代','宮原眼科','布袋戲館','糖廠']):return 'city'
    return 'landmark'

def draw_scene(place):
    n=place['name']; theme=classify(n)
    seed=int(hashlib.sha256(place['id'].encode()).hexdigest()[:12],16)
    r=random.Random(seed)
    palettes=[((18,87,151),(112,192,229),(245,169,105)),((19,72,121),(99,170,217),(238,187,133)),((33,90,142),(111,203,208),(251,174,118)),((27,83,130),(145,203,218),(240,191,137))]
    top,bottom,sun=palettes[seed%len(palettes)]
    im=Image.new('RGB',(W,H)); pixels=im.load()
    # Sunset-to-sea gradient, drawing simple shapes with clear illustration contrast.
    for y in range(H):
        c=blend(top,bottom, min(1,y/H*.98));
        for x in range(W):pixels[x,y]=c
    d=ImageDraw.Draw(im,'RGB')
    circle(d,780+r.randint(-65,40),123+r.randint(-25,30),45,sun)
    for i in range(8):
        cx=r.randint(30,W-30);cy=r.randint(48,202);rw=r.randint(25,68)
        d.ellipse([cx-rw,cy-10,cx+rw,cy+9],fill=blend(top,(240,248,251),.42))
    # Distant mountains / coastal scene
    polygon(d,[(0,341),(140,248+r.randint(0,18)),(310,351),(480,271),(665,344),(815,265),(960,339),(960,540),(0,540)],(56,116,129))
    polygon(d,[(0,373),(190,299),(330,385),(510,307),(670,392),(855,315),(960,373),(960,540),(0,540)],(40,104,127))
    d.rectangle((0,395,W,H), fill=(34,101,128))
    # Water reflections / tiled plaza below subject
    for yy in range(412,534,23):
        for _ in range(15):
            xx=r.randint(0,W-40); wid=r.randint(15,85)
            line(d,[(xx,yy),(xx+wid,yy)],(64,145,170),2)
    c1=(251,237,200); c2=(231,213,176);dark=(35,73,96)
    cx=505; base=410
    def skyline():
        for x in (102,175,250,720,794,863):
            h=r.randint(75,170);ww=r.randint(48,86)
            d.rectangle((x,base-h,x+ww,base),fill=(70,121,135))
            for y in range(base-h+15,base-10,20):
                for xx in range(x+12,x+ww-8,18):
                    d.rectangle((xx,y,xx+6,y+9), fill=(251,213,139))
    if theme in ('tower','fountain','statue','city','hotel','museum','airport'):skyline()
    if theme=='tower':
        if '101' in n:
            d.rectangle((cx-54,128,cx+54,base),fill=(44,144,155))
            for y in range(145,397,33):
                polygon(d,[(cx-76,y),(cx+76,y),(cx+57,y+30),(cx-57,y+30)],(87,202,183))
                line(d,[(cx-69,y),(cx+68,y)],(238,246,206),3)
            line(d,[(cx,77),(cx,130)],(232,241,228),8)
        elif '艾菲爾' in n:
            polygon(d,[(cx,118),(cx+35,260),(cx+95,410),(cx+47,410),(cx,290),(cx-47,410),(cx-95,410),(cx-35,260)],(201,155,101))
            for yy in range(192,411,43):line(d,[(cx-int((yy-100)*.30),yy),(cx+int((yy-100)*.30),yy)],(245,218,169),7)
            line(d,[(cx,87),(cx,120)],c1,6)
        elif '大笨鐘' in n:
            d.rectangle((cx-70,132,cx+70,410), fill=(189,156,113))
            d.polygon([(cx-91,142),(cx,75),(cx+91,142)],fill=(102,95,89))
            circle(d,cx,202,48,(250,236,191));circle(d,cx,202,39,dark)
            for ang in range(0,360,30):
                xx=cx+32*math.cos(math.radians(ang));yy=202+32*math.sin(math.radians(ang));circle(d,xx,yy,2,c1)
            line(d,[(cx,202),(cx,199),(cx+16,190)],c1,5)
        else:
            polygon(d,[(cx-54,410),(cx-23,160),(cx+23,160),(cx+56,410)],(214,226,229))
            d.ellipse((cx-78,146,cx+78,191), fill=(105,192,206))
            line(d,[(cx,90),(cx,148)],c1,7)
    elif theme=='fountain':
        # Landmark-specific KANO figure over distinctive circular pool, not a generic cube.
        d.ellipse((190,364,820,467),fill=(22,104,129))
        d.ellipse((225,362,785,435),fill=(104,224,228))
        d.ellipse((334,355,674,412),fill=(219,204,170))
        polygon(d,[(cx-42,285),(cx+44,285),(cx+28,364),(cx-25,364)],(174,151,114))
        for dx in (-230,-175,-90,84,160,215):
            d.arc((cx+dx-90,277,cx+dx+90,433),195,340,fill=(226,248,242),width=7)
        # Baseball pitcher pose, stylized and contextual rather than exact replica
        circle(d,cx,205,21,(226,183,124));line(d,[(cx-8,227),(cx-22,283)],(235,196,134),22)
        line(d,[(cx-14,246),(cx-101,204)],(233,205,144),16)
        line(d,[(cx+6,250),(cx+100,235)],(234,191,120),15)
        line(d,[(cx-22,275),(cx-77,333)],(226,193,141),17)
        line(d,[(cx-22,277),(cx+55,322)],(224,186,135),18)
    elif theme=='bridge':
        d.rectangle((0,355,W,460),fill=(39,134,162))
        for x in range(0,W,22):
            y=334-15*math.sin(x/960*math.pi)
            d.rectangle((x,int(y),x+22,int(y+15)),fill=(219,223,220))
        d.arc((95,104,885,661),181,358,fill=(235,235,216),width=22)
        for x in range(120,890,70):
            y=335 - 215*(1-((x-490)/400)**2)
            line(d,[(x,y),(x,336)],(217,225,223),5)
        d.rectangle((130,337,830,353),fill=(239,218,173))
    elif theme=='lighthouse':
        polygon(d,[(cx-70,410),(cx-34,172),(cx+34,172),(cx+68,410)],(244,241,220))
        d.rectangle((cx-52,157,cx+52,198),fill=(43,86,102))
        d.polygon([(cx-68,155),(cx,113),(cx+68,155)],fill=(241,87,75))
        circle(d,cx,176,14,(251,229,111))
        for ang in (-45,0,45):
            polygon(d,[(cx,175),(cx+int(110*math.cos(math.radians(ang))),175+int(110*math.sin(math.radians(ang)))),(cx+int(140*math.cos(math.radians(ang+12))),175+int(140*math.sin(math.radians(ang+12))))],(211,219,144))
    elif theme=='statue':
        polygon(d,[(cx-60,351),(cx+60,351),(cx+84,410),(cx-86,410)],(220,210,180))
        circle(d,cx,208,29,(187,220,181));line(d,[(cx,239),(cx,342)],(179,209,178),40)
        line(d,[(cx-13,263),(cx-79,313)],(170,205,169),24)
        line(d,[(cx+12,256),(cx+80,157)],(172,220,183),24)
        polygon(d,[(cx+75,150),(cx+109,98),(cx+101,166)],(248,202,91))
        for i in range(7):line(d,[(cx+94,135),(cx+83+i*4,109)],(255,208,97),3)
    elif theme=='temple':
        for xx in (cx-166,cx-50,cx+66):d.rectangle((xx,271,xx+100,406),fill=(210,180,132))
        polygon(d,[(cx-205,268),(cx,164),(cx+205,268),(cx+148,278),(cx,208),(cx-145,278)],(197,79,65))
        line(d,[(cx-206,264),(cx,162),(cx+206,264)],(245,196,100),8)
        d.rectangle((cx-55,308,cx+55,406),fill=(79,105,117))
        for x in (cx-157,cx+135):line(d,[(x,268),(x,399)],(201,59,60),16)
    elif theme=='rail':
        d.rectangle((228,195,782,389), fill=(221,193,148));polygon(d,[(194,206),(505,128),(815,206)],(144,80,77))
        d.rectangle((405,273,605,389),fill=(63,115,138))
        for x in (265,350,641,718):d.rectangle((x,244,x+47,296),fill=(105,166,182))
        line(d,[(225,500),(735,500)],(222,225,223),7);line(d,[(250,536),(710,536)],(199,204,198),7)
    elif theme=='museum':
        d.rectangle((250,212,754,411),fill=(224,223,200))
        polygon(d,[(217,218),(503,119),(789,218)],(211,192,150))
        for x in (300,385,472,560,640,712):
            d.rectangle((x,228,x+18,401),fill=(243,240,213))
        d.rectangle((230,405,782,420),fill=(187,181,164))
    elif theme=='seaside':
        polygon(d,[(60,380),(200,290),(333,349),(410,405)],(67,139,114))
        polygon(d,[(640,399),(785,271),(935,406)],(70,137,117))
        d.arc((230,330,730,550),181,359,fill=(214,232,227),width=18)
        for x in range(40,910,105):line(d,[(x,464),(x+56,464)],(132,222,221),4)
        if '雙心' in n or '石滬' in n:
            for off in (-85,95):
                d.arc((cx-105+off,342,cx+105+off,450),10,170,fill=(223,208,155),width=15)
    elif theme=='cave':
        polygon(d,[(130,440),(260,260),(405,180),(570,190),(750,280),(850,440)],(73,104,112))
        d.ellipse((265,251,748,575),fill=(21,51,67))
        d.ellipse((345,322,655,560),fill=(56,126,142))
        line(d,[(300,437),(720,437)],(233,228,180),10)
    elif theme=='village':
        for i,x in enumerate((170,345,518,683)):
            y=260+int(21*math.sin(i));c=(217-r.randint(0,25),180-r.randint(0,22),130+r.randint(0,35))
            d.rectangle((x,y,x+165,420),fill=c)
            polygon(d,[(x-17,y),(x+82,y-84),(x+181,y)],(161,75+r.randint(0,35),76))
            for wx in (x+24,x+104):d.rectangle((wx,y+55,wx+28,y+92),fill=(87,151,163))
    elif theme=='airport':
        d.rounded_rectangle((160,260,825,399),radius=19,fill=(203,219,219))
        d.rectangle((210,236,755,270), fill=(64,121,156))
        for x in range(239,725,70):d.rectangle((x,288,x+43,349), fill=(111,175,203))
        line(d,[(550,174),(747,199)],(240,242,236),13)
        polygon(d,[(500,169),(620,150),(720,180),(614,193)],(231,234,234))
        line(d,[(611,157),(591,109)],(236,240,235),8)
    elif theme=='nature':
        polygon(d,[(165,429),(360,164),(523,390)],(74,137,113))
        polygon(d,[(417,429),(651,183),(823,429)],(81,146,117))
        polygon(d,[(360,164),(322,217),(395,210)],(217,236,208))
        for x in (186,250,580,780):
            line(d,[(x,406),(x,341)],(111,90,69),12)
            circle(d,x,325,39,(72,161,109));circle(d,x+22,346,32,(83,182,129))
    elif theme=='city':
        for i,x in enumerate((180,314,449,590,720)):
            h=[170,128,235,168,135][i];ww=105
            d.rounded_rectangle((x,410-h,x+ww,410),radius=9,fill=(176+10*i,189-8*i,178+4*i))
            for y in range(410-h+15,401,25):
                for xx in range(x+15,x+ww-10,28):d.rectangle((xx,y,xx+11,y+12),fill=(254,222,154))
    elif theme=='hotel':
        for x in (310,463,616):d.rounded_rectangle((x,196,x+130,410),radius=11,fill=(186,216,216))
        d.arc((293,132,767,218),180,360,fill=(234,212,163),width=25)
    else:
        d.polygon([(180,410),(260,204),(745,204),(840,410)],fill=(206,207,178))
        d.polygon([(208,214),(509,127),(787,214)], fill=(193,111,97))
        d.ellipse((454,270,559,382),fill=(83,153,160))
    # Botanical silhouettes framing the architecture
    for x,y in ((105,410),(827,414),(80,447),(880,449)):
        line(d,[(x,y),(x,y-62)],(78,101,70),8)
        for dx,dy in ((-22,-56),(22,-54),(0,-76)):circle(d,x+dx,y+dy,27,(65+r.randint(0,16),147+r.randint(0,22),110))
    # Text strip gives precise identification even for stylized archetypes.
    overlay=Image.new('RGBA',(W,H),(0,0,0,0));od=ImageDraw.Draw(overlay)
    od.rounded_rectangle((24,22,267,71),radius=21,fill=(13,41,61,203))
    od.text((43,31),str(place['display_region'])+'  /  2D',font=font(25,True),fill=(251,250,245,255))
    od.rectangle((0,443,W,H),fill=(7,26,43,183))
    title=n if len(n)<=16 else n[:15]+'…'
    size=43 if len(title)<=12 else 36
    od.text((46,459),title,font=font(size,True),fill=(255,255,254,255),stroke_width=1,stroke_fill=(11,44,61,225))
    od.text((W-158,506),'風格示意',font=font(19),fill=(219,233,240,255))
    im=Image.alpha_composite(im.convert('RGBA'),overlay).convert('RGB')
    return im

def main():
    obj=json.loads(CATALOG.read_text(encoding='utf-8'))
    OUTPUT.mkdir(exist_ok=True,parents=True)
    for p in obj['landmarks']:
        if p['name']=='中央噴水圓環':
            p['illustration']='landmarks/illustrations/chiayi_fountain_2d.webp'
            continue
        path=OUTPUT/(p['id']+'.webp')
        draw_scene(p).save(path,'WEBP',quality=81,method=4)
        p['illustration']='landmarks/illustrations/'+path.name
    obj['illustrations_version']='v23-r4-2d-catalog-20261017'
    obj['illustrations_notice']='The 2D art is custom stylized interpretive imagery, not photographic or survey-accurate representation.'
    CATALOG.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('2D illustrations', len(obj['landmarks']), 'TW',sum(p['country']=='TW' for p in obj['landmarks']), 'INTL',sum(p['country']=='INTL' for p in obj['landmarks']))

if __name__=='__main__':main()
