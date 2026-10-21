package io.atmos.weather.briefing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.atmos.weather.AppContainer
import io.atmos.weather.MainActivity
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherBriefingPlan
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.permission.PermissionGuide
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Interval-aware briefing: only real forecast fields are read, no invented weather. */
class WeatherBriefingWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val ctx=applicationContext
        val id=inputData.getInt("slot_id",-1)
        val ms=inputData.getLong("planned_ms",0L)
        val slots=WeatherBriefingStore.load(ctx)
        val slot=slots.firstOrNull { it.id==id && it.enabled } ?: return Result.success()
        val planned=Instant.ofEpochMilli(ms)
        val delay=System.currentTimeMillis()-ms
        // System may defer a task: skip significantly delayed or future/stale work.
        // Do not send a misleading 'current' forecast from a previous schedule.
        try {
            if (ms<=0L || delay < -60_000 || delay > 2*60*60*1000L) return Result.success()
            val prefs=ctx.getSharedPreferences("atmos_settings",Context.MODE_PRIVATE)
            val gps=prefs.contains("gps_lat") && prefs.contains("gps_lon")
            val lat=Double.fromBits(prefs.getLong(if(gps)"gps_lat" else "lat",0L))
            val lon=Double.fromBits(prefs.getLong(if(gps)"gps_lon" else "lon",0L))
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat==0.0 && lon==0.0)) return Result.success()
            if (!PermissionGuide.canNotify(ctx)) return Result.success()
            val city=WeatherCity("briefing", if(gps)prefs.getString("gps_name","目前定位") ?: "目前定位"
                else prefs.getString("city_name","目前城市") ?: "目前城市",GeoPoint(lat,lon))
            val weather=when(val result=AppContainer.repository(ctx).load(city,true)) {
                is WeatherLoadResult.Success -> if(result.stale) null else result.value
                is WeatherLoadResult.Failure -> null
            } ?: return Result.success()
            val next=WeatherBriefingPlan.nextBoundary(slots,planned,ZoneId.systemDefault()) ?: return Result.success()
            val summary=WeatherBriefingMessage.build(weather,Instant.now(),next)
            ThinnNotificationChannels.ensure(ctx)
            val intent=PendingIntent.getActivity(ctx,38000+id,
                Intent(ctx,MainActivity::class.java).putExtra("open_briefing_settings",true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val title="${city.name} · ${summary.title}"
            val n=NotificationCompat.Builder(ctx,ThinnNotificationChannels.channelId(ctx,ThinnNotificationChannels.Type.BRIEFINGS))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(summary.subtitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary.body))
                .setAutoCancel(true).setContentIntent(intent)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
            WeatherNotificationDispatcher.post(ctx,38000+id,n,title,summary.subtitle,XiaomiFocusBridge.Kind.WEATHER_BRIEFING)
            return Result.success()
        } finally {
            // WorkManager persists this next occurrence even across restarts. If the user
            // changed settings during execution, don't re-enable an old deleted slot.
            WeatherBriefingStore.load(ctx).firstOrNull{it.id==id && it.enabled && it==slot}?.let {
                WeatherBriefingScheduler.scheduleNext(ctx,it)
            }
        }
    }
}

