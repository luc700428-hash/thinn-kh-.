package io.atmos.weather.life

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.IOS27AppearanceMode
import io.atmos.weather.ui.ThinnAppTheme
import org.junit.Assert.assertTrue
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LifePagesRenderingTest {
    private val w = 412 * 2; private val h = 1500 * 2
    private fun nodes(view: View): List<SemanticsNode> {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { walk(it) }
        if (view is RootForTest) return walk(view.semanticsOwner.rootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    }
    private fun snapshot(): WeatherSnapshot {
        val now = Instant.now()
        return WeatherSnapshot(WeatherCity("t", "臺北市大安區", GeoPoint(25.03, 121.54)), now, now, "test", 21.0, 20.0, 70, 9.0, 1011.0,
            WeatherCondition.PARTLY_CLOUDY, true, null, null,
            (0 until 48).map { HourlyWeather(now.plusSeconds(it * 3600L), 20.0 + it % 6, if (it == 5) 70 else 10, 3.0, windKmh = 9.0) },
            maxTempC = 25.0, minTempC = 18.0, usAqi = 42)
    }

    private fun render(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit): String {
        val ctx = RuntimeEnvironment.getApplication()
        IOS27AppearanceMode.update(ctx, if (dark) "dark" else "light")
        val c = Robolectric.buildActivity(ComponentActivity::class.java).setup(); val a = c.get(); val view = ComposeView(a)
        try {
            view.setContent { ThinnAppTheme { if (name == "disaster") content() else Column(Modifier.fillMaxSize().background(if (name == "home") Color(0xFF2E5C8A) else MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } } }
            val parent = FrameLayout(a).apply { addView(view, ViewGroup.LayoutParams(w, h)) }; a.setContentView(parent)
            repeat(4) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120))
                parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); parent.layout(0, 0, w, h)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { parent.draw(Canvas(it)) }
            File("build/life-pages").apply { mkdirs() }.let { File(it, "$name-$dark.png").outputStream().use { s -> bmp.compress(Bitmap.CompressFormat.PNG, 100, s) } }
            val texts = nodes(parent).filter { it.config.contains(SemanticsProperties.Text) }
            texts.forEach { n -> assertTrue("${n.config[SemanticsProperties.Text]} ${n.boundsInRoot}", n.boundsInRoot.right <= w + 1f && n.boundsInRoot.left >= -1f) }
            return texts.joinToString("|") { it.config[SemanticsProperties.Text].joinToString("") } + "|" +
                nodes(parent).filter { it.config.contains(SemanticsProperties.ContentDescription) }.joinToString("|") { it.config[SemanticsProperties.ContentDescription].joinToString("") }
        } finally { view.disposeComposition(); c.pause().stop().destroy() }
    }

    @Test fun pagesRenderTheirContentInBothAppearances() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("atmos_settings", Context.MODE_PRIVATE).edit().putLong("lat", 25.0330.toRawBits()).putLong("lon", 121.5654.toRawBits())
            .putString("city_name", "臺北市大安區").commit()
        assertTrue(LifeSources.shelters(ctx).size > 5000)
        for (dark in listOf(true, false)) {
            val home = render("home", dark) { HomeLifeStrip(snapshot(), listOf("大雨特報"), Color.White) {} }
            assertTrue(home, home.contains("今日重點") && home.contains("大雨特報") && home.contains("偏冷"))
            val kit = render("kit", dark) { Column { LifeFeatureContent("kit", null) {} } }
            assertTrue(kit, kit.contains("已準備 0 / 16") && kit.contains("飲用水"))
            val rules = render("rules", dark) { Column { LifeFeatureContent("rules", null) {} } }
            assertTrue(rules, rules.contains("快速範本") && rules.contains("適合洗車／露營"))
            val shelter = render("shelter", dark) { Column { LifeFeatureContent("shelter", null) {} } }
            assertTrue(shelter, shelter.contains("避難收容處所") && shelter.contains("公尺"))
            val snap = render("snap", dark) { Column { LifeFeatureContent("snap", snapshot()) {} } }
            assertTrue(snap, snap.contains("拍照") && snap.contains("雜誌風"))
            val family = render("family", dark) { Column { LifeFeatureContent("family", null) {} } }
            assertTrue(family, family.contains("新增家人"))
            val disaster = render("disaster", dark) { DisasterModeScreen(snapshot(), listOf("大雨特報"), {}, {}) }
            assertTrue(disaster, disaster.contains("災難省電模式") && disaster.contains("最近的避難處所") && disaster.contains("離開災難省電模式"))
        }
    }

    @Test fun watermarkStylesKeepThePhotoSizeAndDrawTheCaption() {
        val photo = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(250, 140, 60)) }
        val facts = LifeCards.SnapFacts("臺北市大安區", Instant.parse("2026-11-26T09:30:00Z"), 21, "多雲", 1011, 12, 70)
        LifeCards.SnapStyle.entries.forEach { style ->
            val out = LifeCards.watermark(photo, facts, style)
            assertTrue(out.width == 1200 && out.height == 900)
            File("build/life-pages").apply { mkdirs() }.let { File(it, "snap-${style.name}.png").outputStream().use { s -> out.compress(Bitmap.CompressFormat.PNG, 90, s) } }
            // Something was drawn over the plain orange photo near the bottom.
            var changed = 0
            for (x in 0 until 1200 step 7) for (y in 600 until 900 step 7) if (out.getPixel(x, y) != photo.getPixel(x, y)) changed++
            assertTrue("$style drew nothing", changed > 50)
        }
        val card = LifeCards.careCard(RuntimeEnvironment.getApplication(), FamilyCare.Person("1", "爸媽", "媽媽", "嘉義市", 23.48, 120.45), snapshot(), "媽媽，嘉義市明天驟降 8°，多穿一點。")
        File("build/life-pages/care-card.png").outputStream().use { s -> card.compress(Bitmap.CompressFormat.PNG, 90, s) }
        assertTrue(card.width == 1080)
    }
}
