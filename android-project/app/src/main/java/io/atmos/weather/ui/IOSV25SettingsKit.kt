package io.atmos.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

/** True inset-grouped presentation cells: do not call back into business repositories. */
@Composable
fun IOSV25SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val opaque=rememberAppleReduceTransparency()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .thinnBackdrop(opaque,16.dp)
        .background(MaterialTheme.colorScheme.surface.copy(alpha=if(opaque)1f else .94f))
        .thinnSpecularEdge(16.dp),content=content)
}

/** Shared inset navigation bar, with the same optical back control on settings pages. */
@Composable
fun IOSSettingsHeader(title:String,onBack:()->Unit,modifier:Modifier=Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min=58.dp).padding(horizontal=16.dp),contentAlignment=Alignment.Center) {
        IOS27RoundControl("‹",onBack,Modifier.align(Alignment.CenterStart))
        Text(title,color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,
            fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(horizontal=54.dp))
    }
}

@Composable
fun IOSSegmentedControl(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    require(labels.isNotEmpty())
    val palette=MaterialTheme.colorScheme
    val reduce=rememberAppleReduceMotion()
    val response=iosMotionResponse()
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(9999.dp))
        .background(palette.surfaceVariant)) {
        val cell=(maxWidth-8.dp)/labels.size
        val position by animateDpAsState(cell*selected.coerceIn(labels.indices),
            if(reduce)tween(0) else spring(response.damping,response.stiffness),label="分段控制滑動")
        Box(Modifier.padding(4.dp).offset(x=position).width(cell).fillMaxHeight()
            .clip(RoundedCornerShape(9999.dp)).thinnBackdrop(rememberAppleReduceTransparency(),9999.dp)
            .background(Color(0xFF0866CC)).thinnSpecularEdge(9999.dp))
        Row(Modifier.fillMaxSize().padding(4.dp).selectableGroup()) {
            labels.forEachIndexed { index,label ->
                Box(Modifier.weight(1f).fillMaxHeight().iosJellyPress().selectable(
                    selected=index==selected,role=Role.Tab,onClick={onSelect(index)}),contentAlignment=Alignment.Center) {
                    Text(label,color=if(index==selected)Color.White else palette.onSurface,fontSize=14.sp,fontWeight=if(index==selected)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun IOSV25SettingsCell(
    title:String, subtitle:String, icon:ImageVector, iconColor:Color,
    trailing:String="", last:Boolean=false, badgeCount:Int=0, onClick:()->Unit
) {
    val palette=MaterialTheme.colorScheme
    val feedback=io.atmos.weather.haptics.LocalHapticEngine.current
    val interactions=remember { MutableInteractionSource() }
    val held by interactions.collectIsPressedAsState()
    val fill by animateColorAsState(
        if(held) { if(palette.background.red > .5f) Color(0xFFE5E5EA) else Color(0xFF2C2C2E) } else Color.Transparent,
        tween(if(held)80 else 250),label="settings-cell-highlight")
    Column {
        Row(Modifier.fillMaxWidth().iosJellyPress().background(fill)
            .semantics(mergeDescendants=true) {contentDescription=listOf(title,subtitle,trailing).filter {it.isNotBlank()}.joinToString("，")}
            .clickable(interactionSource=interactions,indication=null,role=Role.Button,onClick={
                feedback?.play(io.atmos.weather.haptics.HapticEngine.Event.TAP);onClick()
            })
            .defaultMinSize(minHeight=48.dp)
            .padding(start=15.dp,end=16.dp,top=8.dp,bottom=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(iconColor),
                contentAlignment=Alignment.Center) {
                Icon(icon,null,Modifier.size(22.dp),tint=Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                Text(title,color=palette.onSurface,fontSize=17.sp,
                    fontWeight=FontWeight.Normal,lineHeight=22.sp)

            }
            if(badgeCount>0) {
                Box(Modifier.height(20.dp).widthIn(min=22.dp).clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFFFF3B30)).padding(horizontal=6.dp),contentAlignment=Alignment.Center) {
                    Text(if(badgeCount>99) "99+" else "$badgeCount",color=Color.White,fontSize=14.sp)
                }
                Spacer(Modifier.width(6.dp))
            }
            if(trailing.isNotBlank()) {
                Text(trailing,color=palette.onSurfaceVariant,fontSize=17.sp,maxLines=1)
                Spacer(Modifier.width(9.dp))
            }
            SettingsChevron()
        }
        if(!last) Box(Modifier.padding(start=56.dp).fillMaxWidth().height(.5.dp)
            .background(if(palette.background.red > .5f) ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
    }
}

/** A slim chevron with the reference's consistent stroke. */
@Composable
fun SettingsChevron(modifier:Modifier=Modifier) {
    Canvas(modifier.size(width=8.dp,height=14.dp)) {
        val stroke=1.6.dp.toPx();val tint=Color(0xFFC7C7CC)
        drawLine(tint,Offset(stroke/2,stroke/2),Offset(size.width-stroke/2,size.height/2),stroke,StrokeCap.Round)
        drawLine(tint,Offset(size.width-stroke/2,size.height/2),Offset(stroke/2,size.height-stroke/2),stroke,StrokeCap.Round)
    }
}
