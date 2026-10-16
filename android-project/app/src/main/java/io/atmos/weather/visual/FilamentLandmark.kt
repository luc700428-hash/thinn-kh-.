package io.atmos.weather.visual

import android.util.Log
import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer

/** Live GLB rendering in Android Compose. This does not improve a model's source geometry. */
@Composable
fun FilamentLandmark(modelAsset:String, modifier:Modifier=Modifier, fallback:@Composable ()->Unit) {
    var failed by remember(modelAsset) { mutableStateOf(false) }
    val holder=remember(modelAsset) { ModelHolder() }
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(holder,lifecycleOwner) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_START -> holder.resume()
                Lifecycle.Event.ON_STOP -> holder.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if(!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) holder.pause()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); holder.close() }
    }
    if(failed) {
        fallback()
    } else {
        Box(modifier) {
            key(modelAsset) {
                AndroidView(factory={ context ->
                    SurfaceView(context).also { surface ->
                        try {
                            // Filament official Android sample requires native library initialization.
                            Utils.init()
                            val viewer=ModelViewer(surface)
                            val sky=Skybox.Builder().color(0.035f,0.10f,0.16f,1.0f).build(viewer.engine)
                            viewer.scene.skybox=sky
                            val bytes=context.assets.open(modelAsset).use { it.readBytes() }
                            require(bytes.isNotEmpty() && bytes.size<=12*1024*1024) { "Model outside supported size" }
                            val modelBuffer=ByteBuffer.allocateDirect(bytes.size)
                            modelBuffer.put(bytes)
                            modelBuffer.flip()
                            viewer.loadModelGlb(modelBuffer)
                            viewer.transformToUnitCube()
                            surface.setOnTouchListener(viewer)
                            holder.open(viewer,sky)
                        } catch(ex:Exception) {
                            Log.w("thinn-glb","模型顯示失敗：$modelAsset",ex)
                            failed=true
                        }
                    }
                },modifier=Modifier.matchParentSize())
            }
        }
    }
}

/** The Choreographer callback stops when Compose disposes or swaps a landmark. */
private class ModelHolder {
    private var viewer:ModelViewer?=null
    private var sky:Skybox?=null
    private val choreographer=Choreographer.getInstance()
    private var active=false
    private var foreground=true
    private val callback=object:Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos:Long) {
            if(!active) return
            runCatching { viewer?.render(frameTimeNanos) }
            if(active) choreographer.postFrameCallback(this)
        }
    }
    fun open(v:ModelViewer,s:Skybox) {
        close()
        viewer=v;sky=s
        if(foreground) startFrames()
    }
    private fun startFrames() {
        if(viewer==null || active) return
        active=true
        choreographer.postFrameCallback(callback)
    }
    fun pause() {
        foreground=false
        active=false
        choreographer.removeFrameCallback(callback)
    }
    fun resume() {
        foreground=true
        startFrames()
    }
    fun close() {
        active=false
        choreographer.removeFrameCallback(callback)
        val v=viewer ?: return
        runCatching { v.destroyModel() }
        runCatching { sky?.let { v.engine.destroySkybox(it) } }
        runCatching { v.engine.destroy() }
        viewer=null;sky=null
    }
}
