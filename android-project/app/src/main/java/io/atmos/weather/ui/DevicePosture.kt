package io.atmos.weather.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal enum class DeviceKind { PHONE, TABLET, FOLDABLE }
/** Window-relative physical pixels, as supplied by WindowManager. */
internal data class WindowFold(val bounds:Rect,val vertical:Boolean,val halfOpen:Boolean,val separating:Boolean)
internal data class DevicePosture(val kind:DeviceKind,val fold:WindowFold?=null)
internal val LocalDevicePosture=staticCompositionLocalOf<DevicePosture?>{null}
private tailrec fun Context.hostActivity():Activity?=when(this){
    is Activity->this
    is ContextWrapper->if(baseContext!==this)baseContext.hostActivity() else null
    else->null
}

@Composable internal fun DevicePostureProvider(content:@Composable ()->Unit) {
    if(LocalDevicePosture.current!=null){content();return}
    val context=LocalContext.current;val activity=remember(context){context.hostActivity()}
    val density=LocalDensity.current;val config=LocalConfiguration.current
    val foldFlow=remember(activity){activity?.let {host->WindowInfoTracker.getOrCreate(host)
        .windowLayoutInfo(host).map {info->info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let {f->
            WindowFold(Rect(f.bounds.left.toFloat(),f.bounds.top.toFloat(),f.bounds.right.toFloat(),f.bounds.bottom.toFloat()),
                f.orientation==FoldingFeature.Orientation.VERTICAL,f.state==FoldingFeature.State.HALF_OPENED,
                f.isSeparating||f.occlusionType==FoldingFeature.OcclusionType.FULL)
        }}.catch {emit(null)}}}
    val fold=foldFlow?.collectAsState(initial=null)?.value
    var observedFold by rememberSaveable{mutableStateOf(false)}
    SideEffect{if(fold!=null)observedFold=true}
    val largeDevice=remember(activity,config,density.density){
        val bounds=activity?.let {WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(it).bounds}
        if(bounds!=null)minOf(bounds.width(),bounds.height())/density.density>=600f else config.smallestScreenWidthDp>=600
    }
    val hinged=remember(context){context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)}
    val posture=DevicePosture(when{hinged||observedFold||fold!=null->DeviceKind.FOLDABLE;largeDevice->DeviceKind.TABLET;else->DeviceKind.PHONE},fold)
    CompositionLocalProvider(LocalDevicePosture provides posture){content()}
}

/** Place full-window forms and alarms on one side of an occluding hinge. */
@Composable internal fun FoldSafeContent(content:@Composable ()->Unit) {
    val fold=LocalDevicePosture.current?.fold;val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val r=fold?.bounds?.let {with(density){Rect(it.left.toDp().value,it.top.toDp().value,it.right.toDp().value,it.bottom.toDp().value)}}
            ?.takeIf {maxWidth>0.dp&&maxHeight>0.dp&&if(fold.vertical)it.left>=0&&it.right<=maxWidth.value else it.top>=0&&it.bottom<=maxHeight.value}
        val start=if(fold?.separating==true&&fold.vertical&&r!=null&&maxWidth.value-r.right>=260f)(r.right+6).dp else 0.dp
        val top=if(fold?.separating==true&&!fold.vertical&&r!=null&&maxHeight.value-r.bottom>=180f)(r.bottom+6).dp else 0.dp
        val end=if(fold?.separating==true&&fold.vertical&&r!=null&&start==0.dp&&r.left>=260f)(maxWidth.value-r.left+6).dp else 0.dp
        val bottom=if(fold?.separating==true&&!fold.vertical&&r!=null&&top==0.dp&&r.top>=180f)(maxHeight.value-r.top+6).dp else 0.dp
        Box(Modifier.fillMaxSize().padding(start=start,top=top,end=end,bottom=bottom)){content()}
    }
}
