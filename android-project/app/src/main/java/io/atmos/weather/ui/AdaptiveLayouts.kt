package io.atmos.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
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
    DevicePostureProvider { BoxWithConstraints(modifier.fillMaxSize()) {
        val layout=AppWindowLayout(maxWidth.value,maxHeight.value,density.fontScale)
        CompositionLocalProvider(LocalAppWindowLayout provides layout){content()}
    }}
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

/** Stable child slots retain form and animation state when the window changes shape. */
@Composable internal fun AdaptiveScrollingPanes(
    modifier:Modifier=Modifier,scrollState:ScrollState=rememberScrollState(),
    horizontalPadding:Dp=20.dp,topPadding:Dp=12.dp,bottomPadding:Dp=132.dp,
    paneSpacing:Dp=0.dp,applyStatusBars:Boolean=true,leadingFraction:Float=.42f,
    leading:@Composable ColumnScope.()->Unit,trailing:@Composable ColumnScope.()->Unit
) {
    val window=currentAppWindowLayout();val scale=LocalDensity.current.fontScale
    val firstScroll=rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide=AppWindowLayout(maxWidth.value,window.height,scale).twoPanes
        val bottom=if(LocalWorkspaceLayout.current.tablet)minOf(bottomPadding,32.dp) else bottomPadding
        val bars=if(applyStatusBars)Modifier.statusBarsPadding() else Modifier
        Layout(modifier=Modifier.align(Alignment.TopCenter).widthIn(max=if(wide)1280.dp else 760.dp)
            .fillMaxSize().then(bars).then(if(!wide)Modifier.verticalScroll(scrollState,overscrollEffect=null) else Modifier)
            .padding(horizontal=horizontalPadding).padding(top=topPadding),content={
            Column(Modifier.fillMaxWidth().then(if(wide)Modifier.fillMaxHeight().verticalScroll(firstScroll) else Modifier)
                .padding(bottom=if(wide)bottom else 0.dp),verticalArrangement=Arrangement.spacedBy(paneSpacing),content=leading)
            Column(Modifier.fillMaxWidth().then(if(wide)Modifier.fillMaxHeight().verticalScroll(scrollState) else Modifier)
                .padding(bottom=bottom),verticalArrangement=Arrangement.spacedBy(paneSpacing),content=trailing)
        }) {children,c->
            val gap=(if(wide)24.dp else paneSpacing).roundToPx()
            if(wide) {
                val firstWidth=((c.maxWidth-gap)*leadingFraction).toInt().coerceAtLeast(0)
                val a=children[0].measure(Constraints.fixed(firstWidth,c.maxHeight))
                val b=children[1].measure(Constraints.fixed((c.maxWidth-gap-firstWidth).coerceAtLeast(0),c.maxHeight))
                layout(c.maxWidth,c.maxHeight){a.placeRelative(0,0);b.placeRelative(firstWidth+gap,0)}
            } else {
                val a=children[0].measure(Constraints(minWidth=c.maxWidth,maxWidth=c.maxWidth))
                val b=children[1].measure(Constraints(minWidth=c.maxWidth,maxWidth=c.maxWidth))
                layout(c.maxWidth,c.constrainHeight(a.height+gap+b.height)){a.placeRelative(0,0);b.placeRelative(0,a.height+gap)}
            }
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
