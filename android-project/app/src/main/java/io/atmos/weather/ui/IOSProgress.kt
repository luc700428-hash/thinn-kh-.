package io.atmos.weather.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Foundation-drawn indicators keep loading states in the same visual system. */
@Composable
fun IOSActivityIndicator(modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.onSurfaceVariant,strokeWidth:Dp=2.dp) {
    val reduce=rememberAppleReduceMotion()
    val phase=if(reduce)0f else {
        val transition=rememberInfiniteTransition(label="活動指示")
        val value by transition.animateFloat(0f,12f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="旋轉刻度")
        value
    }
    Canvas(modifier.defaultMinSize(22.dp,22.dp).semantics {progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate}) {
        val radius=size.minDimension*.42f
        for(index in 0 until 12) {
            val angle=index*Math.PI/6
            val age=(index-phase.toInt()+12)%12
            val opacity=.18f+.82f*(12-age)/12f
            val direction=Offset(cos(angle).toFloat(),sin(angle).toFloat())
            drawLine(color.copy(alpha=color.alpha*opacity),center+direction*(radius*.62f),center+direction*radius,
                strokeWidth.toPx(),StrokeCap.Round)
        }
    }
}

@Composable
fun IOSLinearProgressIndicator(progress:()->Float,modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary,
    trackColor:Color=MaterialTheme.colorScheme.surfaceVariant) {
    val value=progress().takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
    val smooth by animateFloatAsState(value,tween(if(rememberAppleReduceMotion())0 else 120),label="實際進度")
    Canvas(modifier.fillMaxWidth().height(4.dp).semantics {progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)}) {
        drawLine(trackColor,Offset(0f,center.y),Offset(size.width,center.y),size.height,StrokeCap.Round)
        if(smooth>0f)drawLine(color,Offset(0f,center.y),Offset(size.width*smooth,center.y),size.height,StrokeCap.Round)
    }
}

@Composable
fun IOSLinearProgressIndicator(modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary,
    trackColor:Color=MaterialTheme.colorScheme.surfaceVariant) {
    val reduce=rememberAppleReduceMotion()
    val phase=if(reduce).5f else {
        val transition=rememberInfiniteTransition(label="等待資料")
        val value by transition.animateFloat(-.25f,1f,infiniteRepeatable(tween(1200,easing=LinearEasing)),label="進度光帶")
        value
    }
    Canvas(modifier.fillMaxWidth().height(4.dp).semantics {progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate}) {
        drawLine(trackColor,Offset(0f,center.y),Offset(size.width,center.y),size.height,StrokeCap.Round)
        drawLine(color,Offset(size.width*phase.coerceIn(0f,1f),center.y),Offset(size.width*(phase+.25f).coerceIn(0f,1f),center.y),size.height,StrokeCap.Round)
    }
}
