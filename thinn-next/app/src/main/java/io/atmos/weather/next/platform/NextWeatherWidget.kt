package io.atmos.weather.next.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.atmos.weather.next.MainActivity
import io.atmos.weather.next.R
import io.atmos.weather.next.domain.*
import org.json.JSONObject

/** Launcher widgets cannot run a continuous 60fps animation. Mood changes are event driven. */
class NextWeatherWidget : AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        for(id in ids) updateOne(context,manager,id)
    }
    companion object {
        fun refresh(context:Context) {
            val mgr=AppWidgetManager.getInstance(context)
            val ids=mgr.getAppWidgetIds(ComponentName(context,NextWeatherWidget::class.java))
            for(id in ids) updateOne(context,mgr,id)
        }
        private fun updateOne(ctx:Context,mgr:AppWidgetManager,id:Int) {
            val prefs=ctx.getSharedPreferences("next_weather_cache_v1",Context.MODE_PRIVATE)
            val city=ctx.getSharedPreferences("next_user_v1",Context.MODE_PRIVATE)
                .getString("city","taipei") ?: "taipei"
            val raw=prefs.getString("city_$city",null)
            val data=runCatching{ JSONObject(raw ?: "") }.getOrNull()
            val condition=data?.optInt("code",-1)?.let { WmoWeatherCodes.scene(it) }
            val temperature=data?.optDouble("temp",Double.NaN)?.takeIf { it.isFinite() }
            val observed=data?.optLong("observed",0L) ?: 0L
            val label=KnownCities.cities.firstOrNull { it.id==city }?.name ?: "天氣"
            val stale=DataFreshnessPolicy.classify(observed,System.currentTimeMillis())!=DataFreshness.FRESH
            val v=RemoteViews(ctx.packageName,R.layout.widget_next)
            v.setTextViewText(R.id.widget_title,"thinn-khì NEXT · $label")
            v.setTextViewText(R.id.widget_mood,condition?.emoji ?: "☁️")
            v.setTextViewText(R.id.widget_temperature,temperature?.let { "${it.toInt()}° · ${condition?.label ?: "未知天氣"}" } ?: "尚未取得天氣")
            v.setTextViewText(R.id.widget_status,if(stale)"上次資料 · 非即時 · 點擊更新" else "天氣已取得 · 點擊開啟")
            val intent=Intent(ctx,MainActivity::class.java)
            val pending=PendingIntent.getActivity(ctx,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_mood,pending)
            v.setOnClickPendingIntent(R.id.widget_temperature,pending)
            mgr.updateAppWidget(id,v)
        }
    }
}
