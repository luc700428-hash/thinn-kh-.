package io.atmos.weather.next

import io.atmos.weather.domain.model.WeatherCondition

/** One shared source of truth for weather visuals, ambient sound and adaptive alarm music. */
enum class WeatherScene(val label: String) {
    SUNNY("晴天"), DRIZZLE("毛毛雨"), LIGHT_RAIN("小雨"), MODERATE_RAIN("中雨"),
    HEAVY_RAIN("大雨"), THUNDERSTORM("雷雨"), SEVERE_THUNDERSTORM("大雷雨"),
    HAZE("霾"), FOG("霧"), DENSE_FOG("大霧"), DUST_STORM("沙塵暴"),
    LIGHT_SNOW("小雪"), MODERATE_SNOW("中雪"), HEAVY_SNOW("大雪")
}

object WeatherScenes {
    private val drizzleCodes = setOf(51, 53, 55, 56, 57)
    private val lightRainCodes = setOf(61, 80)
    private val moderateRainCodes = setOf(63, 81)
    private val heavyRainCodes = setOf(65, 66, 67, 82)
    private val snowLightCodes = setOf(71, 77, 85)
    private val snowModerateCodes = setOf(73)
    private val snowHeavyCodes = setOf(75, 86)

    fun classify(condition: WeatherCondition?, code: Int? = null, visibilityM: Double? = null): WeatherScene? {
        // Safety: hazardous visibility/air-quality scenes outrank a possibly stale
        // forecast code of 0 (clear), so fog, haze and dust are never shown as sunny.
        when(condition) {
            WeatherCondition.HAZE -> return WeatherScene.HAZE
            WeatherCondition.DUST, WeatherCondition.DUST_STORM -> return WeatherScene.DUST_STORM
            WeatherCondition.FOG -> return if (visibilityM != null && visibilityM < 200.0)
                WeatherScene.DENSE_FOG else WeatherScene.FOG
            else -> Unit
        }
        // Specific weather codes refine the remaining coarse condition values.
        when (code) {
            0, 1, 2 -> {
                // A stale clear-sky code must not override an observed rain/snow/storm condition.
                val incompatible = condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM,
                    WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.HAIL,
                    WeatherCondition.FREEZING_RAIN)
                if (!incompatible) return WeatherScene.SUNNY
            }
            3 -> {
                val incompatible = condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM,
                    WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.HAIL,
                    WeatherCondition.FREEZING_RAIN)
                if (!incompatible) return null // genuine overcast preserves original visuals
            }
            95 -> return WeatherScene.THUNDERSTORM
            96, 99 -> return WeatherScene.SEVERE_THUNDERSTORM
            in drizzleCodes -> return WeatherScene.DRIZZLE
            in lightRainCodes -> return WeatherScene.LIGHT_RAIN
            in moderateRainCodes -> return WeatherScene.MODERATE_RAIN
            in heavyRainCodes -> return WeatherScene.HEAVY_RAIN
            in snowLightCodes -> return WeatherScene.LIGHT_SNOW
            in snowModerateCodes -> return WeatherScene.MODERATE_SNOW
            in snowHeavyCodes -> return WeatherScene.HEAVY_SNOW
            45, 48 -> return if (visibilityM != null && visibilityM < 200.0) WeatherScene.DENSE_FOG else WeatherScene.FOG
        }
        return when (condition) {
            WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY -> WeatherScene.SUNNY
            WeatherCondition.CLOUDY -> null // preserve existing cloudy scene instead of inventing sunlight
            WeatherCondition.RAIN -> WeatherScene.MODERATE_RAIN
            WeatherCondition.FREEZING_RAIN, WeatherCondition.SLEET -> WeatherScene.LIGHT_RAIN
            WeatherCondition.HAIL -> WeatherScene.HEAVY_RAIN
            WeatherCondition.THUNDERSTORM -> WeatherScene.THUNDERSTORM
            WeatherCondition.FOG -> if (visibilityM != null && visibilityM < 200.0) WeatherScene.DENSE_FOG else WeatherScene.FOG
            WeatherCondition.HAZE -> WeatherScene.HAZE
            WeatherCondition.DUST, WeatherCondition.DUST_STORM -> WeatherScene.DUST_STORM
            WeatherCondition.SNOW -> WeatherScene.MODERATE_SNOW
            else -> null // NEVER tell the user the weather is sunny when the input is unknown.
        }
    }

    fun fromCachedName(value: String?, code: Int? = null, visibilityM:Double?=null): WeatherScene? {
        val condition = WeatherCondition.entries.firstOrNull { it.name == value }
        return classify(condition, code, visibilityM)
    }

    fun ambienceAsset(scene: WeatherScene) = "next_weather/ambience/${scene.name.lowercase()}.wav"
    fun musicAsset(theme: String, scene: WeatherScene?): String? =
        when(theme) {
            in WeatherWakePolicy.paletteThemes -> "next_weather/music/$theme/${WeatherWakePolicy.paletteArrangement(scene)}.wav"
            in WeatherWakePolicy.originalThemes -> "next_weather/music/$theme/${scene?.name?.lowercase() ?: "neutral"}.wav"
            else -> null
        }
}
