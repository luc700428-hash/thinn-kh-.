package io.atmos.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherCity
import kotlin.math.max

internal data class WorkspaceLayout(val width:Float,val height:Float,val fontScale:Float,
    val fold:WindowFold?=null,val device:DeviceKind=DeviceKind.TABLET) {
    val book=fold?.let{it.separating&&it.vertical&&it.bounds.left>=200&&width-it.bounds.right>=260}==true
    val tabletop=fold?.let{it.separating&&!it.vertical&&it.bounds.top>=180&&height-it.bounds.bottom>=200}==true
    val loneVertical=fold?.let{it.separating&&it.vertical&&!book}==true
    val loneHorizontal=fold?.let{it.separating&&!it.vertical&&!tabletop}==true
    val paneStart=if(loneVertical&&width-fold!!.bounds.right>fold.bounds.left)fold.bounds.right+6 else 0f
    val paneEnd=if(loneVertical&&paneStart==0f)width-fold!!.bounds.left+6 else 0f
    val paneTop=if(loneHorizontal&&height-fold!!.bounds.bottom>fold.bounds.top)fold.bounds.bottom+6 else 0f
    val paneBottom=if(loneHorizontal&&paneTop==0f)height-fold!!.bounds.top+6 else 0f
    val tablet=(device!=DeviceKind.PHONE&&width>=600&&height>=280)||fold?.separating==true
    val sidebar=book||(!tabletop&&!loneVertical&&!loneHorizontal&&tablet&&width>=max(1000f,940f*fontScale.coerceIn(1f,1.5f))&&height>=480)
    val sidebarWidth=if(book)fold!!.bounds.left-6 else if(fontScale>1.25f)280f else 248f
    val bodyStart=if(book)fold!!.bounds.right+6 else if(sidebar)sidebarWidth+1 else paneStart
    val bodyTop=if(tabletop)fold!!.bounds.bottom+6 else if(tablet&&!sidebar)paneTop+64f else paneTop
    val contentWidth=width-bodyStart-paneEnd
    val listDetail=tablet&&contentWidth>=720f*fontScale.coerceIn(1f,1.5f)&&height-bodyTop-paneBottom>=480
}
internal val LocalWorkspaceLayout=staticCompositionLocalOf{WorkspaceLayout(0f,0f,1f,device=DeviceKind.PHONE)}
internal val LocalWorkspaceDetailInset=staticCompositionLocalOf{0.dp}
internal val LocalWorkspaceDetailTitle=staticCompositionLocalOf<String?>{null}

