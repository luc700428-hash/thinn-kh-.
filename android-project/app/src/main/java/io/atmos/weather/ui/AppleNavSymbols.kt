package io.atmos.weather.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Four original, licensable, iOS-weighted vector glyphs. No SF Symbols are bundled. */
object AppleNavSymbols {
    val weather:ImageVector by lazy { vector("sun") {
        path(fill=SolidColor(Color.White)) {
            moveTo(12f,6.4f); curveTo(8.9f,6.4f,6.4f,8.9f,6.4f,12f)
            curveTo(6.4f,15.1f,8.9f,17.6f,12f,17.6f)
            curveTo(15.1f,17.6f,17.6f,15.1f,17.6f,12f)
            curveTo(17.6f,8.9f,15.1f,6.4f,12f,6.4f);close()
            moveTo(11.1f,0.8f);lineTo(12.9f,0.8f);lineTo(12.9f,4f);lineTo(11.1f,4f);close()
            moveTo(11.1f,20f);lineTo(12.9f,20f);lineTo(12.9f,23.2f);lineTo(11.1f,23.2f);close()
            moveTo(.8f,11.1f);lineTo(4f,11.1f);lineTo(4f,12.9f);lineTo(.8f,12.9f);close()
            moveTo(20f,11.1f);lineTo(23.2f,11.1f);lineTo(23.2f,12.9f);lineTo(20f,12.9f);close()
        }
    } }
    val quake:ImageVector by lazy { vector("seismic") {
        path(fill=SolidColor(Color.White)) {
            moveTo(1f,7f);curveTo(4f,4.5f,6f,9.5f,9f,7f)
            curveTo(12f,4.5f,14f,9.5f,17f,7f);curveTo(19f,5.2f,21f,6.3f,23f,7f)
            lineTo(23f,9f);curveTo(20f,6.5f,19f,11.5f,16f,9f)
            curveTo(13f,6.5f,11f,11.5f,8f,9f);curveTo(5f,6.5f,4f,11.5f,1f,9f);close()
            moveTo(1f,14f);curveTo(4f,11.5f,6f,16.5f,9f,14f)
            curveTo(12f,11.5f,14f,16.5f,17f,14f)
            curveTo(19f,12.2f,21f,13.3f,23f,14f);lineTo(23f,16f)
            curveTo(20f,13.5f,19f,18.5f,16f,16f)
            curveTo(13f,13.5f,11f,18.5f,8f,16f)
            curveTo(5f,13.5f,4f,18.5f,1f,16f);close()
        }
    } }
    val tools:ImageVector by lazy { vector("tools") {
        path(fill=SolidColor(Color.White)) {
            moveTo(2f,2f);lineTo(10.5f,2f);lineTo(10.5f,10.5f);lineTo(2f,10.5f);close()
            moveTo(13.5f,2f);lineTo(22f,2f);lineTo(22f,10.5f);lineTo(13.5f,10.5f);close()
            moveTo(2f,13.5f);lineTo(10.5f,13.5f);lineTo(10.5f,22f);lineTo(2f,22f);close()
            moveTo(13.5f,13.5f);lineTo(22f,13.5f);lineTo(22f,22f);lineTo(13.5f,22f);close()
        }
    } }
    val settings:ImageVector by lazy { vector("adjust") {
        path(fill=SolidColor(Color.White)) {
            moveTo(3f,5f);lineTo(21f,5f);lineTo(21f,7f);lineTo(3f,7f);close()
            moveTo(3f,11f);lineTo(21f,11f);lineTo(21f,13f);lineTo(3f,13f);close()
            moveTo(3f,17f);lineTo(21f,17f);lineTo(21f,19f);lineTo(3f,19f);close()
        }
        path(fill=SolidColor(Color(0xFF5AC8FA))) {
            moveTo(6f,3f);lineTo(9f,3f);lineTo(9f,9f);lineTo(6f,9f);close()
            moveTo(15f,9f);lineTo(18f,9f);lineTo(18f,15f);lineTo(15f,15f);close()
            moveTo(9f,15f);lineTo(12f,15f);lineTo(12f,21f);lineTo(9f,21f);close()
        }
    } }
    private fun vector(name:String, content:Builder.()->Unit):ImageVector =
        Builder(name=name,defaultWidth=24.dp,defaultHeight=24.dp,
            viewportWidth=24f,viewportHeight=24f).apply(content).build()
}
