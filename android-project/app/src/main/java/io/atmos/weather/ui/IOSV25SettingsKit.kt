package io.atmos.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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

/** True inset-grouped presentation cells: do not call back into business repositories. */
@Composable
fun IOSV25SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface),content=content)
}

@Composable
fun IOSV25SettingsCell(
    title:String, subtitle:String, icon:ImageVector, iconColor:Color,
    trailing:String="", last:Boolean=false, badgeCount:Int=0, onClick:()->Unit
) {
    val palette=MaterialTheme.colorScheme
    val interactions=remember { MutableInteractionSource() }
    val held by interactions.collectIsPressedAsState()
    val fill by animateColorAsState(
        if(held) { if(palette.background.red > .5f) Color(0xFFE5E5EA) else Color(0xFF2C2C2E) } else Color.Transparent,
        tween(if(held)80 else 250),label="settings-cell-highlight")
    Column {
        Row(Modifier.fillMaxWidth().background(fill)
            .semantics(mergeDescendants=true) {contentDescription=listOf(title,subtitle,trailing).filter {it.isNotBlank()}.joinToString("，")}
            .clickable(interactionSource=interactions,indication=null,role=Role.Button,onClick=onClick)
            .defaultMinSize(minHeight=48.dp)
            .padding(start=15.dp,end=12.dp,top=7.dp,bottom=7.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(iconColor),
                contentAlignment=Alignment.Center) {
                Icon(icon,null,Modifier.size(19.dp),tint=Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                Text(title,color=palette.onSurface,fontSize=16.sp,
                    fontWeight=FontWeight.Medium,lineHeight=21.sp)

            }
            if(badgeCount>0) {
                Box(Modifier.height(20.dp).widthIn(min=22.dp).clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFFFF3B30)).padding(horizontal=6.dp),contentAlignment=Alignment.Center) {
                    Text(if(badgeCount>99) "99+" else "$badgeCount",color=Color.White,fontSize=14.sp)
                }
                Spacer(Modifier.width(6.dp))
            }
            if(trailing.isNotBlank()) {
                Text(trailing,color=palette.onSurfaceVariant,fontSize=14.sp,maxLines=1)
                Spacer(Modifier.width(5.dp))
            }
            Icon(Icons.Rounded.ChevronRight,null,Modifier.size(18.dp),
                tint=Color(0xFFC7C7CC))
        }
        if(!last) Box(Modifier.padding(start=56.dp).fillMaxWidth().height(.5.dp)
            .background(if(palette.background.red > .5f) ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
    }
}
