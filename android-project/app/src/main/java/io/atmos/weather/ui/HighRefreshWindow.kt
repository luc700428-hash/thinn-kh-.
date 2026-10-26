package io.atmos.weather.ui

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** A request, never a promise: display hardware, battery/thermal/OEM policy remain authoritative. */
internal object HighRefreshWindow {
    fun apply(window:Window?,view:View) {
        val display=view.display
        @Suppress("DEPRECATION")
        val active=display?.mode
        val chosen=if(active!=null)RefreshRatePolicy.choose(display.supportedModes.map {
            RefreshRatePolicy.Mode(it.modeId,it.physicalWidth,it.physicalHeight,it.refreshRate)
        },active.physicalWidth,active.physicalHeight) else null
        window?.let {
            it.attributes=it.attributes.apply {
                preferredRefreshRate=chosen?.hz ?: RefreshRatePolicy.TARGET_HZ
                if(chosen!=null)preferredDisplayModeId=chosen.id
            }
            if(Build.VERSION.SDK_INT>=35) {
                it.setFrameRatePowerSavingsBalanced(false)
                it.setFrameRateBoostOnTouchEnabled(true)
            }
        }
        if(Build.VERSION.SDK_INT>=35) view.setRequestedFrameRate(RefreshRatePolicy.TARGET_HZ)
    }
}
private fun Context.activity():Activity?=when(this) {
    is Activity->this
    is ContextWrapper->baseContext.takeIf {it!==this}?.activity()
    else->null
}
@Composable internal fun HighRefreshHost() {
    val view=LocalView.current
    DisposableEffect(view) {
        val window=(view.parent as? DialogWindowProvider)?.window ?: view.context.activity()?.window
        fun request(){HighRefreshWindow.apply(window,view)}
        request()
        val listener=object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v:View){request()}
            override fun onViewDetachedFromWindow(v:View)=Unit
        }
        view.addOnAttachStateChangeListener(listener)
        onDispose {view.removeOnAttachStateChangeListener(listener)}
    }
}
/** Common dialog host so alarm/editor/update/map sheets vote for the same refresh rate. */
@Composable fun ThinnDialog(onDismissRequest:()->Unit,properties:DialogProperties=DialogProperties(),content:@Composable ()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest,properties=properties) {HighRefreshHost();content()}
}
