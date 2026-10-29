package io.atmos.weather.typhoon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawDirectedTrack(a:Offset,b:Offset,color:Color,forecast:Boolean=true) {
    drawLine(Color.Black.copy(alpha=.35f),a,b,4.8.dp.toPx(),StrokeCap.Round)
    drawLine(color,a,b,2.8.dp.toPx(),StrokeCap.Round,
        pathEffect=if(forecast)PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(),5.dp.toPx())) else null)
    val points=TrackArrowGeometry.head(a.x,a.y,b.x,b.y,4.3.dp.toPx())
    if(points.size==3)drawPath(Path().apply {moveTo(points[0].x,points[0].y);lineTo(points[1].x,points[1].y);lineTo(points[2].x,points[2].y);close()},color)
}
internal fun DrawScope.drawTrackTime(text:String,at:Offset,cachedPaint:android.graphics.Paint?=null) {
    val paint=cachedPaint?:android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color=android.graphics.Color.WHITE;textSize=12*density
        typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
        setShadowLayer(3*density,0f,density,android.graphics.Color.BLACK)
    }
    val w=paint.measureText(text)
    val x=(at.x+9*density).coerceIn(5*density,(size.width-w-5*density).coerceAtLeast(5*density))
    val y=(at.y-10*density).coerceIn(18*density,(size.height-26*density).coerceAtLeast(18*density))
    if(at.x>=0 && at.y>=0 && at.x<size.width && at.y<size.height)drawContext.canvas.nativeCanvas.drawText(text,x,y,paint)
}
