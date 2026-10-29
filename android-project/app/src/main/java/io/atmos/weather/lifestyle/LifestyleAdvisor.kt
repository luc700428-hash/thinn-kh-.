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
    val extraReminders:List<String> = emptyList(),
    val coatDecision:String = layer,
    val temperatureSummary:String = "",
    val timeAdvice:String = "",
    val bring:List<String> = emptyList()
)

object LifestyleAdvisor {
    /** At 2h old, data may still be displayed with a clear stale warning but is not authoritative. */
    fun isFresh(weather: WeatherSnapshot, now: Instant = Instant.now()): Boolean =
        !weather.fetchedAt.isAfter(now.plusSeconds(120)) &&
            Duration.between(weather.fetchedAt, now).seconds in 0..7200 &&
            Duration.between(weather.observedAt, now).seconds in -120..10_800

    fun recommend(weather: WeatherSnapshot, mode: OutingMode, isStale: Boolean = false,
                  now: Instant = Instant.now(), preferences: LifestylePreferences = LifestylePreferences()): OutfitPlan {
        val observedFeels = weather.feelsLikeC?.takeIf{it.isFinite()&&it in -90.0..70.0} ?: weather.temperatureC
        val coldNow=minOf(weather.temperatureC,observedFeels)
        val current=(if(coldNow<21) coldNow else observedFeels)+preferences.comfort.adjustmentC
        val future=weather.hourly.filter {it.at>=now&&it.at<=now.plusSeconds(6*3600)}.sortedBy{it.at}
        val laterMinimum=future.mapNotNull{it.temperatureC?.takeIf(Double::isFinite)}.minOrNull()
        val coldestActual=minOf(weather.temperatureC,laterMinimum?:weather.temperatureC)
        val coldestFeels=minOf(observedFeels,future.mapNotNull{it.feelsLikeC?.takeIf(Double::isFinite)}.minOrNull()?:observedFeels)
        // Preferences may add warmth; they cannot suppress a measured/forecast low-temperature reminder.
        val coldest=minOf(coldestActual,coldestFeels)
        val coatRequired=weather.temperatureC<21||current<21||coldestActual<21||coldestFeels<21
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
            coldest<4 -> "保暖內搭＋保暖中層＋厚外套，外層防風防水；帽子、圍巾、手套與厚襪一起準備"
            coldest<10 -> "保暖內搭＋刷毛或針織中層＋保暖外套；風雨中再加防風防水層"
            mode==OutingMode.MOTORBIKE&&current<25 -> "防風外套；天冷時搭配保暖中層"
            coatRequired&&current>=21 -> "帶件外套備用，預報稍後或戶外氣溫低於 21°C"
            else -> layer
        }
        val extras=buildList {
            if(coldest<4)add("氣溫或體感有低於 4°C 的時段，特別留意低溫；頭頸與四肢保持溫暖，長者、幼兒避免久留戶外。")
            else if(coldest<10)add("氣溫或體感低於 10°C 的時段請完整保暖，風大或下雨時加防風、防水外層。")
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
            coldest<4 -> extras.first()
            coldest<10 -> extras.first()
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
            coldest<4 -> "低於 4°C，特別留意低溫"
            coldest<10 -> "低於 10°C，完整保暖再出門"
            currentRain -> "今天出門記得帶雨具"
            coatRequired -> if(weather.temperatureC<21)"低於 21°C，出門帶件外套" else "稍後轉涼，記得帶件外套"
            current >= 30 -> "今天偏熱，穿得清爽一些"
            current < 12 -> "今天偏冷，注意保暖"
            current < 19 -> "適合洋蔥式穿搭"
            else -> "今天適合輕鬆舒適穿搭"
        }
        val coat=when {
            coldest<4 -> "要，厚外套並完整保暖；帽子、圍巾、手套別漏掉"
            coldest<10 -> "要，保暖外套搭配中層，頭頸與腳踝也要遮好"
            coatRequired -> if(coldNow<21)"要，現在偏涼，穿件可脫的外套" else "要帶，現在不冷但六小時內會轉涼"
            mode==OutingMode.MOTORBIKE&&current<25 -> "帶防風薄外套，騎車迎風會更涼"
            preferences.comfort==TemperatureComfort.FEELS_COLD -> "帶一件薄外套，冷氣房和傍晚可加上"
            current>=28 -> "一般不需保暖外套；日曬可選透氣防曬薄外搭"
            else -> "一般不需厚外套；久待冷氣房可帶薄罩衫"
        }
        val timeFormat=java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(
            runCatching{java.time.ZoneId.of(weather.timeZoneId?:"Asia/Taipei")}.getOrDefault(java.time.ZoneId.of("Asia/Taipei")))
        val coldHour=future.filter{it.temperatureC?.isFinite()==true||it.feelsLikeC?.isFinite()==true}.minByOrNull{minOf(it.temperatureC?:Double.POSITIVE_INFINITY,it.feelsLikeC?:Double.POSITIVE_INFINITY)}
        val wetHour=future.firstOrNull{(it.precipitationProbabilityPct?:-1)>=preferences.umbrellaThresholdPct}
        val timeAdvice=when {
            !isFresh -> "資料較舊，重新整理後再安排外出時段。"
            weather.condition==WeatherCondition.THUNDERSTORM -> "先避開戶外活動，等雷雨遠離再出發；勿在樹下或空曠處停留。"
            highWind -> "陣風較強，避開海邊、高處與招牌；風雨大時優先延後非必要外出。"
            wetHour!=null -> "${timeFormat.format(wetHour.at)} 前後有較高降雨機率，出發先備好${if(mode==OutingMode.MOTORBIKE)"雨衣" else "雨具"}；機率不是整段都會下雨。"
            coldHour!=null && coldest<21 -> {
                val value=minOf(coldHour.temperatureC?:Double.POSITIVE_INFINITY,coldHour.feelsLikeC?:Double.POSITIVE_INFINITY)
                "六小時內 ${timeFormat.format(coldHour.at)} 前後較涼，氣溫或體感約 ${value.roundToInt()}°C；回程也把外套帶著。"
            }
            mode==OutingMode.WALK && current<16 -> "先在室內暖身再出門，運動後擦乾汗、穿回外套，別穿濕衣吹風。"
            (weather.currentUv?:0.0)>=6 -> "目前紫外線偏強，走有遮蔭的路，補充飲水並適時休息。"
            future.isEmpty() -> "尚缺未來六小時預報，出門前再確認天氣與回程安排。"
            else -> "六小時內未達你的帶傘門檻，仍留意回程天氣；室內外溫差大時分層穿脫。"
        }
        val summary="現在 ${weather.temperatureC.roundToInt()}°C · 體感 ${observedFeels.roundToInt()}°C"+
            laterMinimum?.let{" · 六小時最低氣溫 ${it.roundToInt()}°C"}.orEmpty()
        val bring=buildList {
            if(coatRequired)add(if(coldest<10)"保暖外套" else "薄外套")
            if(coldest<10)add("帽子／圍巾")
            if(currentRain||rainExpected||moderateRain)add(if(mode==OutingMode.MOTORBIKE||highWind)"合身雨衣" else "折疊傘")
            if((weather.currentUv?:0.0)>=3)add("防曬／帽子")
            if(current>=25||mode==OutingMode.WALK||mode==OutingMode.OUTDOORS)add("飲水")
        }.distinct().take(5)
        val warmBottoms=if(coldest<10)"保暖長褲＋襪子，必要時加保暖內搭；別讓腳踝受風" else adjustedBottoms
        return OutfitPlan(headline, adjustedTop, warmBottoms, adjustedLayer, shoes, umbrella, notice,evidence,isFresh,extras,
            coat,summary,timeAdvice,bring)
    }
}
