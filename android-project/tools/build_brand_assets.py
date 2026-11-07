#!/usr/bin/env python3
"""Build matching SVG, Android vectors and launcher sizes for the fixed green identity.

Only optional PNG export needs cairosvg. SVG/vector generation uses the standard library.
"""
from pathlib import Path
import argparse
import math
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[1]
RES = PROJECT / 'app/src/main/res'
BRANDING = PROJECT / 'branding'
ANDROID = 'http://schemas.android.com/apk/res/android'
AAPT = 'http://schemas.android.com/aapt'
ET.register_namespace('android', ANDROID)
ET.register_namespace('aapt', AAPT)

def circle(x, y, radius):
    return f'M{x-radius},{y}a{radius},{radius} 0 1,0 {radius*2},0a{radius},{radius} 0 1,0 {-radius*2},0'

MARK = [
    ('M132,12H380C446.3,12 500,65.7 500,132V380C500,446.3 446.3,500 380,500H132C65.7,500 12,446.3 12,380V132C12,65.7 65.7,12 132,12Z', 'tile'),
    (circle(342, 198, 80), '#FFD46E'),
    (circle(338, 192, 70), '#FFE397'),
    ('M166,405C131,405 104,375 104,331C104,290 134,257 176,255C183,213 222,182 269,182C316,182 355,222 359,275C388,279 409,307 409,341C409,377 384,405 351,405Z', '#D8ECE1'),
    ('M176,303C172,252 190,182 269,182C321,182 359,223 359,275V349H176Z', '#F5FAF6'),
    ('M166,303H355C384,303 407,326 407,354C407,382 384,405 355,405H166C138,405 116,382 116,354C116,326 138,303 166,303Z', 'cloud'),
]
SYMBOL = [
    (circle(352, 168, 66), '#FFE19A'),
    ('M119,381C85,381 58,354 58,320C58,265 92,220 134,220C151,220 167,225 181,233C198,179 232,143 278,143C340,143 386,188 395,247C427,257 449,283 449,318C449,352 422,379 389,379H119Z', 'cloud'),
    ('M182,407C239,383 310,386 394,405C401,407 405,412 404,417C403,422 398,425 392,424C310,406 246,405 187,428C179,431 172,427 172,421C172,415 175,410 182,407Z', 'wave'),
]
GRADIENTS = {
    'tile': (0, 0, 512, 512, ['#B9E2CE', '#87BFA5', '#5F9F82']),
    'cloud': (180, 190, 330, 420, ['#FCFFFC', '#F0F8F2', '#E0F0E5']),
    'wave': (170, 405, 405, 425, ['#B9E2CE', '#91BEA6', '#6EAA8B']),
}

def svg(paths):
    defs = []
    for key, (x1,y1,x2,y2,colors) in GRADIENTS.items():
        stops=''.join(f'<stop offset="{i/(len(colors)-1)}" stop-color="{c}"/>' for i,c in enumerate(colors))
        defs.append(f'<linearGradient id="{key}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stops}</linearGradient>')
    content=''.join(f'<path d="{d}" fill="{fill if fill.startswith("#") else "url(#"+fill+")"}"/>' for d,fill in paths)
    return '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><defs>'+''.join(defs)+'</defs>'+content+'</svg>\n'

def vector(paths):
    root=ET.Element('vector',{f'{{{ANDROID}}}width':'108dp',f'{{{ANDROID}}}height':'108dp',f'{{{ANDROID}}}viewportWidth':'512',f'{{{ANDROID}}}viewportHeight':'512'})
    for d,fill in paths:
        path=ET.SubElement(root,'path',{f'{{{ANDROID}}}pathData':d})
        if fill.startswith('#'):path.set(f'{{{ANDROID}}}fillColor',fill)
        else:
            attr=ET.SubElement(path,f'{{{AAPT}}}attr',{'name':'android:fillColor'})
            x1,y1,x2,y2,colors=GRADIENTS[fill]
            gradient=ET.SubElement(attr,'gradient',{f'{{{ANDROID}}}type':'linear',f'{{{ANDROID}}}startX':str(x1),f'{{{ANDROID}}}startY':str(y1),f'{{{ANDROID}}}endX':str(x2),f'{{{ANDROID}}}endY':str(y2)})
            for i,c in enumerate(colors):ET.SubElement(gradient,'item',{f'{{{ANDROID}}}offset':str(i/(len(colors)-1)),f'{{{ANDROID}}}color':c})
    ET.indent(root)
    return ET.tostring(root,encoding='unicode')+'\n'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--png',action='store_true');args=parser.parse_args()
    BRANDING.mkdir(exist_ok=True)
    for name,paths in [('brand_mark',MARK),('brand_symbol',SYMBOL)]:
        source=svg(paths)
        (BRANDING/(name+'.svg')).write_text(source)
        (RES/'drawable'/(name+'.xml')).write_text(vector(paths))
        if args.png:
            import cairosvg
            cairosvg.svg2png(bytestring=source.encode(),write_to=str(BRANDING/(name+'.png')),output_width=1024,output_height=1024)
            if name=='brand_mark':
                for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
                    cairosvg.svg2png(bytestring=source.encode(),write_to=str(RES/f'mipmap-{density}/ic_launcher.png'),output_width=size,output_height=size)

if __name__=='__main__':main()
