package io.atmos.weather.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.*

/** A geographic north-up dial, not a claim that the phone's physical heading was measured. */
@Composable fun BearingCompass(degrees:Double?,label:String,modifier:Modifier=Modifier) {
    val palette=MaterialTheme.colorScheme;val reduce=rememberAppleReduceMotion()
    val angle=remember{Animatable(degrees?.toFloat()?:0f)}
    LaunchedEffect(degrees,reduce) {
        val target=degrees?.takeIf{it.isFinite()}?.toFloat()?:return@LaunchedEffect
        val delta=((target-angle.value)%360+540)%360-180
        if(reduce)angle.snapTo(angle.value+delta) else angle.animateTo(angle.value+delta,spring(.82f,160f))
    }
    Canvas(modifier.semantics{contentDescription="$label ${degrees?.roundToInt()?.let{"$it 度"}?:"未提供"}，正北在上"}) {
        val center=Offset(size.width/2,size.height/2);val r=minOf(size.width,size.height)*.36f
        drawCircle(palette.primary.copy(alpha=.08f),r,center)
        drawCircle(palette.outlineVariant,r,center,style=Stroke(density))
        repeat(24){i->val a=i*PI/12;val outside=r;val inside=r-if(i%6==0)8*density else 3*density
            drawLine(palette.onSurfaceVariant.copy(alpha=.6f),center+Offset((sin(a)*inside).toFloat(),(-cos(a)*inside).toFloat()),
                center+Offset((sin(a)*outside).toFloat(),(-cos(a)*outside).toFloat()),density)}
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=palette.onSurface.toArgbCompat();textSize=12*density;textAlign=Paint.Align.CENTER}
        listOf("北","東","南","西").forEachIndexed{i,text->val a=i*PI/2;val at=center+Offset((sin(a)*(r+15*density)).toFloat(),(-cos(a)*(r+15*density)).toFloat())
            drawContext.canvas.nativeCanvas.drawText(text,at.x,at.y+4*density,paint)}
        if(degrees!=null) {
            val a=Math.toRadians(angle.value.toDouble());val tip=center+Offset((sin(a)*r*.75).toFloat(),(-cos(a)*r*.75).toFloat())
            val side=Offset((cos(a)*r*.13).toFloat(),(sin(a)*r*.13).toFloat());val back=center-Offset((sin(a)*r*.35).toFloat(),(-cos(a)*r*.35).toFloat())
            drawPath(Path().apply{moveTo(tip.x,tip.y);lineTo(back.x+side.x,back.y+side.y);lineTo(center.x,center.y);lineTo(back.x-side.x,back.y-side.y);close()},palette.primary)
            drawCircle(palette.primary,3.5f*density,center)
        }
    }
}
private fun androidx.compose.ui.graphics.Color.toArgbCompat()=android.graphics.Color.argb((alpha*255).roundToInt(),(red*255).roundToInt(),(green*255).roundToInt(),(blue*255).roundToInt())