data class WeatherBriefingText(val title:String,val subtitle:String,val body:String)
object WeatherBriefingMessage {
    private val format=DateTimeFormatter.ofPattern("週E HH:mm",Locale.TAIWAN)
    fun build(w:WeatherSnapshot,start:Instant,until:Instant):WeatherBriefingText {
        // The reminders are scheduled in the handset's local zone, not the queried city's zone.
        val zone=ZoneId.systemDefault()
        val range="${start.atZone(zone).format(format)} 至 ${until.atZone(zone).format(format)}"
        val hours=w.hourly.filter{!it.at.isBefore(start) && it.at.isBefore(until)}
        val min=hours.mapNotNull{it.temperatureC}.minOrNull()
        val max=hours.mapNotNull{it.temperatureC}.maxOrNull()
        val chance=hours.mapNotNull{it.precipitationProbabilityPct}.maxOrNull()
        val snowChance=hours.mapNotNull{it.snowfallProbabilityPct}.maxOrNull()
        val wind=hours.mapNotNull{it.windKmh}.maxOrNull()
        val uv=hours.mapNotNull{it.uvIndex}.maxOrNull()
        val coverageEnd=hours.lastOrNull()?.at?.plusSeconds(3600)
        val lines=mutableListOf<String>()
        lines+="指定預報區間：$range"
        lines+="目前約 ${w.temperatureC.roundToInt()}°C · ${condition(w.condition)}"
        if(min!=null && max!=null) lines+="預報氣溫約 ${min.roundToInt()}～${max.roundToInt()}°C"
        if(chance!=null) lines+="區間最高降雨機率 $chance%"
        if(snowChance!=null && snowChance>=20) lines+="區間最高降雪機率 $snowChance%"
        if(wind!=null && wind>=20) lines+="較強風速可達約 ${wind.roundToInt()} km/h"
        if(uv!=null && uv>=6) lines+="紫外線偏強（最高約 ${uv.roundToInt()}），外出注意防曬"
        when {
            hours.any{it.weatherCode in listOf(95,96,99)} -> lines+="有雷雨訊號，減少空曠高處的戶外活動。"
            hours.any{it.weatherCode in listOf(71,73,75,77,85,86)} -> lines+="可能降雪，注意道路濕滑及低溫。"
            (chance?:0)>=60 -> lines+="建議攜帶雨具，留意降雨較明顯的時段。"
            (chance?:0)>=30 -> lines+="出門可備妥折疊傘。"
        }
        if((max?:w.temperatureC)>=32) lines+="高溫時補充水分，減少長時間曝曬。"
        if((min?:w.temperatureC)<=13) lines+="留意保暖，準備外套。"
        if((w.usAqi?:0)>=100) lines+="空氣品質模型偏差時，敏感族群減少戶外劇烈活動。"
        if(hours.isEmpty()) lines+="這段時間暫無可用逐時預報，請打開 APP 查看最新資料。"
        if(coverageEnd==null || coverageEnd.isBefore(until)) lines+="預報資料未完整涵蓋至下一次排程；沒有資料的時段不做推測。"
        val sub="${w.temperatureC.roundToInt()}°C · ${condition(w.condition)}" + (chance?.let{" · 最高降雨 $it%"} ?: "")
        return WeatherBriefingText("智慧天氣提醒",sub,lines.joinToString("\n"))
    }
    private fun condition(c:io.atmos.weather.domain.model.WeatherCondition)=when(c) {
        io.atmos.weather.domain.model.WeatherCondition.CLEAR -> "晴朗"
        io.atmos.weather.domain.model.WeatherCondition.PARTLY_CLOUDY -> "多雲時晴"
        io.atmos.weather.domain.model.WeatherCondition.CLOUDY -> "陰或多雲"
        io.atmos.weather.domain.model.WeatherCondition.FOG -> "有霧"
        io.atmos.weather.domain.model.WeatherCondition.HAZE -> "霾"
        io.atmos.weather.domain.model.WeatherCondition.DUST,io.atmos.weather.domain.model.WeatherCondition.DUST_STORM -> "沙塵"
        io.atmos.weather.domain.model.WeatherCondition.RAIN -> "降雨"
        io.atmos.weather.domain.model.WeatherCondition.FREEZING_RAIN -> "凍雨"
        io.atmos.weather.domain.model.WeatherCondition.SLEET -> "雨夾雪"
        io.atmos.weather.domain.model.WeatherCondition.SNOW -> "降雪"
        io.atmos.weather.domain.model.WeatherCondition.HAIL -> "冰雹"
        io.atmos.weather.domain.model.WeatherCondition.THUNDERSTORM -> "雷雨"
        else -> "天氣變化"
    }
}
