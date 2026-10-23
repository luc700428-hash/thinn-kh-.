package io.atmos.weather.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Original neutral 24x24 vector symbols, designed for the app tab bar.
 * No Apple SF Symbols, Apple font assets, or Android Material glyphs are bundled here.
 * Tint is applied by the existing Compose Icon call and preserves its active state.
 */
object AppleGlyphs {
    private fun lineIcon(name: String, svgPath: String): ImageVector =
        ImageVector.Builder(name=name, defaultWidth=24.dp, defaultHeight=24.dp,
            viewportWidth=24f, viewportHeight=24f).apply {
            addPath(pathData=PathParser().parsePathString(svgPath).toNodes(),
                stroke=SolidColor(Color.White), strokeLineWidth=1.75f,
                strokeLineCap=StrokeCap.Round, strokeLineJoin=StrokeJoin.Round)
        }.build()

    val Sun: ImageVector by lazy { lineIcon("Weather Sun",
        "M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 " +
        "M12 2 L12 4 M12 20 L12 22 M2 12 L4 12 M20 12 L22 12 " +
        "M4.93 4.93 L6.34 6.34 M17.66 17.66 L19.07 19.07 " +
        "M19.07 4.93 L17.66 6.34 M6.34 17.66 L4.93 19.07") }
    val Quake: ImageVector by lazy { lineIcon("Seismic Waves",
        "M2 8 C4.5 5.5 6.5 10.5 9 8 S13.5 5.5 16 8 S20.5 10.5 22 8 " +
        "M2 13 C4.5 10.5 6.5 15.5 9 13 S13.5 10.5 16 13 S20.5 15.5 22 13 " +
        "M2 18 C4.5 15.5 6.5 20.5 9 18 S13.5 15.5 16 18 S20.5 20.5 22 18") }
    val Grid: ImageVector by lazy { lineIcon("Tool Grid",
        "M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z M14 14 H21 V21 H14 Z") }
    val Settings: ImageVector by lazy { lineIcon("Settings",
        "M12 3 L13.7 4 L15.5 3.7 L17.2 5.4 L16.9 7.2 L18.2 8.6 L20.3 8.9 L20.3 11.2 " +
        "L18.5 12.3 L18.5 14 L20.3 15.2 L20.3 17.2 L18.1 17.6 L16.9 19.1 " +
        "L17.2 20.8 L15.5 21.7 L13.7 20.7 L12 21 L10.3 20.7 L8.5 21.7 " +
        "L6.8 20.8 L7.1 19.1 L5.9 17.6 L3.7 17.2 L3.7 15.2 L5.5 14 L5.5 12.3 " +
        "L3.7 11.2 L3.7 8.9 L5.8 8.6 L7.1 7.2 L6.8 5.4 L8.5 3.7 L10.3 4 Z " +
        "M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9") }
}
