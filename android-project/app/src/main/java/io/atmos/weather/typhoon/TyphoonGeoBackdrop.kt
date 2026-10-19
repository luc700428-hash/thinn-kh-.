package io.atmos.weather.typhoon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Simplified coastline display reference only, not a hazard or probability polygon.
 * Geodetic lon/lat points; drawn through the exact storm-map projection.
 * Offshore groups remain visible and labelled on the same map as the CWA track.
 */
object TyphoonGeoBackdrop {
    // Taiwan's coastline clockwise, sampled from geographic landmarks (not official GIS).
    private val taiwan = listOf(
        121.92 to 25.29, 122.00 to 25.02, 121.99 to 24.69,
        121.89 to 24.43, 121.72 to 24.05, 121.66 to 23.73,
        121.56 to 23.40, 121.40 to 23.04, 121.24 to 22.76,
        121.10 to 22.36, 120.91 to 22.07, 120.75 to 21.89,
        120.65 to 21.92, 120.73 to 22.16, 120.75 to 22.42,
        120.53 to 22.68, 120.35 to 22.88, 120.23 to 23.12,
        120.12 to 23.43, 120.10 to 23.72, 120.16 to 24.01,
        120.35 to 24.27, 120.47 to 24.55, 120.63 to 24.82,
        120.80 to 25.02, 121.00 to 25.13, 121.25 to 25.27,
        121.48 to 25.30, 121.72 to 25.28
    )
    private val islands = listOf(
        listOf(119.48 to 23.74,119.52 to 23.65,119.57 to 23.63,119.62 to 23.58,
               119.60 to 23.53,119.54 to 23.56,119.49 to 23.60,119.44 to 23.65), // Penghu
        listOf(118.28 to 24.51,118.41 to 24.53,118.47 to 24.48,118.47 to 24.40,
               118.39 to 24.38,118.31 to 24.40,118.27 to 24.45), // Kinmen
        listOf(119.91 to 26.25,119.99 to 26.25,120.03 to 26.19,119.98 to 26.15,
               119.90 to 26.16), // Matsu (representative major islands)
        listOf(121.43 to 22.69,121.50 to 22.65,121.51 to 22.62,
               121.48 to 22.60,121.43 to 22.63), // Green island
        listOf(121.47 to 22.08,121.60 to 22.08,121.60 to 22.01,
               121.54 to 21.95,121.48 to 21.97), // Lanyu
        listOf(120.36 to 22.37,120.39 to 22.35,120.38 to 22.32,
               120.35 to 22.33) // Xiaoliuqiu
    )
    val keyAnchors = listOf(
        121.0 to 23.65, 119.57 to 23.57, 118.38 to 24.44, 119.95 to 26.17
    )

    fun DrawScope.draw(viewport:TyphoonViewport) {
        val fill=Color(0xFF3E776F)
        val edge=Color(0xFF8AE9D7)
        fun polygon(positions:List<Pair<Double,Double>>) {
            if(positions.size<3) return
            val path=Path()
            positions.forEachIndexed { i,(lon,lat) ->
                val (x,y)=viewport.project(lon,lat)
                if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            path.close()
            drawPath(path,fill.copy(alpha=.62f))
            drawPath(path,edge.copy(alpha=.9f),style=Stroke(width=1.6f))
        }
        polygon(taiwan)
        islands.forEach(::polygon)
    }
}
