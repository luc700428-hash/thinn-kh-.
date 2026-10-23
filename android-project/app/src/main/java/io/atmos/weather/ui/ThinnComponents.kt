package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine

private val bg = ThinnOpticalMaterial.surface
private val stroke = Color.White.copy(alpha=.16f)
private val dim = Color(0xFFD0DFED)

@Composable
fun ThinnSection(label:String, modifier:Modifier=Modifier, content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(label,fontSize=14.sp,fontWeight=FontWeight.Medium,color=dim,
            modifier=Modifier.padding(start=15.dp,bottom=9.dp,top=14.dp))
        AppleGlassCard(modifier=Modifier.fillMaxWidth(),radius=ThinnDesignTokens.radiusCard,
            contentPadding=PaddingValues(0.dp),content=content)
    }
}

@Composable
fun ThinnRow(title:String,subtitle:String="",icon:ImageVector?=null,destructive:Boolean=false,
    trailing:(@Composable ()->Unit)?=null,onClick:(()->Unit)?=null) {
    val haptic=LocalHapticEngine.current
    val textColor=if(destructive) ThinnDesignTokens.critical else Color.White
    Row(Modifier.fillMaxWidth().heightIn(min=58.dp)
        .then(if(onClick!=null) Modifier.clickable(role=Role.Button) {
            haptic?.play(HapticEngine.Event.TAP)
            onClick()
        } else Modifier)
        .padding(horizontal=15.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(icon!=null) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                .background(ThinnDesignTokens.accent.copy(alpha=.19f)),contentAlignment=Alignment.Center) {
                Icon(icon,null,Modifier.size(20.dp),tint=ThinnDesignTokens.accent)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title,color=textColor,fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.Medium)
            if(subtitle.isNotBlank()) Text(subtitle,color=dim,fontSize=14.sp,lineHeight=17.sp)
        }
        when {
            trailing!=null -> trailing()
            onClick!=null -> Icon(Icons.Rounded.ChevronRight,null,tint=dim.copy(alpha=.75f),modifier=Modifier.size(20.dp))
        }
    }
}

@Composable
fun ThinnProgressLine(progress:Float?,label:String,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    val bounded=progress?.coerceIn(0f,1f)
    val smooth by animateFloatAsState(bounded ?: 0f,animationSpec=tween(if(reduce)0 else 190,easing=AppleMotion.fluid),label="真實進度")
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=Color.White,fontSize=15.sp)
        if (bounded == null) {
            LinearProgressIndicator(modifier=Modifier.fillMaxWidth().height(5.dp)
                .clip(CircleShape),color=ThinnDesignTokens.accent,trackColor=Color.White.copy(alpha=.16f))
        } else {
            LinearProgressIndicator(progress={smooth},modifier=Modifier.fillMaxWidth().height(5.dp)
                .clip(CircleShape),color=ThinnDesignTokens.accent,trackColor=Color.White.copy(alpha=.16f))
        }
    }
}
