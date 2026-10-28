package io.atmos.weather.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.ComposeView
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

/** Compose the real catalogue, including offscreen groups visited by pager precomposition. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ToolsPageRenderingTest {
    @Test fun completeCatalogueRendersEveryGroupInLightAndDark() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("thinn_settings",0).edit().clear()
            .putBoolean("reduce_motion",true).putBoolean("reduce_transparency",true).commit()
        for(dark in listOf(false,true)) {
            val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity=controller.get()
            val view=ComposeView(activity)
            try {
                view.setContent {
                    ThinnAppTheme {
                        MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()) {
                            AppGlassToolsPage(onBriefings={},onUpdates={},onDiagnostics={},onSeismic={})
                        }
                    }
                }
                val parent=FrameLayout(activity).apply{addView(view,ViewGroup.LayoutParams(400,1600))}
                activity.setContentView(parent)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
                parent.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY))
                parent.layout(0,0,400,1600)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
                val bitmap=Bitmap.createBitmap(400,1600,Bitmap.Config.ARGB_8888)
                parent.draw(Canvas(bitmap))
                // Distinct category icons prove that the entire catalogue reached the real draw path.
                for(tint in listOf(0xffff9500.toInt(),0xff007aff.toInt(),0xff007a78.toInt(),0xff5856d6.toInt())) {
                    var pixels=0
                    for(y in 0 until bitmap.height)for(x in 0 until bitmap.width)if(bitmap.getPixel(x,y)==tint)pixels++
                    assertTrue("Missing category icon in ${if(dark)"dark" else "light"} catalogue: ${Integer.toHexString(tint)}",pixels>100)
                }
                val output=File("build/tools-renders").apply{mkdirs()}
                File(output,"tools_${if(dark)"dark" else "light"}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            } finally {
                view.disposeComposition()
                controller.pause().stop().destroy()
            }
        }
    }
}
