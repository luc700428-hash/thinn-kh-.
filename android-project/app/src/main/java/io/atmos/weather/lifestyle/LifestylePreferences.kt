package io.atmos.weather.lifestyle

/** Preferences influence clothing only; they never change meteorological facts. */
enum class TemperatureComfort(val label: String, val adjustmentC: Double) {
    FEELS_COLD("怕冷", -3.0), BALANCED("一般", 0.0), FEELS_HOT("怕熱", 3.0)
}

enum class ClothingStyle(val label: String) {
    CASUAL("日常休閒"), OFFICE("通勤正式"), SPORT("運動機能")
}

data class LifestylePreferences(
    val comfort: TemperatureComfort = TemperatureComfort.BALANCED,
    val style: ClothingStyle = ClothingStyle.CASUAL,
    /** When any upcoming 6h rain probability meets this level, suggest taking rain protection. */
    val umbrellaThresholdPct: Int = 31
) {
    init { require(umbrellaThresholdPct in 20..90) { "雨具提醒門檻須介於 20% 至 90%" } }
}
