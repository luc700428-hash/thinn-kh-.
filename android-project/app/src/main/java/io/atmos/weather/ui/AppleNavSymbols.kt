package io.atmos.weather.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Original outline glyphs, consistently weighted. No SF Symbols are bundled. */
internal fun appGlyph(name:String, body:ImageVector.Builder.()->Unit):ImageVector =
    ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply(body).build()
internal fun ImageVector.Builder.glyphStroke(body:PathBuilder.()->Unit) {
    path(fill=null,stroke=SolidColor(Color.White),strokeLineWidth=1.7f,
        strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round,pathBuilder=body)
}
internal fun PathBuilder.glyphCircle(x:Float,y:Float,r:Float) {
    val k=r*.5522848f
    moveTo(x+r,y);curveTo(x+r,y+k,x+k,y+r,x,y+r)
    curveTo(x-k,y+r,x-r,y+k,x-r,y);curveTo(x-r,y-k,x-k,y-r,x,y-r)
    curveTo(x+k,y-r,x+r,y-k,x+r,y);close()
}
object AppleNavSymbols {
    val weather by lazy {appGlyph("weather") {glyphStroke {
        glyphCircle(8f,8f,3.4f)
        moveTo(8f,1.3f);lineTo(8f,2.4f);moveTo(1.3f,8f);lineTo(2.4f,8f)
        moveTo(3.2f,3.2f);lineTo(4f,4f);moveTo(12f,4f);lineTo(12.8f,3.2f)
        moveTo(3.2f,12.8f);lineTo(4f,12f)
        moveTo(8f,20.6f);curveTo(3.4f,20.6f,3.1f,14f,7.3f,13.5f)
        curveTo(8.8f,7.7f,17.1f,8.1f,18.2f,13.4f)
        curveTo(23.7f,12.6f,24.3f,20.6f,18.7f,20.6f);lineTo(8f,20.6f);close()
    }}}
    val quake by lazy {appGlyph("seismic-wave") {glyphStroke {
        moveTo(1.8f,12f);lineTo(5.4f,12f);lineTo(8.1f,5.2f)
        lineTo(11.5f,19.4f);lineTo(15f,8.6f);lineTo(17.5f,12f);lineTo(22.2f,12f)
    }}}
    val tools by lazy {appGlyph("tools-grid") {glyphStroke {
        for(x in listOf(3f,14f)) for(y in listOf(3f,14f)) {
            moveTo(x+1.6f,y);lineTo(x+5.4f,y);quadTo(x+7f,y,x+7f,y+1.6f)
            lineTo(x+7f,y+5.4f);quadTo(x+7f,y+7f,x+5.4f,y+7f)
            lineTo(x+1.6f,y+7f);quadTo(x,y+7f,x,y+5.4f)
            lineTo(x,y+1.6f);quadTo(x,y,x+1.6f,y);close()
        }
    }}}
    val settings by lazy {appGlyph("gear") {glyphStroke {
        for(i in 0 until 64) {
            val a=(i*Math.PI/32-Math.PI/2).toFloat()
            val r=if(i%8 in 1..4) 10f else 8.25f
            val x=12f+cos(a)*r;val y=12f+sin(a)*r
            if(i==0) moveTo(x,y) else lineTo(x,y)
        };close();glyphCircle(12f,12f,4f)
    }}}
}
