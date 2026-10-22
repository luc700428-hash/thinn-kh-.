package io.atmos.weather.next.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original Android controls with iPhone-like geometry; no copied Apple assets. */
object IosColor {
    val background=Color(0xFF000000)
    val group=Color(0xFF1C1C1E)
    val divider=Color(0xFF38383A)
    val muted=Color(0xFF98989F)
    val secondary=Color(0xFFB7B7BD)
    val blue=Color(0xFF0A84FF)
    val orange=Color(0xFFFF9F0A)
    val green=Color(0xFF30D158)
}
@Composable
fun IosText(s:String,size:Int=16,weight:FontWeight=FontWeight.Normal,color:Color=Color.White,
    modifier:Modifier=Modifier) {
    BasicText(s,modifier,style=TextStyle(color=color,fontSize=size.sp,fontWeight=weight,
        lineHeight=(size+4).sp))
}
@Composable
fun IosGroup(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(IosColor.group),content=content)
}
@Composable
fun IosSeparator() {
    Spacer(Modifier.fillMaxWidth().padding(start=50.dp).height(0.5.dp).background(IosColor.divider))
}
@Composable
fun IosSwitch(value:Boolean,onChange:(Boolean)->Unit,enabled:Boolean=true) {
    val position by animateFloatAsState(if(value) 1f else 0f,
        animationSpec=spring(dampingRatio=.82f,stiffness=650f),label="iosSwitch")
    val fill=if(value) IosColor.green else Color(0xFF454548)
    Box(Modifier.width(51.dp).height(31.dp).clip(RoundedCornerShape(50))
        .background(fill).clickable(enabled=enabled){onChange(!value)}.padding(2.dp)) {
        Box(Modifier.offset(x=(20*position).dp).size(27.dp).clip(RoundedCornerShape(50))
            .background(Color.White).graphicsLayer { shadowElevation=2.dp.toPx() })
    }
}
@Composable
fun IosSettingRow(icon:String,title:String,detail:String?=null,checked:Boolean?=null,
    onToggle:((Boolean)->Unit)?=null,onClick:(()->Unit)?=null,accent:Color=IosColor.blue) {
    Row(Modifier.fillMaxWidth().heightIn(min=49.dp).then(if(onClick!=null)Modifier.clickable{onClick()} else Modifier)
        .padding(start=14.dp,end=14.dp,top=9.dp,bottom=9.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(accent),contentAlignment=Alignment.Center) {
            IosText(icon,17,FontWeight.SemiBold)
        }
        Spacer(Modifier.width(13.dp))
        IosText(title,16,modifier=Modifier.weight(1f))
        if(checked!=null && onToggle!=null) IosSwitch(checked,onToggle)
        else {
            if(detail!=null) IosText(detail,14,color=IosColor.muted)
            if(onClick!=null) {Spacer(Modifier.width(8.dp));IosText("›",25,color=Color(0xFF66666B))}
        }
    }
}
@Composable
fun WeatherGlassCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.clip(RoundedCornerShape(17.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF47759D).copy(alpha=.67f),
            Color(0xFF304F73).copy(alpha=.60f))))
        .border(0.6.dp,Color.White.copy(alpha=.14f),RoundedCornerShape(17.dp))
        .padding(15.dp),content=content)
}
