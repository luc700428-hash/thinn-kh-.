package io.atmos.weather.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import io.atmos.weather.presentation.home.LocalAppearance

data class IOSMotionResponse(val damping:Float,val stiffness:Float)
@Composable fun iosMotionResponse():IOSMotionResponse{
 val v=LocalAppearance.current.springiness.coerceIn(0f,1f)
 return IOSMotionResponse(.9f-.35f*v,320f-100f*v)
}
/** Observes the existing click gesture; never steals scrolling or replaces a callback. */
fun Modifier.iosJellyPress():Modifier=composed{
 val reduce=rememberAppleReduceMotion();val response=iosMotionResponse();var down by remember{mutableStateOf(false)}
 val x by animateFloatAsState(if(down&&!reduce).975f else 1f,if(reduce)snap() else spring(response.damping,response.stiffness),label="果凍橫向回彈")
 val y by animateFloatAsState(if(down&&!reduce).95f else 1f,if(reduce)snap() else spring(response.damping,response.stiffness),label="果凍縱向回彈")
 this.graphicsLayer{scaleX=x;scaleY=y;alpha=if(down&&!reduce).92f else 1f}.pointerInput(Unit){awaitEachGesture{awaitFirstDown(requireUnconsumed=false);down=true;try{var held=true;while(held){val event=awaitPointerEvent(PointerEventPass.Final);held=event.changes.any{it.pressed}}}finally{down=false}}}
}
