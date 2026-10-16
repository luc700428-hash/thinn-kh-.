package io.atmos.weather.domain.model

import java.time.Duration
import java.time.ZoneId
import kotlin.math.roundToInt

data class WeatherAdvice(val heading: String, val detail: String, val priority: Int)

/** Explainable forecast-based advice; no cloud LLM, no fabricated forecasts. */
object WeatherAdviceEngine {
    /** Local-time greeting, separate from official weather alert claims. */
    fun daypart(weather: WeatherSnapshot): String {
        val zone = runCatching { ZoneId.of(weather.timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei"))
        return when (weather.observedAt.atZone(zone).hour) {
            in 0..2 -> "半夜"
            in 3..4 -> "凌晨"
            in 5..7 -> "清晨"
            in 8..10 -> "早上"
            in 11..13 -> "中午"
            in 14..16 -> "下午"
            in 17..19 -> "傍晚"
            else -> "晚上"
        }
    }
    fun suggest(weather: WeatherSnapshot, pm25Override: Double? = null, visibilityOverrideKm: Double? = null): List<WeatherAdvice> {
        val suggestions = mutableListOf<WeatherAdvice>()
        val now = weather.observedAt
        val nextSixHours = weather.hourly.filter {
            !it.at.isBefore(now) && Duration.between(now,it.at).toHours() <= 6
        }
        val upcomingRain = nextSixHours.firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 60 }
        val upcomingSnow = nextSixHours.firstOrNull { (it.snowfallProbabilityPct ?: 0) >= 45 || (it.snowfallCm ?: 0.0) > 0.1 }
        val snowActive = weather.condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET)
        val icy = weather.condition == WeatherCondition.FREEZING_RAIN
        if (snowActive || upcomingSnow != null) {
            val probability = nextSixHours.mapNotNull { it.snowfallProbabilityPct }.maxOrNull()
            val status = if (probability != null) "預報降雪機率最高約 $probability%" else "預報顯示可能有降雪（未提供獨立降雪機率）"
            suggestions += WeatherAdvice("降雪與道路結冰提醒", "$status；留意高海拔路段與橋面結冰，避免無防滑準備的行程。", 10)
        }
        if (icy) suggestions += WeatherAdvice("凍雨風險", "雨滴可能落地結冰，濕滑路面與橋梁風險高，請避免不必要的行車。",10)
        if (weather.condition == WeatherCondition.HAIL) suggestions += WeatherAdvice("冰雹與強對流", "遠離戶外空曠區域，避開玻璃棚架與高處，依官方警報安排活動。",10)
        when {
            weather.condition == WeatherCondition.THUNDERSTORM ->
                suggestions += WeatherAdvice("雷雨期間請留意安全", "減少戶外活動，遠離空曠高處；依官方最新警報安排外出。", 10)
            weather.condition == WeatherCondition.RAIN ->
                suggestions += WeatherAdvice("外出記得攜帶雨具", "目前有降雨，留意路面濕滑與能見度。", 8)
            upcomingRain != null && !snowActive && !icy && upcomingSnow == null -> {
                val hrs = Duration.between(now, upcomingRain.at).toHours().coerceAtLeast(0)
                suggestions += WeatherAdvice("未來 $hrs 小時內降雨機率提高", "預報該時段降雨機率約 ${upcomingRain.precipitationProbabilityPct}%，建議備妥雨具。", 7)
            }
        }
        val feels = weather.feelsLikeC ?: weather.temperatureC
        when {
            feels < 12 -> suggestions += WeatherAdvice("需要保暖", "體感約 ${feels.roundToInt()}°C，建議穿著保暖外套與多層次衣物。", 7)
            feels < 20 -> suggestions += WeatherAdvice("適合薄外套", "體感約 ${feels.roundToInt()}°C，可搭配長袖或輕薄外套。", 4)
            feels >= 30 -> suggestions += WeatherAdvice("天氣較熱", "體感約 ${feels.roundToInt()}°C，建議穿透氣衣物、補充水分並避免長時間曝曬。", 7)
            else -> suggestions += WeatherAdvice("穿搭建議", "體感約 ${feels.roundToInt()}°C，建議舒適透氣的日常衣物。", 3)
        }
        if (weather.condition in setOf(WeatherCondition.HAZE, WeatherCondition.DUST, WeatherCondition.DUST_STORM)) {
            val severe = weather.condition == WeatherCondition.DUST_STORM
            suggestions += WeatherAdvice(if(severe) "疑似強烈揚塵影響視線" else "空氣中氣膠／沙塵偏多",
                if(severe) "模型顯示粉塵、風速及低能見度同時升高；建議減少外出，關閉迎風窗戶並查詢官方警報。此非官方沙塵暴通報。"
                else "敏感族群減少戶外運動；室內視空氣品質選擇關窗與開啟清淨機，模型資料不是官方警報。", 9)
        }
        if ((weather.currentUv ?: 0.0) >= 6) suggestions += WeatherAdvice("紫外線偏強", "外出建議遮陽、防曬，並留意長時間日曬。", 6)
        if (weather.condition == WeatherCondition.FOG || (visibilityOverrideKm?.times(1000.0) ?: weather.visibilityM ?: Double.MAX_VALUE) < 1000.0) {
            suggestions += WeatherAdvice("能見度較差", "可能有霧，行車請減速並依實際路況使用燈光。", 8)
        }
        val pm25 = pm25Override ?: weather.pm25
        val usAqi = weather.usAqi
        // US AQI and PM2.5 are distinct measurements. Do not treat AQI as a PM2.5 concentration,
        // or label the Open-Meteo model estimate as Taiwan's official air-quality index.
        if (usAqi != null && usAqi > 100) {
            val category = when {
                usAqi >= 301 -> "危害"
                usAqi >= 201 -> "非常不健康"
                usAqi >= 151 -> "不健康"
                else -> "對敏感族群不健康"
            }
            val pm25Detail = if (pm25 != null && pm25 > 35.0)
                "；模型 PM2.5 約 ${pm25.roundToInt()} μg/m³" else ""
            suggestions += WeatherAdvice("空氣品質需留意", "Open-Meteo 模型的美國 AQI 約 $usAqi（$category）$pm25Detail；敏感族群減少長時間戶外活動。這不是臺灣環境部的官方 AQI。", if (usAqi >= 151) 9 else 7)
        } else if (pm25 != null && pm25 > 35.0) {
            suggestions += WeatherAdvice("空氣品質需留意", "Open-Meteo 模型的 PM2.5 約 ${pm25.roundToInt()} μg/m³，敏感族群可減少長時間戶外活動；這不是官方實測 AQI。", 7)
        }
        val nextThree = nextSixHours.take(3)
        val maxChance = nextSixHours.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        if (maxChance != null && maxChance in 30..59 && upcomingRain == null && !snowActive && upcomingSnow == null) {
            suggestions += WeatherAdvice("出門前留意降雨趨勢", "未來六小時最高降雨機率約 $maxChance%，可準備折疊傘並留意後續預報。",5)
        }
        if ((weather.windGustKmh ?: 0.0) >= 55.0) {
            suggestions += WeatherAdvice("強陣風請留意", "瞬間陣風約 ${weather.windGustKmh?.roundToInt()} km/h，請固定陽台物品，騎車與臨海活動注意側風。", 8)
        }
        if ((weather.windKmh ?: 0.0) >= 28.0) {
            suggestions += WeatherAdvice("風勢較強，留意戶外物品", "目前風速約 ${weather.windKmh?.roundToInt()} km/h；騎車、海岸及高處活動請特別留意。",7)
        }
        if ((weather.humidityPct ?: 0) >= 80 && feels >= 25) {
            suggestions += WeatherAdvice("濕度偏高，衣著以透氣為主", "相對濕度約 ${weather.humidityPct}%，體感可能悶熱；可選吸濕排汗衣物。",4)
        }
        if ((weather.humidityPct ?: 100) <= 35) {
            suggestions += WeatherAdvice("環境較乾燥", "目前相對濕度約 ${weather.humidityPct}%，外出可適度補充水分。",4)
        }
        val later = nextSixHours.lastOrNull()
        if (later != null && nextThree.isNotEmpty()) {
            val startTemp=nextThree.first().temperatureC
            val endTemp=later.temperatureC
            if(startTemp != null && endTemp != null && kotlin.math.abs(endTemp-startTemp)>=4.0) {
                suggestions += WeatherAdvice("未來數小時氣溫變化明顯", "預報氣溫由約 ${startTemp.roundToInt()}°C 變為 ${endTemp.roundToInt()}°C，建議準備能增減的衣物。",5)
            }
        }
        // Additional actionable details; only if the needed forecast variable exists.
        val wind=weather.windKmh
        if (wind != null && wind in 18.0..27.99) suggestions += WeatherAdvice("戶外活動留意陣風", "目前平均風速約 ${wind.roundToInt()} km/h，攜帶帽子、雨傘或騎車時留意陣風與道路狀況。", 4)
        if (weather.isDay && (weather.currentUv ?: 0.0) in 3.0..5.99) suggestions += WeatherAdvice("日間適度防曬", "紫外線指數約 ${weather.currentUv?.roundToInt()}，戶外活動時間長可準備帽子、防曬用品。", 4)
        val laterRain=nextSixHours.lastOrNull { (it.precipitationProbabilityPct ?: 0) >= 40 }
        if (laterRain != null && upcomingRain == null && !snowActive && upcomingSnow == null && !icy && weather.condition != WeatherCondition.RAIN) {
            val hrs=Duration.between(now,laterRain.at).toHours().coerceAtLeast(0)
            suggestions+=WeatherAdvice("${hrs} 小時內可能有雨", "${laterRain.at.atZone(runCatching{ZoneId.of(weather.timeZoneId ?: "Asia/Taipei")}.getOrDefault(ZoneId.of("Asia/Taipei"))).hour} 時左右的降雨機率約 ${laterRain.precipitationProbabilityPct}%；長時間外出可帶輕便雨具。",5)
        }
        if (weather.isDay && (weather.sunset != null)) {
            val sunset=weather.sunset
            val hoursUntil=Duration.between(now,sunset).toMinutes()
            if(hoursUntil in 1..150) suggestions+=WeatherAdvice("日落將近", "預計約 ${hoursUntil/60} 小時 ${hoursUntil%60} 分後日落；傍晚戶外活動或行車留意光線變化。",4)
        }
        return suggestions.distinctBy { it.heading }.sortedByDescending { it.priority }.take(9)
    }
}
