package io.atmos.weather.presentation.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import io.atmos.weather.domain.model.*
import io.atmos.weather.ui.IOS27AppearanceMode
import io.atmos.weather.ui.ThinnAppTheme
import io.atmos.weather.ui.WeatherCardPalettes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * r22: the home cards take their ink from the card palette, so text stays readable on light and
 * dark cards alike. Renders both appearances (build/home-cards/) and checks the ink contrast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HomeCardsThemeRenderingTest {
    private val now = Instant.now()
    private fun weather() = WeatherSnapshot(WeatherCity("t", "嘉義", GeoPoint(23.48, 120.45)), now, now, "test", 24.0, 25.0, 86, 8.0, 1012.0,
        WeatherCondition.CLOUDY, true, now.minusSeconds(9 * 3600), now.plusSeconds(2 * 3600),
        (0 until 72).map { HourlyWeather(now.plusSeconds(it * 3600L), 24.0 - (it % 24) * .2, if (it in 30..40) 60 else 10, 3.0, humidityPct = 80, windKmh = 8.0, weatherCode = 3, feelsLikeC = 25.0) },
        maxTempC = 25.0, minTempC = 19.0, currentUv = 5.0, windDirectionDeg = 0, visibilityM = 12_000.0, usAqi = 77, pm25 = 18.0, timeZoneId = "Asia/Taipei")

    @Test fun cardsRenderReadablyInLightAndDark() {
        val ctx = RuntimeEnvironment.getApplication()
        for (mode in listOf("light", "dark")) {
            IOS27AppearanceMode.update(ctx, mode)
            val c = Robolectric.buildActivity(ComponentActivity::class.java).setup(); val a = c.get(); val view = ComposeView(a)
            try {
                var contrast = 0f
                view.setContent {
                    ThinnAppTheme {
                        val p = WeatherCardPalettes.current
                        contrast = (maxOf(p.ink.luminance(), p.fill.copy(alpha = 1f).luminance()) + .05f) /
                            (minOf(p.ink.luminance(), p.fill.copy(alpha = 1f).luminance()) + .05f)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeOutlookCard(weather(), "嘉義") {}
                            DailyGuidanceCard(weather(), false) {}
                            WeatherIndicatorGrid(weather())
                            DailyForecastCard(weather())
                        }
                    }
                }
                val parent = FrameLayout(a).apply { addView(view, ViewGroup.LayoutParams(400, 2300)) }; a.setContentView(parent)
                repeat(5) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
                    parent.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2300, View.MeasureSpec.EXACTLY)); parent.layout(0, 0, 400, 2300)
                }
                val b = Bitmap.createBitmap(400, 2300, Bitmap.Config.ARGB_8888); parent.draw(Canvas(b))
                File("build/home-cards").apply { mkdirs() }.let { File(it, "$mode.png").outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 100, o) } }
                assertTrue("$mode card ink contrast $contrast", contrast >= 4.5f)
            } finally { view.disposeComposition(); c.pause().stop().destroy() }
        }
    }
}
