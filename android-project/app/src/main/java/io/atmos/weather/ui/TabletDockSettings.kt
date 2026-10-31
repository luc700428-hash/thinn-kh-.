package io.atmos.weather.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class TabletDockPosition(val id:String,val title:String) {
    RIGHT("right","右側"),BOTTOM("bottom","底部"),LEFT("left","左側"),TOP("top","上方");
    val vertical get()=this==RIGHT||this==LEFT
    companion object {fun fromId(id:String?)=entries.firstOrNull{it.id==id}?:RIGHT}
}
internal object TabletDockPreference {
    const val KEY="tablet_dock_position_v1"
    fun read(context:Context)=TabletDockPosition.fromId(context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getString(KEY,null))
    fun save(context:Context,position:TabletDockPosition){context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).edit().putString(KEY,position.id).apply()}
}
@Composable internal fun rememberTabletDockPosition():TabletDockPosition {
    val context=LocalContext.current
    var current by remember(context){mutableStateOf(TabletDockPreference.read(context))}
    DisposableEffect(context) {
        val prefs=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,key->if(key==TabletDockPreference.KEY||key==null)current=TabletDockPreference.read(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        current=TabletDockPreference.read(context)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return current
}

internal data class TabletDockPlacement(val position:TabletDockPosition,val dock:Rect,val content:Rect)
/** All dimensions are available dp. A reserved lane keeps the dock outside readable content. */
internal object TabletDockGeometry {
    fun place(pane:Rect,fontScale:Float,preferred:TabletDockPosition):TabletDockPlacement {
        val scale=fontScale.takeIf{it.isFinite()}?.coerceIn(1f,3f)?:1f
        val width=pane.width.coerceAtLeast(1f);val height=pane.height.coerceAtLeast(1f)
        val horizontalHeight=maxOf(68f,54f+24f*scale)
        val verticalWidth=maxOf(76f,48f+22f*scale)
        val minVerticalHeight=4*maxOf(58f,41f+24f*scale)+10f+24f
        val position=if(preferred.vertical&&(width-verticalWidth-24f<300f||height<minVerticalHeight))TabletDockPosition.BOTTOM else preferred
        val margin=12f.coerceAtMost(minOf(width,height)*.04f)
        val dockWidth=if(position.vertical)verticalWidth.coerceAtMost(width-margin*2) else minOf(560f,width-margin*2)
        val dockHeight=if(position.vertical)minOf(4*maxOf(64f,47f+24f*scale)+10f,height-margin*2) else horizontalHeight.coerceAtMost(height-margin*2)
        val x=when(position){TabletDockPosition.LEFT->pane.left+margin;TabletDockPosition.RIGHT->pane.right-margin-dockWidth;else->pane.left+(width-dockWidth)/2}
        val y=when(position){TabletDockPosition.TOP->pane.top+margin;TabletDockPosition.BOTTOM->pane.bottom-margin-dockHeight;else->pane.top+(height-dockHeight)/2}
        val dock=Rect(x,y,x+dockWidth,y+dockHeight)
        val lane=if(position.vertical)dockWidth+margin*2 else dockHeight+margin*2
        val content=when(position){
            TabletDockPosition.RIGHT->Rect(pane.left,pane.top,(pane.right-lane).coerceAtLeast(pane.left+1),pane.bottom)
            TabletDockPosition.LEFT->Rect((pane.left+lane).coerceAtMost(pane.right-1),pane.top,pane.right,pane.bottom)
            TabletDockPosition.TOP->Rect(pane.left,(pane.top+lane).coerceAtMost(pane.bottom-1),pane.right,pane.bottom)
            TabletDockPosition.BOTTOM->Rect(pane.left,pane.top,pane.right,(pane.bottom-lane).coerceAtLeast(pane.top+1))
        }
        return TabletDockPlacement(position,dock,content)
    }
    fun fit(rect:Rect,pane:Rect):Rect {
        val width=rect.width.coerceIn(1f,pane.width.coerceAtLeast(1f));val height=rect.height.coerceIn(1f,pane.height.coerceAtLeast(1f))
        val x=rect.left.coerceIn(pane.left,(pane.right-width).coerceAtLeast(pane.left))
        val y=rect.top.coerceIn(pane.top,(pane.bottom-height).coerceAtLeast(pane.top))
        return Rect(x,y,x+width,y+height)
    }
}

@Composable internal fun TabletDockSettingsPage(onBack:()->Unit) {
    val context=LocalContext.current;val current=rememberTabletDockPosition();val p=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader("平板 Dock 位置",onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            AppText("把 Dock 放在順手的位置",fontSize=26.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,color=p.onSurface)
            AppText("位置調整會立即套用，並替頁面保留空間，方便閱讀與操作。",fontSize=16.sp,lineHeight=26.sp,color=p.onSurfaceVariant)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).selectableGroup()) {
                TabletDockPosition.entries.forEachIndexed {index,position->
                    val selected=current==position
                    val icon=when(position){TabletDockPosition.RIGHT->Icons.Rounded.KeyboardArrowRight;TabletDockPosition.LEFT->Icons.Rounded.KeyboardArrowLeft;TabletDockPosition.TOP->Icons.Rounded.KeyboardArrowUp;TabletDockPosition.BOTTOM->Icons.Rounded.KeyboardArrowDown}
                    Row(Modifier.fillMaxWidth().background(if(selected)p.primaryContainer else p.surface).iosJellyPress()
                        .selectable(selected,role=Role.RadioButton,onClick={TabletDockPreference.save(context,position)})
                        .heightIn(min=68.dp).padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(icon,null,Modifier.size(28.dp),tint=if(selected)p.onPrimaryContainer else p.primary)
                        AppText(position.title+(if(position==TabletDockPosition.RIGHT)" · 預設" else ""),fontSize=17.sp,lineHeight=28.sp,
                            fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,color=if(selected)p.onPrimaryContainer else p.onSurface,
                            modifier=Modifier.weight(1f).padding(start=14.dp))
                        if(selected)Icon(Icons.Rounded.CheckCircle,"已選取",Modifier.size(24.dp),tint=p.onPrimaryContainer)
                    }
                    if(index<TabletDockPosition.entries.lastIndex)GlassDivider()
                }
            }
            AppText("適用於平板與展開的折疊畫面。手機維持底部 Dock；視窗較窄或較矮時會暫用底部，放大後恢復你選的位置。",fontSize=14.sp,lineHeight=24.sp,color=p.onSurfaceVariant)
        }
    }
}
