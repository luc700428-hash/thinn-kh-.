package io.atmos.weather.ui
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Display-synchronised wall clock. Read its value in drawing, never in the whole page composition. */
@Composable internal fun rememberDisplayWallClock(active:Boolean):State<Long> {
    val result=remember {mutableLongStateOf(System.currentTimeMillis())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var started by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->started=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(active,started) {
        if(active && started) {
            val baseWall=System.currentTimeMillis();val baseFrame=withFrameNanos {it}
            while(true)withFrameNanos {result.longValue=baseWall+(it-baseFrame)/1_000_000}
        }
    }
    return result
}
