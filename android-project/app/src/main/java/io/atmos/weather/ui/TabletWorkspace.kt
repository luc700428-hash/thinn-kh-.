package io.atmos.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherCity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class WorkspaceLayout(val width:Float,val height:Float,val fontScale:Float,
    val fold:WindowFold?=null,val device:DeviceKind=DeviceKind.TABLET,
    val dockPosition:TabletDockPosition=TabletDockPosition.RIGHT) {
    val book=fold?.let{it.separating&&it.vertical&&it.bounds.left>=200&&width-it.bounds.right>=260}==true
    val tabletop=fold?.let{it.separating&&!it.vertical&&it.bounds.top>=180&&height-it.bounds.bottom>=200}==true
    val tablet=(device!=DeviceKind.PHONE&&width>=600)||fold?.separating==true
    val pane:Rect get()=when {
        book->Rect(fold!!.bounds.right+6,0f,width,height)
        tabletop->Rect(0f,fold!!.bounds.bottom+6,width,height)
        fold?.separating==true&&fold.vertical->if(width-fold.bounds.right>fold.bounds.left)
            Rect(fold.bounds.right+6,0f,width,height) else Rect(0f,0f,(fold.bounds.left-6).coerceAtLeast(1f),height)
        fold?.separating==true->if(height-fold.bounds.bottom>fold.bounds.top)
            Rect(0f,fold.bounds.bottom+6,width,height) else Rect(0f,0f,width,(fold.bounds.top-6).coerceAtLeast(1f))
        else->Rect(0f,0f,width,height)
    }
    val overview:Rect? get()=when {book->Rect(0f,0f,fold!!.bounds.left-6,height);tabletop->Rect(0f,0f,width,fold!!.bounds.top-6);else->null}
    val placement get()=if(tablet)TabletDockGeometry.place(pane,fontScale,dockPosition)
        else TabletDockPlacement(TabletDockPosition.BOTTOM,Rect.Zero,Rect(0f,0f,width,height))
    val contentWidth get()=placement.content.width
    val listDetail get()=tablet&&contentWidth>=720f*fontScale.coerceIn(1f,1.5f)&&placement.content.height>=480
}
internal val LocalWorkspaceLayout=staticCompositionLocalOf{WorkspaceLayout(0f,0f,1f,device=DeviceKind.PHONE)}
internal val LocalWorkspaceDetailInset=staticCompositionLocalOf{0.dp}
internal val LocalWorkspaceDetailTitle=staticCompositionLocalOf<String?>{null}

