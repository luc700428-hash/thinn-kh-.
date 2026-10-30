package io.atmos.weather.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*

/** Hardware, battery/thermal policy and the launcher remain authoritative. No synthetic FPS. */
internal object HighRefreshWindow {
    private fun update(params:WindowManager.LayoutParams,chosen:RefreshRatePolicy.Mode?):Boolean {
        val hz=chosen?.hz?:RefreshRatePolicy.TARGET_HZ;val mode=chosen?.id?:0
        if(params.preferredRefreshRate==hz&&params.preferredDisplayModeId==mode)return false
        params.preferredRefreshRate=hz;params.preferredDisplayModeId=mode;return true
    }
    private fun requestViews(view:View) {
        if(Build.VERSION.SDK_INT>=36&&view is ViewGroup) {
            view.propagateRequestedFrameRate(RefreshRatePolicy.TARGET_HZ,true)
        } else if(Build.VERSION.SDK_INT>=35) {
            view.setRequestedFrameRate(RefreshRatePolicy.TARGET_HZ)
            if(view is ViewGroup)for(i in 0 until view.childCount)requestViews(view.getChildAt(i))
        }
    }
    fun apply(window:Window?,view:View) {
        val display=view.display
        @Suppress("DEPRECATION")
        val active=display?.mode
        val chosen=if(active!=null)RefreshRatePolicy.choose(display.supportedModes.map {
            RefreshRatePolicy.Mode(it.modeId,it.physicalWidth,it.physicalHeight,it.refreshRate)
        },active.physicalWidth,active.physicalHeight) else null
        window?.let {
            val params=it.attributes
            if(update(params,chosen))it.attributes=params
            if(Build.VERSION.SDK_INT>=35) {
                it.setFrameRatePowerSavingsBalanced(false)
                it.setFrameRateBoostOnTouchEnabled(true)
            }
        }
        // Popup has its own WindowManager root, separate from the Activity window.
        val root=view.rootView
        val popupParams=root.layoutParams as? WindowManager.LayoutParams
        if(root!==window?.decorView&&root.isAttachedToWindow&&popupParams!=null&&update(popupParams,chosen)) {
            runCatching{(view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root,popupParams)}
        }
        requestViews(view)
    }
    /** SurfaceView renderers vote separately; use the same target as every app window. */
    fun bindSurface(view:SurfaceView) {
        view.holder.addCallback(object:SurfaceHolder.Callback {
            private fun request(holder:SurfaceHolder) {
                if(holder.surface.isValid)runCatching{holder.surface.setFrameRate(RefreshRatePolicy.TARGET_HZ,
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)}
                apply(view.context.activity()?.window,view)
            }
            override fun surfaceCreated(holder:SurfaceHolder)=request(holder)
            override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int)=request(holder)
            override fun surfaceDestroyed(holder:SurfaceHolder)=Unit
        })
    }
}
private fun Context.activity():Activity?=when(this) {
    is Activity->this
    is ContextWrapper->baseContext.takeIf {it!==this}?.activity()
    else->null
}
/** Match system icons to the visible page, including full-screen dialog windows. */
@Suppress("DEPRECATION")
@Composable internal fun AppSystemBars(darkBackground:Boolean) {
    val view=LocalView.current
    SideEffect {
        val window=(view.parent as? DialogWindowProvider)?.window?:view.context.activity()?.window
        window?.let {
            val flags=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            it.insetsController?.setSystemBarsAppearance(if(darkBackground)0 else flags,flags)
            it.navigationBarColor=if(darkBackground)android.graphics.Color.BLACK else 0xfff2f2f7.toInt()
        }
    }
}
@Composable internal fun HighRefreshHost() {
    val view=LocalView.current
    DisposableEffect(view) {
        val window=(view.parent as? DialogWindowProvider)?.window?:view.context.activity()?.window
        fun request(){HighRefreshWindow.apply(window,view)}
        request()
        val listener=object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v:View){request()}
            override fun onViewDetachedFromWindow(v:View)=Unit
        }
        val manager=view.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayListener=object:DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId:Int)=Unit
            override fun onDisplayRemoved(displayId:Int)=Unit
            override fun onDisplayChanged(displayId:Int){if(view.isAttachedToWindow&&view.display?.displayId==displayId)request()}
        }
        view.addOnAttachStateChangeListener(listener)
        manager.registerDisplayListener(displayListener,Handler(Looper.getMainLooper()))
        onDispose {view.removeOnAttachStateChangeListener(listener);manager.unregisterDisplayListener(displayListener)}
    }
}
@Composable internal fun rememberDisplayRefreshRate():Float? {
    val view=LocalView.current
    fun read()=view.display?.refreshRate?.takeIf{it.isFinite()&&it>0}
    var hz by remember(view){mutableStateOf(read())}
    DisposableEffect(view) {
        val manager=view.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val callback=object:DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId:Int){hz=read()}
            override fun onDisplayRemoved(displayId:Int){hz=read()}
            override fun onDisplayChanged(displayId:Int){if(view.display?.displayId==displayId)hz=read()}
        }
        val attachment=object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v:View){hz=read()}
            override fun onViewDetachedFromWindow(v:View)=Unit
        }
        view.addOnAttachStateChangeListener(attachment)
        manager.registerDisplayListener(callback,Handler(Looper.getMainLooper()))
        view.post{hz=read()}
        onDispose{manager.unregisterDisplayListener(callback);view.removeOnAttachStateChangeListener(attachment)}
    }
    return hz
}
/** App windows, sheets and popup menus share one refresh-rate preference. */
@Composable fun ThinnDialog(onDismissRequest:()->Unit,properties:DialogProperties=DialogProperties(),
    adaptiveContentWidth:Dp?=960.dp,content:@Composable ()->Unit) {
    val readingDensity=LocalDensity.current
    val contentColor=LocalContentColor.current
    Dialog(onDismissRequest,properties=properties){
        CompositionLocalProvider(LocalDensity provides readingDensity,LocalContentColor provides contentColor){
            HighRefreshHost()
            if(!properties.usePlatformDefaultWidth && adaptiveContentWidth!=null) {
                AdaptiveWindowProvider { AdaptivePageFrame(maxContentWidth=adaptiveContentWidth){content()} }
            } else content()
        }
    }
}
@Composable fun ThinnPopup(alignment:Alignment=Alignment.TopStart,offset:IntOffset=IntOffset.Zero,
    onDismissRequest:(()->Unit)?=null,properties:PopupProperties=PopupProperties(),content:@Composable ()->Unit) {
    val readingDensity=LocalDensity.current
    val contentColor=LocalContentColor.current
    Popup(alignment,offset,onDismissRequest,properties){
        CompositionLocalProvider(LocalDensity provides readingDensity,LocalContentColor provides contentColor){HighRefreshHost();content()}
    }
}
@Composable fun ThinnPopup(popupPositionProvider:PopupPositionProvider,onDismissRequest:(()->Unit)?=null,
    properties:PopupProperties=PopupProperties(),content:@Composable ()->Unit) {
    val readingDensity=LocalDensity.current
    val contentColor=LocalContentColor.current
    Popup(popupPositionProvider,onDismissRequest,properties){
        CompositionLocalProvider(LocalDensity provides readingDensity,LocalContentColor provides contentColor){HighRefreshHost();content()}
    }
}
