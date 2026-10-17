package io.atmos.weather.domain.model

import java.time.Instant

fun main() {
    val weather = WeatherSnapshot(
        city=WeatherCity("chiayi", "嘉義縣民雄", GeoPoint(23.555,120.431)),
        observedAt=Instant.parse("2026-10-17T04:00:00Z"),
        fetchedAt=Instant.parse("2026-10-17T04:00:00Z"),
        sourceId="open-meteo-model", temperatureC=30.0,feelsLikeC=31.0,
        humidityPct=80,windKmh=2.0,pressureHpa=1012.0,
        condition=WeatherCondition.HAZE,isDay=true,sunrise=null,sunset=null,
        hourly=emptyList(),usAqi=92,pm25=26.0,aerosolOpticalDepth=.6,
        visibilityM=8000.0
    )
    val advice = WeatherAdviceEngine.suggest(weather)
    check(advice.none { it.heading.contains("沙塵") || it.heading.contains("結冰") || it.heading.contains("降雪") })
    check(advice.any { it.heading.contains("懸浮微粒") && it.detail.contains("不等於") })
    check(advice.none { it.detail.contains("沙塵暴通知") && !it.detail.contains("非官方") })
    println("Kotlin weather aerosol and warm-city guard: PASS")
}
