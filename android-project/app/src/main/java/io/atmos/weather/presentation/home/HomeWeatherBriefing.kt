package io.atmos.weather.presentation.home

import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure, testable weather summary for the home screen. No invented measurements. */
data class HomeWeatherFact(val label:String, val value:String, val note:String)

object HomeWeatherBriefing {
    fun build(weather:WeatherSnapshot):List<HomeWeatherFact> {
        val upcoming=weather.hourly.filter {
            val minutes=Duration.between(weather.observedAt,it.at).toMinutes()
            minutes in 0..360
        }.sortedBy { it.at }
        val rainMax=upcoming.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        val rain=HomeWeatherFact("未來六小時降雨",rainMax?.let { "$it%" } ?: "—",
            if(rainMax==null) "尚無降雨機率資料" else "期間最高預報機率")
        val firstTemp=upcoming.firstNotNullOfOrNull { it.temperatureC }
        val lastTemp=upcoming.lastOrNull { it.temperatureC != null }?.temperatureC
        val change=if(firstTemp!=null&&lastTemp!=null)lastTemp-firstTemp else null
        val trend=HomeWeatherFact("未來六小時氣溫",when {
            change==null -> "—"
            change >= 1.0 -> "上升 ${change.roundToInt()}°"
            change <= -1.0 -> "下降 ${abs(change).roundToInt()}°"
            else -> "變化不大"
        },if(change==null) "尚無連續氣溫預報" else "依逐時預報計算")
        val wind=weather.windKmh
        val windFact=HomeWeatherFact("目前風速",wind?.let { "${it.roundToInt()} km/h" } ?: "—",
            when {
                wind==null -> "尚無風速資料"
                (weather.windGustKmh ?: 0.0)>=55 -> "陣風較強，外出留意安全"
                else -> "平均風速"
            })
        val airFact=if(weather.usAqi!=null) HomeWeatherFact("空氣品質",
            "AQI ${weather.usAqi}","美國標準模型推估，非臺灣環境部官方 AQI")
        else HomeWeatherFact("相對濕度",weather.humidityPct?.let { "$it%" } ?: "—",
            if(weather.humidityPct==null) "尚無濕度資料" else "目前觀測／估計")
        return listOf(rain,trend,windFact,airFact)
    }
}
