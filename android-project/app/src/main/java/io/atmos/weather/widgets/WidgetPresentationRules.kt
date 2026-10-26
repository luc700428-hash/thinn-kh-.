package io.atmos.weather.widgets
object WidgetPresentationRules {
 enum class Freshness { MISSING, FRESH, CACHED, EXPIRED }
 fun freshness(stamp:Long,now:Long):Freshness=when{stamp<=0||stamp>now+60_000->Freshness.MISSING;now-stamp<=90*60_000L->Freshness.FRESH;now-stamp<=24*60*60_000L->Freshness.CACHED;else->Freshness.EXPIRED}
 fun countdownBase(target:Long,wall:Long,elapsed:Long):Long?=(target-wall).takeIf{it in 1..86_400_000L}?.let{elapsed+it}
 fun pixels(dp:Float,density:Float)= (dp.coerceAtLeast(1f)*density.coerceIn(.75f,4f)).toInt().coerceIn(1,640)
 fun temperature(value:Int,available:Boolean)=if(available&&value in -90..65)"$value°" else "—°"
}
