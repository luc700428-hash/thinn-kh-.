package io.atmos.weather.support

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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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

/** Sponsor card and page in light and dark (build/sponsor/), checked for visible content. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SponsorRenderingTest {
    @Test fun cardAndPageRenderInLightAndDark() {
        val ctx = RuntimeEnvironment.getApplication()
        val w = 400; val h = 1500
        for (mode in listOf("light", "dark")) {
            IOS27AppearanceMode.update(ctx, mode)
            // Still frames: the breathing buddy, drifting hearts and button sweep are endless
            // animations, and each frame is a full native render (that made this test take 27 min).
            ctx.getSharedPreferences("thinn_settings", 0).edit().putBoolean("reduce_motion", true).commit()
            val c = Robolectric.buildActivity(ComponentActivity::class.java).setup(); val a = c.get(); val view = ComposeView(a)
            try {
                view.setContent {
                    ThinnAppTheme {
                        Column {
                            Box(Modifier.padding(16.dp)) { SponsorSettingsCard {} }
                            SponsorPage {}
                        }
                    }
                }
                val parent = FrameLayout(a).apply { addView(view, ViewGroup.LayoutParams(w, h)) }; a.setContentView(parent)
                repeat(3) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
                    parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); parent.layout(0, 0, w, h)
                }
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); parent.draw(Canvas(b))
                File("build/sponsor").apply { mkdirs() }.let { File(it, "$mode.png").outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 100, o) } }
                // The page must actually paint: many distinct colours, not a blank sheet.
                val colours = HashSet<Int>(); for (y in 0 until h step 7) for (x in 0 until w step 7) colours.add(b.getPixel(x, y))
                assertTrue("$mode sponsor page looks blank (${colours.size} colours)", colours.size > 60)
            } finally { view.disposeComposition(); c.pause().stop().destroy() }
        }
    }
}
