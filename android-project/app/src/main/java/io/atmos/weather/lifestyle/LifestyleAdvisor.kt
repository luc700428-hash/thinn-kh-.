package io.atmos.weather.lifestyle

import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Deterministic, auditable advice. Does not contact a language model. */
enum class OutingMode(val title: String) {
    DAILY("休閒"), WALK("運動"), MOTORBIKE("通勤"), OUTDOORS("戶外")
}

data class OutfitPlan(
    val headline: String,
    val top: String,
    val bottoms: String,
    val layer: String,
    val shoes: String,
    val umbrella: String,
    val reminder: String,
    val evidence: String,
    val weatherFresh: Boolean
)

object LifestyleAdvisor {
    /** At 2h old, data may still be displayed with a clear stale warning but is not authoritative. */
    fun isFresh(weather: WeatherSnapshot, now: Instant = Instant.now()): Boolean =
        !weather.fetchedAt.isAfter(now.plusSeconds(120)) &&
            Duration.between(weather.fetchedAt, now).seconds in 0..7200 &&
            Duration.between(weather.observedAt, now).seconds in -120..10_800

    fun recommend(weather: WeatherSnapshot, mode: OutingMode, isStale: Boolean = false,
                  now: Instant = Instant.now(), preferences: LifestylePreferences = LifestylePreferences()): OutfitPlan {
        val observedFeels = weather.feelsLikeC ?: weather.temperatureC
        val current = observedFeels + preferences.comfort.adjustmentC
        val future = weather.hourly.filter { !it.at.isBefore(weather.observedAt) &&
            Duration.between(weather.observedAt, it.at).toMinutes() <= 360 }
        val forecastRain = future.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        val currentRain = weather.condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM,
            WeatherCondition.HAIL, WeatherCondition.FREEZING_RAIN, WeatherCondition.SLEET)
        val highWind = (weather.windGustKmh ?: weather.windKmh ?: 0.0) > 54.0
        val moderateRain = forecastRain != null && forecastRain >= (preferences.umbrellaThresholdPct - 20).coerceAtLeast(20)
        val rainExpected = currentRain || (forecastRain != null && forecastRain >= preferences.umbrellaThresholdPct)
        val isFresh = !isStale && isFresh(weather, now)
        val top: String
        val bottoms: String
        val layer: String
        when {
            current < 12 -> { top="保暖內搭＋毛衣或厚長袖"; bottoms="厚長褲或加內搭保暖褲"; layer="厚外套或防風羽絨外套" }
            current < 20 -> { top="長袖上衣或薄針織"; bottoms="長褲（牛仔褲／棉質長褲）"; layer="薄外套，清晨夜晚可加一層" }
            current <= 28 -> { top="透氣中長袖上衣"; bottoms="舒適長褲"; layer="按體感帶薄外套" }
            current < 30 -> { top="透氣棉質短袖或排汗衣"; bottoms="輕薄短褲"; layer="避免悶熱厚外套，冷氣房可帶薄罩衫" }
            else -> { top="寬鬆、淺色、排汗短袖"; bottoms="透氣短褲或薄長褲"; layer="薄防曬外搭，避免悶熱厚外套" }
        }
        val styledTop = when {
            current < 12 -> top // Never sacrifice warmth for dress code.
            preferences.style == ClothingStyle.OFFICE && current < 25 -> "襯衫或有領薄長袖，依體感加針織外搭"
            preferences.style == ClothingStyle.OFFICE -> "透氣短袖襯衫或輕薄有領上衣"
            preferences.style == ClothingStyle.SPORT && current >= 19 -> "透氣排汗機能上衣，避開過厚材質"
            else -> top
        }
        val styledBottoms = when {
            current < 12 -> bottoms
            preferences.style == ClothingStyle.OFFICE -> "舒適、有彈性的輕薄長褲或西裝長褲"
            preferences.style == ClothingStyle.SPORT -> "活動方便的機能長褲或運動短褲"
            else -> bottoms
        }
        val adjustedBottoms = if (mode == OutingMode.MOTORBIKE) "包覆腿部的透氣長褲，避免短褲騎車" else styledBottoms
        val adjustedTop = when {
            mode == OutingMode.WALK && current >= 20 -> "吸濕排汗運動上衣，適合跑步與運動"
            mode == OutingMode.OUTDOORS && current >= 25 -> "透氣排汗上衣，長時間日曬可加薄防曬長袖"
            else -> styledTop
        }
        val adjustedLayer = if (mode == OutingMode.MOTORBIKE && current < 25) "防風外套；天冷時搭配保暖中層" else layer
        val umbrella = when {
            highWind && (rainExpected || moderateRain) -> "風勢強，不建議撐傘；改準備防水外套並注意安全"
            currentRain -> "要帶！目前有降雨，請準備雨傘或雨衣"
            rainExpected -> "建議帶折疊傘／輕便雨衣，未來六小時可能下雨"
            moderateRain -> "可以帶折疊傘備用，預報有降雨可能"
            forecastRain == null -> "尚缺未來降雨機率，出門前再確認雷達與官方預報"
            else -> "依目前六小時預報可不特別帶傘，仍請留意天氣變化"
        }
        val shoes = when {
            currentRain || rainExpected -> "包覆式防滑、防潑水鞋，避免易滑鞋底"
            mode == OutingMode.OUTDOORS -> "舒適防滑的步行／運動鞋"
            mode == OutingMode.MOTORBIKE -> "包覆腳趾的鞋，避免拖鞋"
            else -> "好走的休閒鞋或運動鞋"
        }
        val notice = when {
            !isFresh -> "目前資料較舊或處於離線狀態，以上僅供參考；請先刷新天氣資料。"
            weather.condition == WeatherCondition.THUNDERSTORM -> "有雷雨，先避開空曠高處與戶外活動，依官方警報行動。"
            mode == OutingMode.MOTORBIKE && (currentRain || rainExpected) -> "機車通勤請帶合身雨衣，路面濕滑時減速；雷雨或強風時優先考慮延後出發。"
            mode == OutingMode.OUTDOORS && (weather.currentUv ?: 0.0) >= 6 -> "紫外線偏強，準備帽子、防曬與飲水，適時休息。"
            (weather.usAqi ?: 0) > 100 -> "空氣品質模型顯示可能不佳，敏感族群可縮短戶外活動。"
            (weather.windGustKmh ?: 0.0) >= 55.0 -> "陣風較強，避免高處與無固定物品附近，留意官方強風資訊。"
            else -> "出門前看一下短時預報；長時間在外可準備飲水並注意天氣變化。"
        }
        val probability = forecastRain?.let { "；未來六小時最高降雨機率 $it%" } ?: "；六小時降雨機率未提供"
        val evidence = "體感 ${observedFeels.roundToInt()}°C（氣溫 ${weather.temperatureC.roundToInt()}°C；穿搭偏好 ${preferences.comfort.label}）$probability" +
            weather.windKmh?.let { "；風速 ${it.roundToInt()} km/h" }.orEmpty() +
            "；帶傘提醒門檻 ${preferences.umbrellaThresholdPct}%" +
            "；資料：${weather.sourceId}"
        val headline = when {
            !isFresh -> "天氣資訊可能已過時"
            currentRain -> "今天出門記得帶雨具"
            current >= 30 -> "今天偏熱，穿得清爽一些"
            current < 12 -> "今天偏冷，注意保暖"
            current < 19 -> "適合洋蔥式穿搭"
            else -> "今天適合輕鬆舒適穿搭"
        }
        return OutfitPlan(headline, adjustedTop, adjustedBottoms, adjustedLayer, shoes, umbrella, notice, evidence, isFresh)
    }
}
