package io.atmos.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal enum class AppWindowWidth { COMPACT, MEDIUM, EXPANDED }

/** Current app-window dimensions in dp, never a physical device or pixel-ratio guess. */
internal data class AppWindowLayout(val width:Float,val height:Float,val fontScale:Float) {
    val widthClass get()=when {width<600f->AppWindowWidth.COMPACT;width<840f->AppWindowWidth.MEDIUM;else->AppWindowWidth.EXPANDED}
    val compactHeight get()=height<480f
    val aspectRatio get()=width/height.coerceAtLeast(1f)
    val twoPanes get()=!compactHeight && width>=max(840f,760f*fontScale.coerceAtLeast(1f))
    fun gridColumns(available:Float,maxColumns:Int=2):Int =
        if(compactHeight)1 else ((available+18f)/(320f*fontScale.coerceIn(1f,1.5f)+18f)).toInt().coerceIn(1,maxColumns)
    fun mapHeight(phone:Float):Float=when {
        compactHeight->(height*.60f).coerceIn(180f,phone)
        width>=600f->(height*.55f).coerceIn(360f,620f)
        else->phone
    }
}

internal val LocalAppWindowLayout=staticCompositionLocalOf<AppWindowLayout?>{null}

@Composable internal fun currentAppWindowLayout():AppWindowLayout {
    LocalAppWindowLayout.current?.let{return it}
    val density=LocalDensity.current
    val size=LocalWindowInfo.current.containerSize
    return AppWindowLayout(with(density){size.width.toDp().value},with(density){size.height.toDp().value},density.fontScale)
}

/** One bounded root observes rotation, split screen and desktop window resizing. */
@Composable fun AdaptiveWindowProvider(modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    val density=LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout=AppWindowLayout(maxWidth.value,maxHeight.value,density.fontScale)
        CompositionLocalProvider(LocalAppWindowLayout provides layout){content()}
    }
}

/** Keep reading/forms comfortably bounded, while the parent background stays full-window. */
@Composable fun AdaptivePageFrame(modifier:Modifier=Modifier,maxContentWidth:Dp=1120.dp,content:@Composable ()->Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gutter=if(maxWidth>=600.dp)24.dp else 0.dp
        val width=(maxWidth-gutter*2).coerceAtLeast(0.dp).coerceAtMost(maxContentWidth)
        Box(Modifier.align(Alignment.TopCenter).width(width).fillMaxHeight()){content()}
    }
}

@Composable internal fun adaptiveMapHeight(phone:Dp):Dp=currentAppWindowLayout().mapHeight(phone.value).dp

/** Movable panes keep sliders, focus and child state when crossing a breakpoint. */
@Composable internal fun AdaptiveScrollingPanes(
    modifier:Modifier=Modifier,
    scrollState:ScrollState=rememberScrollState(),
    horizontalPadding:Dp=20.dp,
    topPadding:Dp=12.dp,
    bottomPadding:Dp=132.dp,
    paneSpacing:Dp=0.dp,
    applyStatusBars:Boolean=true,
    leadingFraction:Float=.42f,
    leading:@Composable ColumnScope.()->Unit,
    trailing:@Composable ColumnScope.()->Unit
) {
    val window=currentAppWindowLayout()
    val scale=LocalDensity.current.fontScale
    val latestLeading=rememberUpdatedState(leading);val latestTrailing=rememberUpdatedState(trailing)
    val spacing=rememberUpdatedState(paneSpacing)
    val first=remember{movableContentOf{Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(spacing.value)){latestLeading.value(this)}}}
    val second=remember{movableContentOf{Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(spacing.value)){latestTrailing.value(this)}}}
    val firstScroll=rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide=AppWindowLayout(maxWidth.value,window.height,scale).twoPanes
        val bars=if(applyStatusBars)Modifier.statusBarsPadding() else Modifier
        if(wide) {
            Row(Modifier.align(Alignment.TopCenter).widthIn(max=1280.dp).fillMaxSize()
                .then(bars).padding(horizontal=horizontalPadding,vertical=topPadding),
                horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(leadingFraction).fillMaxHeight().verticalScroll(firstScroll)
                    .padding(bottom=bottomPadding)){first()}
                Column(Modifier.weight(1f-leadingFraction).fillMaxHeight().verticalScroll(scrollState)
                    .padding(bottom=bottomPadding)){second()}
            }
        } else {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max=760.dp).fillMaxSize()
                .verticalScroll(scrollState,overscrollEffect=null).padding(horizontal=horizontalPadding)
                .then(bars).padding(top=topPadding,bottom=bottomPadding)) {first();if(paneSpacing>0.dp)Spacer(Modifier.height(paneSpacing));second()}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AdaptiveCardGrid(count:Int,modifier:Modifier=Modifier,maxColumns:Int=2,
    itemKey:(Int)->Any={it},content:@Composable (Int)->Unit) {
    val window=currentAppWindowLayout();val scale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns=window.copy(fontScale=scale).gridColumns(maxWidth.value,maxColumns)
        val cell=(maxWidth-18.dp*(columns-1))/columns
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp),maxItemsInEachRow=columns) {
            repeat(count){index->key(itemKey(index)){Box(Modifier.width(cell)){content(index)}}}
        }
    }
}

/** A square radar viewport plus a scrolling inspector in wide/short windows. */
@Composable internal fun AdaptiveMapInspector(modifier:Modifier=Modifier,
    map:@Composable (Modifier)->Unit,controls:@Composable ColumnScope.()->Unit) {
    val scale=LocalDensity.current.fontScale
    val latestMap=rememberUpdatedState(map);val latestControls=rememberUpdatedState(controls)
    val mapPane=remember{movableContentOf<Modifier>{m->latestMap.value(m)}}
    val inspector=remember{movableContentOf{Column(Modifier.fillMaxWidth()){latestControls.value(this)}}}
    val phoneScroll=rememberScrollState();val controlsScroll=rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        if(maxWidth>=840.dp || (maxWidth>=600.dp && maxHeight<480.dp)) {
            val inspectorWidth=minOf((320f*scale.coerceIn(1f,1.3f)).dp,maxWidth*.48f)
            Row(Modifier.align(Alignment.TopCenter).widthIn(max=1440.dp).fillMaxSize().padding(16.dp),
                horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center) {
                    val side=minOf(maxWidth,maxHeight)
                    mapPane(Modifier.size(side))
                }
                Column(Modifier.width(inspectorWidth).fillMaxHeight()
                    .verticalScroll(controlsScroll)){inspector()}
            }
        } else {
            // Leave a strip of controls visible, so map panning cannot trap a short window.
            val side=minOf((maxWidth-24.dp).coerceAtLeast(0.dp),(maxHeight-96.dp).coerceAtLeast(0.dp),696.dp)
            Column(Modifier.align(Alignment.TopCenter).widthIn(max=720.dp).fillMaxSize()
                .verticalScroll(phoneScroll).padding(horizontal=12.dp,vertical=8.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                mapPane(Modifier.align(Alignment.CenterHorizontally).size(side))
                inspector()
            }
        }
    }
}
