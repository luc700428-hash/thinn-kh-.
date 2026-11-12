package io.atmos.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ringtone-style gallery: tap to preview, confirm to persist, return to cancel. */
@Composable internal fun ThemeColorPage(modifier:Modifier=Modifier,onBack:()->Unit) {
 val context=LocalContext.current
 val token=remember{Any()}
 var draft by remember{mutableStateOf(AppThemeColors.saved)}
 DisposableEffect(token){
  AppThemeColors.preview(token,draft)
  onDispose{AppThemeColors.endPreview(token)}
 }
 androidx.activity.compose.BackHandler(onBack=onBack)
 val p=MaterialTheme.colorScheme
 Column(modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
  Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
   IOS27RoundControl("‹",onBack,fill=p.surfaceVariant,foreground=p.onSurface)
   AppText("主題顏色",modifier=Modifier.weight(1f),color=p.onSurface,fontSize=19.sp,
    fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
   IOS27RoundControl("✓",{
    AppThemeColors.update(context,draft)
    io.atmos.weather.widgets.WidgetSync.refreshCached(context)
    onBack()
   },fill=p.primary,foreground=p.onPrimary)
  }
  AppText("挑選你的主題",color=p.onSurface,fontSize=25.sp,fontWeight=FontWeight.Bold,
   modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
  AppText("點選卡片預覽，按右上角勾號套用。標誌維持薄荷綠。",color=p.onSurfaceVariant,
   fontSize=14.sp,lineHeight=23.sp,modifier=Modifier.padding(start=20.dp,end=20.dp,bottom=18.dp))
  ThemeColorPicker(Modifier.weight(1f),draft){color->draft=color;AppThemeColors.preview(token,color)}
 }
}

@Composable internal fun ThemeColorPicker(modifier:Modifier=Modifier,selected:AppThemeColor=AppThemeColors.value,
 onChoose:((AppThemeColor)->Unit)?=null) {
 val context=LocalContext.current
 val dark=MaterialTheme.colorScheme.background.red<.5f
 LazyVerticalGrid(GridCells.Fixed(2),modifier.fillMaxSize().testTag("theme-color-gallery"),
  contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=workspaceBottomPadding(112.dp)),
  horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  items(AppThemeColor.entries,key={it.id}) {theme->
   val chosen=selected==theme
   val colors=BrandPalette.scheme(theme,dark)
   Column(Modifier.fillMaxWidth().heightIn(min=184.dp).iosJellyPress().clip(RoundedCornerShape(22.dp))
    .background(Brush.linearGradient(listOf(colors.surface,colors.surfaceVariant)))
    .border(if(chosen)2.dp else 1.dp,if(chosen)colors.primary else colors.outlineVariant,RoundedCornerShape(22.dp))
    .semantics{contentDescription="主題顏色：${theme.title}";stateDescription=if(chosen)"已選取" else "未選取"}
    .selectable(chosen,role=Role.RadioButton,onClick={
     if(onChoose!=null)onChoose(theme) else {
      AppThemeColors.update(context,theme);io.atmos.weather.widgets.WidgetSync.refreshCached(context)
     }
    }).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Box(Modifier.fillMaxWidth().height(92.dp)) {
     Box(Modifier.align(Alignment.CenterEnd).size(72.dp).testTag("theme-swatch-${theme.id}")
      .clip(RoundedCornerShape(6.dp)).background(Color(theme.swatchArgb))
      .border(1.dp,colors.outline.copy(alpha=.30f),RoundedCornerShape(6.dp)))
     AppText(if(chosen)"✓ 已選擇" else "預覽",fontSize=14.sp,
      color=if(chosen)colors.onPrimary else colors.onSurface,
      modifier=Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(12.dp))
       .background(if(chosen)colors.primary else colors.surfaceVariant).padding(horizontal=9.dp,vertical=5.dp))
    }
    AppText(theme.title,color=colors.onSurface,fontSize=18.sp,fontWeight=FontWeight.Bold)
    AppText(if(chosen)"目前預覽" else "淺色與深色皆適用",color=colors.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp)
   }
  }
 }
}
