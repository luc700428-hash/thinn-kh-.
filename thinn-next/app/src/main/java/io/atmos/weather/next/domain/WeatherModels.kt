package io.atmos.weather.next.domain

/** This is a taxonomy of 14 renderable states, not 14 fabricated forecast categories. */
enum class WeatherScene(val label: String, val emoji: String) {
    CLEAR("晴朗", "☀️"), PARTLY_CLOUDY("晴時多雲", "🌤️"), CLOUDY("多雲", "☁️"),
    FOG("起霧", "🌫️"), HAZE("霾", "🌁"), DUST("沙塵", "🌪️"),
    DUST_STORM("沙塵暴", "🌪️"), DRIZZLE("毛毛雨", "🌦️"),
    RAIN("降雨", "🌧️"), FREEZING_RAIN("凍雨", "🌧️"),
    SLEET("雨夾雪", "🌨️"), SNOW("降雪", "❄️"),
    HAIL("冰雹", "🧊"), THUNDERSTORM("雷雨", "⛈️")
}

/** WMO weather codes only; sources without a recognized code are not a sunny day. */
object WmoWeatherCodes {
    fun scene(code: Int): WeatherScene? = when (code) {
        0 -> WeatherScene.CLEAR
        1, 2 -> WeatherScene.PARTLY_CLOUDY
        3 -> WeatherScene.CLOUDY
        45, 48 -> WeatherScene.FOG
        51, 53, 55 -> WeatherScene.DRIZZLE
        56, 57, 66, 67 -> WeatherScene.FREEZING_RAIN
        61, 63, 65, 80, 81, 82 -> WeatherScene.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherScene.SNOW
        95 -> WeatherScene.THUNDERSTORM
        96, 99 -> WeatherScene.HAIL
        else -> null // e.g. 999: show unknown rather than making up conditions
    }
}

data class WeatherCity(val id: String, val name: String, val lat: Double, val lon: Double)
object KnownCities {
    val cities = listOf(
        WeatherCity("taipei", "臺北", 25.0330, 121.5654),
        WeatherCity("chiayi", "嘉義", 23.4801, 120.4491),
        WeatherCity("kaohsiung", "高雄", 22.6273, 120.3014),
        WeatherCity("hualien", "花蓮", 23.9769, 121.6045),
        WeatherCity("kinmen", "金門", 24.4321, 118.3170),
        WeatherCity("penghu", "澎湖", 23.5712, 119.5793)
    )
}

data class WeatherReading(
    val cityId: String,
    val temperatureC: Double,
    val feelsLikeC: Double?,
    val humidityPct: Int?,
    val windKmh: Double?,
    val precipitationMm: Double?,
    val wmoCode: Int,
    val observedEpochMs: Long,
    val receivedEpochMs: Long,
    val source: String = "Open-Meteo"
) {
    val scene: WeatherScene? get() = WmoWeatherCodes.scene(wmoCode)
}

enum class DataFreshness { FRESH, OLD, FUTURE, UNKNOWN }
object DataFreshnessPolicy {
    const val MAX_AGE_MS: Long = 2L * 60L * 60L * 1000L
    fun classify(observedMs: Long, nowMs: Long): DataFreshness = when {
        observedMs <= 0L || nowMs <= 0L -> DataFreshness.UNKNOWN
        observedMs > nowMs + 30L * 60L * 1000L -> DataFreshness.FUTURE
        nowMs - observedMs > MAX_AGE_MS -> DataFreshness.OLD
        else -> DataFreshness.FRESH
    }
    fun display(reading: WeatherReading, nowMs: Long, fromCache: Boolean): String {
        val freshness = classify(reading.observedEpochMs, nowMs)
        return when {
            fromCache -> "上次資料 · 非即時"
            freshness == DataFreshness.FRESH -> "Open-Meteo · 最新取得"
            freshness == DataFreshness.FUTURE -> "來源時間異常 · 非即時"
            freshness == DataFreshness.OLD -> "資料已過期 · 非即時"
            else -> "時間無法驗證 · 非即時"
        }
    }
}

sealed interface WeatherLoad {
    data class Live(val reading: WeatherReading): WeatherLoad
    data class Cached(val reading: WeatherReading, val reason: String): WeatherLoad
    data class Unavailable(val explanation: String): WeatherLoad
}
