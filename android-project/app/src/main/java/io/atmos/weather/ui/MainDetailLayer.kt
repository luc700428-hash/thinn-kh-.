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
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Keeps an outgoing detail alive through its transition. Back gestures seek the same layer. */
@Composable
fun MainDetailLayer(visible:Boolean,onBack:()->Unit,content:@Composable ()->Unit) {
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
    AnimatedVisibility(visible=visible,modifier=Modifier.fillMaxSize(),
        enter=slideInHorizontally(tween(if(reduce)0 else 420,easing=AppleMotion.fluid)){it}+
            fadeIn(tween(if(reduce)0 else 230),initialAlpha=.65f),
        exit=slideOutHorizontally(tween(if(reduce)0 else 360,easing=AppleMotion.fluid)){it}+
            fadeOut(tween(if(reduce)0 else 320))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width=constraints.maxWidth.toFloat()
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX=gesture.value*width;alpha=1f-.12f*gesture.value
            }.background(MaterialTheme.colorScheme.background)){content()}
        }
    }
}
