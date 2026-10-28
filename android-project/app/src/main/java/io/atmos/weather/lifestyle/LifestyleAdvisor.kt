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
    val weatherFresh:Boolean,
    val extraReminders:List<String> = emptyList()
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
        val coldNow=minOf(weather.temperatureC,observedFeels)
        val current=(if(coldNow<21) coldNow else observedFeels)+preferences.comfort.adjustmentC
        val future=weather.hourly.filter {it.at>=now.minusSeconds(3600)&&it.at<=now.plusSeconds(6*3600)}
        val laterMinimum=future.mapNotNull{it.temperatureC?.takeIf(Double::isFinite)}.minOrNull()
        val coldestActual=minOf(weather.temperatureC,laterMinimum?:weather.temperatureC)
        val coatRequired=weather.temperatureC<21||current<21||coldestActual<21
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
            minOf(current,weather.temperatureC)<4 -> {top="保暖內搭＋毛衣或厚長袖";bottoms="厚長褲加保暖內搭";layer="厚外套與防風外層，準備帽子、圍巾與手套"}
            minOf(current,weather.temperatureC)<10 -> {top="保暖內搭＋厚長袖";bottoms="保暖長褲，避免露出腳踝";layer="厚外套或保暖中層＋防風外套"}
            current<16 -> {top="長袖或針織上衣";bottoms="舒適保暖長褲";layer="保暖外套，方便依室內外溫度加減"}
            current<21 -> {top="長袖上衣或薄針織";bottoms="長褲（牛仔褲／棉質長褲）";layer="出門帶件外套，清晨夜晚可加一層"}
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
        val adjustedLayer=when {
            coldestActual<4 -> "準備厚外套、防風層與帽子圍巾；稍後低溫時完整保暖"
            coldestActual<10 -> "保暖外套＋防風層，低於 10°C 的時段注意完整保暖"
            mode==OutingMode.MOTORBIKE&&current<25 -> "防風外套；天冷時搭配保暖中層"
            coatRequired&&current>=21 -> "帶件外套備用，預報稍後或戶外氣溫低於 21°C"
            else -> layer
        }
        val extras=buildList {
            if(coldestActual<4)add("預報有低於 4°C 的時段，特別留意低溫；長者、幼兒外出多一層保暖。")
            else if(coldestActual<10)add("氣溫低於 10°C 的時段請完整保暖，風大或下雨時加防風、防水外層。")
            if(laterMinimum!=null&&weather.temperatureC-laterMinimum>=6)add("未來六小時可能降溫約 ${(weather.temperatureC-laterMinimum).roundToInt()}°C，外套別留在家。")
            if(coatRequired&&(weather.windKmh?:0.0)>=18)add("風勢會加重冷感，外層選防風材質；騎車時留意側風。")
            if(coatRequired&&currentRain)add("濕冷時衣物保持乾燥，雨衣內仍要留保暖層。")
            if((weather.currentUv?:0.0)>=6)add("紫外線偏強，準備防曬、帽子與飲水。")
        }
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
            coldestActual<4 -> extras.first()
            coldestActual<10 -> extras.first()
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
            coldestActual<4 -> "低於 4°C，特別留意低溫"
            coldestActual<10 -> "低於 10°C，完整保暖再出門"
            currentRain -> "今天出門記得帶雨具"
            coatRequired -> if(weather.temperatureC<21)"低於 21°C，出門帶件外套" else "稍後轉涼，記得帶件外套"
            current >= 30 -> "今天偏熱，穿得清爽一些"
            current < 12 -> "今天偏冷，注意保暖"
            current < 19 -> "適合洋蔥式穿搭"
            else -> "今天適合輕鬆舒適穿搭"
        }
        return OutfitPlan(headline, adjustedTop, adjustedBottoms, adjustedLayer, shoes, umbrella, notice,evidence,isFresh,extras)
    }
}
