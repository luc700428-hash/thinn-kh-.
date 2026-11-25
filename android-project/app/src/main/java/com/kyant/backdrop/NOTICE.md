Backdrop (com.kyant.backdrop) 1.0.6 by Kyant — https://github.com/Kyant0/AndroidLiquidGlass
Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0

Vendored into thinn-khì so it builds with this project's Compose version. Changes from upstream:
- effects/Lens.kt: the com.kyant.shapes branch is removed; shapes without corner radii get no
  lens instead of throwing.
- LayerRecorder.kt: the context parameter became an ordinary first parameter (this project's Kotlin has no context parameters).
- InverseLayerScope.kt: blendMode/colorFilter are plain properties (GraphicsLayerScope here has none).
- highlight/HighlightModifier.kt: no RuntimeShader on software canvases (plain colour rim instead).
- RuntimeShaderCache.kt: compiled shaders are shared process-wide (SharedRuntimeShaders) and can be prewarmed.
- effects/LiquidRefraction.kt (added): the refraction model of @zakisheriff/liquid-glass (below) as an AGSL effect.

Liquid Glass (@zakisheriff/liquid-glass) by Zaki Sheriff — https://github.com/zakisheriff/Liquid-Glass
MIT License, Copyright (c) 2026 Zaki Sheriff. Its refraction engine (Snell's-law squircle bevel,
lens magnification and dispersion, specular rim), springs, touch response, switch, slider,
segmented-control and tab-bar lens behaviour are ported to Kotlin/Compose in
effects/LiquidRefraction.kt and io/atmos/weather/ui (LiquidGlassEngine.kt, LiquidSegmented.kt,
GlassControls.kt, LiquidBubbleSlider.kt, AppleLiquidDockPaint.kt, LiquidGlassKit.kt).

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