/** One permanent content root, plus the same liquid dock on all four tablet edges. */
@Composable internal fun TabletWorkspaceShell(selected:Int,onSelect:(Int)->Unit,onSearch:()->Unit,
    cities:List<WeatherCity>,selectedCity:WeatherCity,onCity:(WeatherCity)->Unit,
    overview:String,content:@Composable ()->Unit) {
    val posture=LocalDevicePosture.current?:DevicePosture(DeviceKind.PHONE)
    val preferred=rememberTabletDockPosition();val reduce=rememberAppleReduceMotion()
    val density=LocalDensity.current
    val insets=WindowInsets.systemBars.union(WindowInsets.displayCutout)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val left=with(density){insets.getLeft(this,androidx.compose.ui.unit.LayoutDirection.Ltr).toDp().value}
        val top=with(density){insets.getTop(this).toDp().value}
        val width=(maxWidth.value-with(density){(insets.getLeft(this,androidx.compose.ui.unit.LayoutDirection.Ltr)+insets.getRight(this,androidx.compose.ui.unit.LayoutDirection.Ltr)).toDp().value}).coerceAtLeast(1f)
        val edgeMode=(posture.kind!=DeviceKind.PHONE&&width>=600f)||posture.fold?.separating==true
        val bottomInset=if(edgeMode)maxOf(insets.getBottom(density),WindowInsets.ime.getBottom(density)) else insets.getBottom(density)
        val height=(maxHeight.value-with(density){(insets.getTop(this)+bottomInset).toDp().value}).coerceAtLeast(1f)
        val rawFold=posture.fold?.let{f->f.copy(bounds=with(density){Rect(f.bounds.left.toDp().value-left,f.bounds.top.toDp().value-top,
            f.bounds.right.toDp().value-left,f.bounds.bottom.toDp().value-top)})}
        val fold=rawFold?.takeIf {f->if(f.vertical)f.bounds.left>=0&&f.bounds.right<=width else f.bounds.top>=0&&f.bounds.bottom<=height}
        val layout=WorkspaceLayout(width,height,density.fontScale,fold,posture.kind,preferred)
        val target=layout.placement;val pane=layout.pane
        val dockMotion=remember{Animatable(target.dock,Rect.VectorConverter)}
        val contentMotion=remember{Animatable(target.content,Rect.VectorConverter)}
        val opacity=remember{Animatable(1f)}
        var renderedVertical by remember{mutableStateOf(target.position.vertical)}
        var previousPane by remember{mutableStateOf(pane)}
        val paneChanged=previousPane!=pane
        LaunchedEffect(pane,target,reduce,layout.tablet) {
            if(reduce||previousPane!=pane||!layout.tablet) {
                previousPane=pane;renderedVertical=target.position.vertical
                dockMotion.snapTo(target.dock);contentMotion.snapTo(target.content);opacity.snapTo(1f)
            } else {
                if(renderedVertical!=target.position.vertical) {
                    opacity.animateTo(0f,tween(90))
                    val center=dockMotion.value.center
                    renderedVertical=target.position.vertical
                    dockMotion.snapTo(TabletDockGeometry.fit(Rect(center.x-target.dock.width/2,center.y-target.dock.height/2,
                        center.x+target.dock.width/2,center.y+target.dock.height/2),pane))
                }
                coroutineScope {
                    launch{dockMotion.animateTo(target.dock,tween(400,easing=LinearEasing))}
                    launch{contentMotion.animateTo(target.content,tween(400,easing=AppleMotion.fluid))}
                    launch{opacity.animateTo(1f,tween(220))}
                }
            }
        }
        val body=TabletDockGeometry.fit(if(paneChanged)target.content else contentMotion.value,pane)
        val dock=TabletDockGeometry.fit(if(paneChanged)target.dock else dockMotion.value,pane)
        val palette=MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().then(if(layout.tablet)Modifier.background(palette.background)
            .windowInsetsPadding(insets).imePadding() else Modifier)
            .onPreviewKeyEvent {event->
                if(!layout.tablet||event.type!=KeyEventType.KeyDown)false
                else if(event.isCtrlPressed||event.isMetaPressed){when(event.key){
                    Key.One->{onSelect(0);true};Key.Two->{onSelect(1);true};Key.Three->{onSelect(2);true};Key.Four->{onSelect(3);true}
                    Key.F->{onSearch();true};else->false
                }}else false
            }.focusable()) {
            CompositionLocalProvider(LocalWorkspaceLayout provides layout) {
                Box(Modifier.fillMaxSize().padding(start=body.left.dp,top=body.top.dp,
                    end=(width-body.right).coerceAtLeast(0f).dp,bottom=(height-body.bottom).coerceAtLeast(0f).dp)
                    .testTag("tablet-main-content")) {AdaptiveWindowProvider{content()}}
                if(layout.tablet) {
                    layout.overview?.let {area->
                        Box(Modifier.offset(area.left.dp,area.top.dp).size(area.width.dp,area.height.dp)
                            .background(palette.surfaceContainerLow)) {TabletWeatherOverview(selectedCity,overview,onSearch)}
                    }
                    AppGlassNavigation(selected,onSelect,
                        Modifier.offset(dock.left.dp,dock.top.dp).size(dock.width.dp,dock.height.dp)
                            .graphicsLayer{alpha=if(paneChanged)1f else opacity.value}
                            .semantics{contentDescription="主導覽 Dock · ${target.position.title}"}.testTag("tablet-navigation-dock"),
                        vertical=if(paneChanged)target.position.vertical else renderedVertical,edgeSized=true)
                }
            }
        }
    }
}

/** Folded panes keep useful weather context, without duplicating the four navigation tabs. */
@Composable private fun TabletWeatherOverview(city:WeatherCity,overview:String,onSearch:()->Unit) {
    val p=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.Center) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudQueue,null,Modifier.size(30.dp),tint=p.primary)
            AppText("thinn-khì",fontSize=23.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold,color=p.onSurface,modifier=Modifier.padding(start=12.dp))
        }
        Spacer(Modifier.height(24.dp))
        AppText(city.name,fontSize=22.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold,color=p.onSurface)
        AppText(overview,fontSize=34.sp,lineHeight=48.sp,color=p.onSurface)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.heightIn(min=48.dp).iosJellyPress().clickable(onClick=onSearch).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search,null,Modifier.size(22.dp),tint=p.primary)
            AppText("搜尋城市",fontSize=16.sp,lineHeight=26.sp,color=p.primary,modifier=Modifier.padding(start=10.dp))
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
