package io.atmos.weather.presentation.home

import android.app.Application
import android.graphics.Bitmap
import io.atmos.weather.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*

/** The share card renders at 1080×1350 with real content in day and night (build/share-card/). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW-xxhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherShareCardTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = ZonedDateTime.of(2026, 11, 18, 9, 0, 0, 0, zone).toInstant()
    private fun weather(day: Boolean, condition: WeatherCondition) =
        WeatherSnapshot(WeatherCity("t", "嘉義市 · 東區", GeoPoint(23.48, 120.45)), now, now, "test", 24.0, 25.0, 70, 10.0, 1012.0,
            condition, day, now.minusSeconds(3 * 3600), now.plusSeconds(8 * 3600),
            (0 until 24).map { HourlyWeather(now.plusSeconds(it * 3600L), 24.0 - it % 5, if (it == 5) 70 else 10, 4.0, humidityPct = 70, windKmh = 10.0, weatherCode = 2, feelsLikeC = 24.0) },
            maxTempC = 28.0, minTempC = 19.0, currentUv = 4.0, usAqi = 62, timeZoneId = "Asia/Taipei")

    @Test fun rendersDayAndNightCards() {
        val ctx = RuntimeEnvironment.getApplication()
        val out = File("build/share-card").apply { mkdirs() }
        for ((name, w) in listOf("day" to weather(true, WeatherCondition.PARTLY_CLOUDY), "night-rain" to weather(false, WeatherCondition.RAIN))) {
            val b = WeatherShareCard.render(ctx, w, "嘉義市 · 東區", now)
            assertEquals(1080, b.width); assertEquals(1350, b.height)
            File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val colours = HashSet<Int>(); for (y in 0 until b.height step 9) for (x in 0 until b.width step 9) colours.add(b.getPixel(x, y))
            assertTrue("$name card looks empty (${colours.size} colours)", colours.size > 200)
        }
        val tiles = WeatherShareCard.tiles(weather(true, WeatherCondition.CLEAR), now, zone)
        assertEquals(listOf("降雨機率", "空氣品質", "日落"), tiles.map { it.label })
        assertEquals("70%", tiles[0].value)
    }
}
