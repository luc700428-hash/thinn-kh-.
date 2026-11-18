Backdrop (com.kyant.backdrop) 1.0.6 by Kyant — https://github.com/Kyant0/AndroidLiquidGlass
Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0

Vendored into thinn-khì so it builds with this project's Compose version. Changes from upstream:
- effects/Lens.kt: the com.kyant.shapes branch is removed; shapes without corner radii get no
  lens instead of throwing.
- LayerRecorder.kt: the context parameter became an ordinary first parameter (this project's Kotlin has no context parameters).
- InverseLayerScope.kt: blendMode/colorFilter are plain properties (GraphicsLayerScope here has none).
- highlight/HighlightModifier.kt: no RuntimeShader on software canvases (plain colour rim instead).