/** Navigation changes position without ever moving the live pager to another composition root. */
@Composable internal fun TabletWorkspaceShell(selected:Int,onSelect:(Int)->Unit,onSearch:()->Unit,
    cities:List<WeatherCity>,selectedCity:WeatherCity,onCity:(WeatherCity)->Unit,
    overview:String,content:@Composable ()->Unit) {
    val posture=LocalDevicePosture.current?:DevicePosture(DeviceKind.PHONE)
    val density=LocalDensity.current
    val insets=WindowInsets.systemBars.union(WindowInsets.displayCutout)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val left=with(density){insets.getLeft(this,androidx.compose.ui.unit.LayoutDirection.Ltr).toDp().value}
        val top=with(density){insets.getTop(this).toDp().value}
        val width=(maxWidth.value-with(density){(insets.getLeft(this,androidx.compose.ui.unit.LayoutDirection.Ltr)+insets.getRight(this,androidx.compose.ui.unit.LayoutDirection.Ltr)).toDp().value}).coerceAtLeast(1f)
        val height=(maxHeight.value-with(density){(insets.getTop(this)+insets.getBottom(this)).toDp().value}).coerceAtLeast(1f)
        val rawFold=posture.fold?.let{f->f.copy(bounds=with(density){Rect(f.bounds.left.toDp().value-left,f.bounds.top.toDp().value-top,
            f.bounds.right.toDp().value-left,f.bounds.bottom.toDp().value-top)})}
        // A fold event and the first measured bounds may arrive in separate frames.
        val fold=rawFold?.takeIf {f->if(f.vertical)f.bounds.left>=0&&f.bounds.right<=width
            else f.bounds.top>=0&&f.bounds.bottom<=height}
        val layout=WorkspaceLayout(width,height,density.fontScale,fold,posture.kind)
        var drawerOpen by rememberSaveable{mutableStateOf(false)}
        LaunchedEffect(layout.sidebar,layout.tablet){if(layout.sidebar||!layout.tablet)drawerOpen=false}
        BackHandler(drawerOpen){drawerOpen=false}
        val palette=MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().then(if(layout.tablet)Modifier.background(palette.background)
            .windowInsetsPadding(insets).imePadding() else Modifier)
            .onPreviewKeyEvent {event->
                if(!layout.tablet||event.type!=KeyEventType.KeyDown)false
                else if(event.key==Key.Escape&&drawerOpen){drawerOpen=false;true}
                else if(event.isCtrlPressed||event.isMetaPressed){when(event.key){
                    Key.One->{onSelect(0);true};Key.Two->{onSelect(1);true};Key.Three->{onSelect(2);true};Key.Four->{onSelect(3);true}
                    Key.F->{onSearch();true};else->false
                }}else false
            }.focusable()) {
            CompositionLocalProvider(LocalWorkspaceLayout provides layout) {
                // Stable child slot: resizing changes constraints and paddings only.
                Box(Modifier.fillMaxSize().padding(start=layout.bodyStart.dp,top=layout.bodyTop.dp,end=layout.paneEnd.dp,bottom=layout.paneBottom.dp)) {
                    AdaptiveWindowProvider{content()}
                }
                if(layout.sidebar) WorkspaceSidebar(selected,onSelect,onSearch,cities,selectedCity,onCity,
                    Modifier.width(layout.sidebarWidth.dp).fillMaxHeight())
                if(layout.tablet&&!layout.sidebar) {
                    Column(Modifier.fillMaxWidth().padding(start=layout.paneStart.dp,end=layout.paneEnd.dp).offset(y=layout.paneTop.dp).height(if(layout.tabletop)(fold!!.bounds.top-6).dp else 64.dp)
                        .background(palette.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            IconButton(onClick={drawerOpen=true}){Icon(Icons.Rounded.Menu,"開啟側邊欄")}
                            Text(listOf("天氣","地震","工具","設定")[selected.coerceIn(0,3)],fontSize=22.sp,lineHeight=32.sp,
                                fontWeight=FontWeight.Bold,color=palette.onSurface,modifier=Modifier.weight(1f).padding(horizontal=12.dp))
                            IconButton(onClick=onSearch){Icon(Icons.Rounded.Search,"搜尋城市")}
                        }
                        if(layout.tabletop)Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
                            Text(selectedCity.name,fontSize=22.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
                            Text(overview,fontSize=32.sp,lineHeight=46.sp,color=palette.onSurface)
                            Spacer(Modifier.height(12.dp))
                            WorkspaceTabs(selected,onSelect)
                        }
                    }
                }
                AnimatedVisibility(layout.tablet&&!layout.sidebar&&drawerOpen,enter=fadeIn(),exit=fadeOut()) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.35f))
                        .clickable{drawerOpen=false})
                }
                AnimatedVisibility(layout.tablet&&!layout.sidebar&&drawerOpen,
                    enter=slideInHorizontally{ -it },exit=slideOutHorizontally{ -it }) {
                    WorkspaceSidebar(selected,{onSelect(it);drawerOpen=false},{onSearch();drawerOpen=false},cities,selectedCity,
                        {onCity(it);drawerOpen=false},Modifier.padding(start=layout.paneStart.dp,top=layout.paneTop.dp,bottom=layout.paneBottom.dp)
                            .width(minOf(320f,layout.contentWidth-40).coerceAtLeast(120f).dp).fillMaxHeight())
                }
            }
        }
    }
}

