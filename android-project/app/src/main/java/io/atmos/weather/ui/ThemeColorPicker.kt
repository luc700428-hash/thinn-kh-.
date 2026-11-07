package io.atmos.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ThemeColorPicker() {
    val context=LocalContext.current
    val palette=MaterialTheme.colorScheme
    val selected=AppThemeColors.value
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        AppText("主題顏色",fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
        AppText("套用至所有頁面、Dock 與小工具。品牌標誌維持綠色。",
            fontSize=14.sp,lineHeight=23.sp,color=palette.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            AppThemeColor.entries.forEach {theme->
                val chosen=theme==selected
                Column(Modifier.widthIn(min=68.dp).iosJellyPress()
                    .semantics{contentDescription="主題顏色：${theme.title}"}
                    .selectable(chosen,role=Role.RadioButton,onClick={
                        if(!chosen) {
                            AppThemeColors.update(context,theme)
                            io.atmos.weather.widgets.WidgetSync.refreshCached(context)
                        }
                    }).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(theme.primaryArgb))
                        .border(if(chosen)3.dp else 1.dp,if(chosen)palette.onSurface else palette.outline.copy(alpha=.4f),CircleShape),
                        contentAlignment=Alignment.Center) {
                        if(chosen)AppText("✓",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
                    }
                    AppText(theme.title+(if(theme==AppThemeColor.FOREST)" · 預設" else ""),
                        fontSize=14.sp,color=palette.onSurface,fontWeight=if(chosen)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
