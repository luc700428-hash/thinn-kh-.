package io.atmos.weather.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Keeps an outgoing detail alive through its transition. Back gestures seek the same layer. */
@Composable
fun MainDetailLayer(visible:Boolean,onBack:()->Unit,inset:Dp=LocalWorkspaceDetailInset.current,content:@Composable ()->Unit) {
    val reduce=rememberAppleReduceMotion()
    val gesture=remember {Animatable(0f)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(visible){if(visible)gesture.snapTo(0f)}
    PredictiveBackHandler(enabled=visible) {events ->
        try {
            events.collect {gesture.snapTo(if(reduce)0f else it.progress.coerceIn(0f,1f))}
            onBack()
        } catch(cancel:CancellationException) {
            scope.launch {gesture.animateTo(0f,spring(.82f,430f))}
            throw cancel
        }
    }
    AnimatedVisibility(visible=visible,modifier=Modifier.fillMaxSize().padding(start=inset),
        // iOS navigation: the page slides in from the right edge on a critically damped spring, no fade.
        enter=if(reduce)fadeIn(tween(150)) else slideInHorizontally(AppleMotion.push()){it},
        exit=if(reduce)fadeOut(tween(150)) else slideOutHorizontally(AppleMotion.push()){it}) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width=constraints.maxWidth.toFloat()
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX=gesture.value*width;alpha=1f-.12f*gesture.value
            }.background(MaterialTheme.colorScheme.background)){
                // Fixed detail controls retain a readable area above a floating Dock.
                // The outer background still fills the page behind its clear glass.
                val bottom=LocalWorkspaceBottomClearance.current
                CompositionLocalProvider(LocalWorkspaceBottomClearance provides 0.dp) {
                    AdaptivePageFrame(Modifier.padding(bottom=bottom)){content()}
                }
            }
        }
    }
}
