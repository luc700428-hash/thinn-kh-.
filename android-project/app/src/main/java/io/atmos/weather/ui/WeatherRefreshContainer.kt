package io.atmos.weather.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WeatherRefreshContainer(refreshing:Boolean,onRefresh:()->Unit,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    val state=rememberPullToRefreshState()
    val reduce=rememberAppleReduceMotion()
    // Every per-frame value is read while drawing, never while composing: pulling or the
    // refresh spinner must not recompose the forecast page under it.
    val pull={state.distanceFraction.coerceIn(0f,1.8f)}
    // The page follows the finger 1:1 (the pull state springs back by itself); only the
    // refreshing rest position is animated here.
    val offset=animateFloatAsState(if(refreshing)30f else 0f,
        if(reduce)snap() else spring(.88f,280f),label="weather-pull-rebound")
    val spin:State<Float> =if(refreshing&&!reduce)
        rememberInfiniteTransition(label="refresh-light").animateFloat(0f,360f,infiniteRepeatable(tween(1000,easing=LinearEasing)),label="refresh-ring")
        else rememberUpdatedState(0f)
    PullToRefreshBox(isRefreshing=refreshing,onRefresh=onRefresh,state=state,modifier=modifier,
        indicator={
            Canvas(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=12.dp).size(42.dp)
                .graphicsLayer{alpha=if(refreshing)1f else pull().coerceIn(0f,1f)}) {
                val amount=pull()
                if(amount<=.01f&&!refreshing)return@Canvas
                val phase=if(refreshing&&!reduce)spin.value else amount*200f
                drawCircle(brandColor(0xDD163F34),size.minDimension/2)
                drawCircle(Color.White.copy(alpha=.18f),size.minDimension/2-1.dp.toPx(),style=Stroke(1.dp.toPx()))
                drawArc(brandColor(0xFFB1E4D6),phase,if(refreshing)265f else 320f*amount.coerceIn(0f,1f),false,
                    Offset(10.dp.toPx(),10.dp.toPx()),androidx.compose.ui.geometry.Size(22.dp.toPx(),22.dp.toPx()),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
            }
        }) {
        Box(Modifier.graphicsLayer{translationY=if(reduce)0f else maxOf(offset.value,pull()*36f).dp.toPx()}){content()}
    }
}
