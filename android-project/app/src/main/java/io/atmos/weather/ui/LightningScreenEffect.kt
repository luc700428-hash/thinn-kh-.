package io.atmos.weather.ui

import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color

/** Draw-phase only: a flash must not recompose the weather page or its maps every display frame. */
internal fun Modifier.lightningScreenEffect(alpha:State<Float>,enabled:Boolean,night:Boolean)=drawWithContent {
 drawContent()
 if(enabled){
  val value=alpha.value*(if(night).72f else 1f)
  if(value>0f)drawRect(Color(0xFFEAF2FF).copy(alpha=value.coerceIn(0f,.23f)))
  else if(value<0f)drawRect(Color(0xFF182A40).copy(alpha=(-value).coerceIn(0f,.08f)))
 }
}

internal class LightningFlashState {
 val alpha=androidx.compose.runtime.mutableFloatStateOf(0f)
 private var job:kotlinx.coroutines.Job?=null
 fun trigger(scope:kotlinx.coroutines.CoroutineScope,seed:Int):kotlinx.coroutines.Job {
  job?.cancel()
  return scope.launch {
   val profile=LightningProfile.fromSeed(seed)
   val start=androidx.compose.runtime.withFrameNanos{it}
   var elapsed=0f
   try {
    while(elapsed<profile.duration)androidx.compose.runtime.withFrameNanos{now->
     elapsed=((now-start)/1_000_000_000.0).toFloat()
     alpha.floatValue=profile.intensity(elapsed)*.22f
    }
   }finally{alpha.floatValue=0f}
  }.also{job=it}
 }
 fun stop(){job?.cancel();job=null;alpha.floatValue=0f}
}
