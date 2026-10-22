package io.atmos.weather.next.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.next.domain.WeatherScene
import kotlin.math.*

/** Original Android rendering system inspired by optical principles, not Apple private APIs. */
object NextPalette {
    val deep = Color(0xFF061D34)
    val light = Color(0xFFFCFFFF)
    val muted = Color(0xFFCEE8F2)
    val aqua = Color(0xFF9EEBFF)
}
object NextPhysics {
    val touch: SpringSpec<Float> = spring(dampingRatio = .79f, stiffness = 530f)
    val settle: SpringSpec<Float> = spring(dampingRatio = .86f, stiffness = 250f)
    fun clampUnit(value:Float):Float = if(value.isFinite()) value.coerceIn(0f,1f) else .5f
}
@Composable
fun NextText(value:String,modifier:Modifier=Modifier,size:Int=16,color:Color=NextPalette.light,
    heavy:Boolean=false,maxLines:Int=Int.MAX_VALUE) {
    BasicText(value,modifier=modifier,style=TextStyle(color=color,fontSize=size.sp,
        fontWeight=if(heavy) FontWeight.SemiBold else FontWeight.Normal,lineHeight=(size+5).sp),
        maxLines=maxLines,overflow=TextOverflow.Ellipsis)
}
@Composable
fun LiquidPanel(modifier:Modifier=Modifier,corner:Dp=26.dp,onClick:(()->Unit)?=null,
    content:@Composable ColumnScope.()->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .976f else 1f,animationSpec=NextPhysics.touch,label="liquidPress")
    val shape=RoundedCornerShape(corner)
    val fill=Brush.linearGradient(listOf(Color.White.copy(alpha=.19f),Color(0xFF87C6E1).copy(alpha=.075f),Color(0xFF244560).copy(alpha=.16f)))
    val rim=Brush.linearGradient(listOf(Color.White.copy(alpha=.57f),Color.White.copy(alpha=.10f),Color(0xFFB2DFFD).copy(alpha=.38f)))
    val touch=if(onClick!=null) Modifier.clickable(interactionSource=interaction,indication=null,onClick=onClick) else Modifier
    Column(modifier.graphicsLayer{scaleX=scale;scaleY=scale}
        .clip(shape).background(fill,shape).border(1.dp,rim,shape)
        .then(touch).padding(18.dp),content=content)
}
@Composable
fun LiquidAction(label:String,modifier:Modifier=Modifier,selected:Boolean=false,onClick:()->Unit) {
    val tone=if(selected) Color(0xFFB5F2FE).copy(alpha=.23f) else Color.White.copy(alpha=.11f)
    val shape=RoundedCornerShape(18.dp)
    Box(modifier.clip(shape).background(tone,shape)
        .border(1.dp,Color.White.copy(alpha=if(selected).46f else .20f),shape)
        .clickable(onClick=onClick).padding(horizontal=16.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
        NextText(label,size=14,heavy=selected)
    }
}
@Composable
fun AtmosphericBackground(scene:WeatherScene?,reduceMotion:Boolean,modifier:Modifier=Modifier) {
    val motion=if(reduceMotion) .38f else {
        val transition=rememberInfiniteTransition(label="atmosphericSky")
        val drift by transition.animateFloat(initialValue=0f,targetValue=1f,
            animationSpec=infiniteRepeatable(tween(18000,easing=LinearEasing),RepeatMode.Reverse),label="lightDrift")
        drift
    }
    val storm=scene in setOf(WeatherScene.THUNDERSTORM,WeatherScene.HAIL,WeatherScene.DUST_STORM)
    val rain=scene in setOf(WeatherScene.RAIN,WeatherScene.DRIZZLE,WeatherScene.FREEZING_RAIN,WeatherScene.SLEET)
    val colors=when {
        storm->listOf(Color(0xFF071627),Color(0xFF1D344D),Color(0xFF0B2037))
        rain->listOf(Color(0xFF0A2337),Color(0xFF28536B),Color(0xFF11334A))
        scene==WeatherScene.CLEAR->listOf(Color(0xFF0C4168),Color(0xFF1686A6),Color(0xFF0C304F))
        else->listOf(Color(0xFF082B48),Color(0xFF386E89),Color(0xFF122D46))
    }
    Canvas(modifier) {
        drawRect(brush=Brush.verticalGradient(colors))
        val lightCenter=Offset(size.width*(.73f+motion*.07f),size.height*(.21f+motion*.02f))
        drawCircle(brush=Brush.radialGradient(listOf(Color(0xFFA3E5FF).copy(alpha=if(storm).12f else .26f),Color.Transparent),center=lightCenter,radius=size.width*.83f),
            center=lightCenter,radius=size.width*.83f)
        // Keep atmosphere quiet in reduce-motion mode; never imply live rain or an active warning.
        if(!reduceMotion && rain) for(i in 0..28) {
            val x=(i*79.31f % size.width)
            val y=((i*107.27f+motion*size.height*.42f)%size.height)
            drawLine(Color(0xFFB4DAF3).copy(alpha=.21f),Offset(x,y),Offset(x-9f,y+25f),strokeWidth=1.5f)
        }
        if(!reduceMotion && scene==WeatherScene.CLEAR) for(i in 0..8) {
            val a=i*6.28318f/9f
            val origin=lightCenter
            drawCircle(Color.White.copy(alpha=.08f),radius=2.5f,center=Offset(origin.x+cos(a)*size.width*.22f,origin.y+sin(a)*size.width*.22f))
        }
    }
}
