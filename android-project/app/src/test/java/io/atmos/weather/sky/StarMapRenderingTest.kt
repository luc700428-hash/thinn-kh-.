package io.atmos.weather.sky

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
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

/** The star map opens without a gyroscope (hand mode), loads the catalogue and draws a sky. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rTW-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class StarMapRenderingTest {
    private fun nodes(view: View): List<SemanticsNode> {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { walk(it) }
        if (view is RootForTest) return walk(view.semanticsOwner.rootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    }

    @Test fun drawsTheSkyWithControls() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("thinn_settings", 0).edit().putBoolean("reduce_motion", true).commit()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity)
        view.setContent { ThinnAppTheme { StarMapPage(WeatherCity("tp", "臺北市", GeoPoint(25.03, 121.56)),
            fixedTime = java.time.Instant.parse("2026-11-27T13:30:00Z"), startView = 285f to 30f) {} } }
        val w = 720; val h = 1520
        val parent = FrameLayout(activity).apply { addView(view, ViewGroup.LayoutParams(w, h)) }
        activity.setContentView(parent)
        repeat(6) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120)); Thread.sleep(60) }
        parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, w, h)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        parent.draw(Canvas(bitmap))
        System.getenv("THINN_SHOT_DIR")?.let { d -> File(d, "stars.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val texts = nodes(parent).filter { it.config.contains(SemanticsProperties.Text) }.flatMap { it.config[SemanticsProperties.Text].map { t -> t.text } }
        assertTrue("title", "星空與銀河" in texts)
        assertTrue("time bar", texts.any { it == "現在" })
        assertTrue("tonight", "今晚看點" in texts)
        assertTrue("catalogue loaded", texts.none { it.contains("正在載入") })
        // Some bright pixels (stars, planets or the Sun) are drawn in the sky area.
        var bright = 0
        for (y in 300 until 900 step 2) for (x in 0 until w step 2) { val c = bitmap.getPixel(x, y)
            if (((c shr 16) and 255) > 200 && ((c shr 8) and 255) > 200 && (c and 255) > 200) bright++ }
        assertTrue("sky drawn ($bright)", bright > 5)
    }
}
