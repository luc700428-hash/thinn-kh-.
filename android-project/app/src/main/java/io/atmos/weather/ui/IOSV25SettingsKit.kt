package io.atmos.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
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
        .thinnGlass(opaque,16.dp,MaterialTheme.colorScheme.surface,clear=.58f,refract=false,frostScale=1.5f)
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

/** The Liquid Glass segmented control; see [LiquidSegmented]. */
@Composable
fun IOSSegmentedControl(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) =
    LiquidSegmented(labels,selected,onSelect,modifier)

@Composable
fun IOSV25SettingsCell(
    title:String, subtitle:String, icon:ImageVector, iconColor:Color,
    trailing:String="", last:Boolean=false, badgeCount:Int=0, selected:Boolean=false,onClick:()->Unit
) {
    val palette=MaterialTheme.colorScheme
    val feedback=io.atmos.weather.haptics.LocalHapticEngine.current
    val interactions=remember { MutableInteractionSource() }
    val held by interactions.collectIsPressedAsState()
    val fill by animateColorAsState(
        if(held) { if(palette.background.red > .5f) Color(0xFFE5E5EA) else brandColor(0xFF2B2D2C) } else if(selected)palette.primaryContainer else Color.Transparent,
        tween(if(held)80 else 250),label="settings-cell-highlight")
    Column {
        Row(Modifier.fillMaxWidth().iosJellyPress().background(fill)
            .semantics(mergeDescendants=true) {contentDescription=listOf(title,subtitle,trailing).filter {it.isNotBlank()}.joinToString("，");this.selected=selected}
            .clickable(interactionSource=interactions,indication=null,role=Role.Button,onClick={
                feedback?.play(io.atmos.weather.haptics.HapticEngine.Event.TAP);onClick()
            })
            .defaultMinSize(minHeight=48.dp)
            .padding(start=15.dp,end=16.dp,top=8.dp,bottom=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            IOSIconTile(icon,iconColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                Text(title,modifier=Modifier.fillMaxWidth(),color=palette.onSurface,fontSize=17.sp,
                    fontWeight=FontWeight.Normal,lineHeight=26.sp)

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

/**
 * The iOS Settings icon: a continuous-corner square in one system colour, lit from the top (a
 * soft vertical gradient and a hairline highlight), with a white filled glyph about 62% of the tile.
 */
@Composable
fun IOSIconTile(icon:ImageVector,color:Color,size:androidx.compose.ui.unit.Dp=29.dp) {
    val top=Color(color.red+(1-color.red)*.16f,color.green+(1-color.green)*.16f,color.blue+(1-color.blue)*.16f)
    val bottom=Color(color.red*.9f,color.green*.9f,color.blue*.9f)
    val shape=RoundedCornerShape(size*.235f)
    Box(Modifier.size(size).clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top,bottom)))
        .border(.5.dp,androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha=.38f),Color.White.copy(alpha=.04f))),shape),
        contentAlignment=Alignment.Center) {
        Icon(icon,null,Modifier.size(size*.62f),tint=Color.White)
    }
}

/** The iOS system colours used by the Settings icons, so every list reads the same way. */
object IOSColors {
    val blue=Color(0xFF0A84FF);val green=Color(0xFF34C759);val orange=Color(0xFFFF9500);val red=Color(0xFFFF3B30)
    val pink=Color(0xFFFF2D55);val purple=Color(0xFFAF52DE);val indigo=Color(0xFF5856D6);val teal=Color(0xFF30B0C7)
    val cyan=Color(0xFF32ADE6);val mint=Color(0xFF00C7BE);val yellow=Color(0xFFFFCC00);val brown=Color(0xFFA2845E)
    val gray=Color(0xFF8E8E93);val graphite=Color(0xFF3A3A3C)
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