@Composable private fun WorkspaceTabs(selected:Int,onSelect:(Int)->Unit) {
    AppGlassNavigation(selected,onSelect,Modifier.widthIn(max=560.dp))
}
@Composable private fun WorkspaceSidebar(selected:Int,onSelect:(Int)->Unit,onSearch:()->Unit,
    cities:List<WeatherCity>,selectedCity:WeatherCity,onCity:(WeatherCity)->Unit,modifier:Modifier) {
    val p=MaterialTheme.colorScheme
    Column(modifier.background(p.surfaceContainerLow).padding(horizontal=16.dp)
        .verticalScroll(rememberScrollState()).padding(top=26.dp,bottom=24.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.WbSunny,null,Modifier.size(28.dp),tint=p.primary)
            Text("thinn-khì",fontSize=25.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,color=p.onSurface,
                modifier=Modifier.padding(start=12.dp))
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surfaceContainerHighest)
            .iosJellyPress().clickable(onClick=onSearch).heightIn(min=48.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search,null,Modifier.size(20.dp),tint=p.onSurfaceVariant)
            Text("搜尋城市",fontSize=16.sp,lineHeight=26.sp,color=p.onSurfaceVariant,modifier=Modifier.padding(start=10.dp))
        }
        Spacer(Modifier.height(22.dp))
        val labels=listOf("天氣","地震","工具","設定")
        val icons=listOf(Icons.Rounded.CloudQueue,Icons.Rounded.MonitorHeart,Icons.Rounded.GridView,Icons.Rounded.Settings)
        labels.forEachIndexed {i,label->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(selected==i)p.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .iosJellyPress().selectable(selected==i,role=Role.Tab,onClick={onSelect(i)}).heightIn(min=54.dp).padding(horizontal=14.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically) {
                val ink=if(selected==i)p.onPrimaryContainer else p.onSurface
                Icon(icons[i],null,Modifier.size(24.dp),tint=ink)
                Text(label,fontSize=17.sp,lineHeight=28.sp,fontWeight=if(selected==i)FontWeight.SemiBold else FontWeight.Normal,
                    color=ink,modifier=Modifier.weight(1f).padding(start=12.dp))
                if(selected==i)Icon(Icons.Rounded.Check,null,Modifier.size(18.dp),tint=ink)
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("我的城市",fontSize=14.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,color=p.onSurfaceVariant,
            modifier=Modifier.padding(start=14.dp,bottom=8.dp))
        (listOf(selectedCity)+cities).distinctBy{it.id}.forEach {city->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .selectable(city.id==selectedCity.id,onClick={onCity(city)}).heightIn(min=48.dp).padding(horizontal=14.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text(city.name,fontSize=16.sp,lineHeight=27.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=p.onSurface,modifier=Modifier.weight(1f))
                if(city.id==selectedCity.id)Icon(Icons.Rounded.LocationOn,null,Modifier.size(18.dp),tint=p.primary)
            }
        }
    }
}

/** Both child slots remain stable across list/detail and compact presentation. */
@Composable internal fun WorkspaceSettingsStack(nested:Boolean,onBack:()->Unit,modifier:Modifier=Modifier,
    root:@Composable ()->Unit,detail:@Composable ()->Unit) {
    val workspace=LocalWorkspaceLayout.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val split=workspace.tablet&&maxWidth.value>=720*LocalDensity.current.fontScale.coerceIn(1f,1.5f)&&maxHeight>=480.dp
        val master=if(LocalDensity.current.fontScale>1.25f)350.dp else 300.dp
        Box(if(split&&nested)Modifier.width(master).fillMaxHeight() else Modifier.fillMaxSize()) {root()}
        MainDetailLayer(nested,onBack,if(split)master+16.dp else 0.dp){detail()}
    }
}
