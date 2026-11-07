#!/usr/bin/env python3
"""Install real 3x native widget renders as the launcher's default forest previews.

First run WidgetRenderingTest.allTwentyEightWidgetsHaveNewRenderedPreviews.
"""
from pathlib import Path
import shutil
import struct

def install(project:Path):
    source=project/'app/build/widget-renders'
    resources=project/'app/src/main/res'
    work=[]
    for variant in range(28):
        src=source/f'catalogue_hd_{variant:02}.png'
        header=src.read_bytes()[:24]
        family={25:5,26:6,27:7}.get(variant,variant//5)
        expected=(960 if family in range(1,6) else 540,540)
        if header[:8]!=b'\x89PNG\r\n\x1a\n' or struct.unpack('>II',header[16:24])!=expected:
            raise ValueError(f'Missing native high-density preview: {src.name}')
        if variant<25:dest=resources/'drawable-nodpi'/f'widget_glass_{variant:02}.png'
        elif variant==25:dest=resources/'drawable-nodpi/widget_safety_preview.png'
        elif variant==26:dest=resources/'drawable-nodpi/widget_precision_preview.png'
        else:dest=resources/'drawable/widget_buddy_preview.png'
        work.append((src,dest))
    for src,dest in work:shutil.copy2(src,dest)
    print('Installed 28 high-density native widget previews.')

if __name__=='__main__':install(Path(__file__).resolve().parents[1])
