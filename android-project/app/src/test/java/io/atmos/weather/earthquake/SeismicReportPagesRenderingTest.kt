package io.atmos.weather.earthquake

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import io.atmos.weather.ui.ThinnAppTheme
import io.atmos.weather.ui.IOS27AppearanceMode
import org.json.JSONObject
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

/** CWA reports parse with every station and render as the DPIP-style list and detail page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicReportPagesRenderingTest {
    private fun reports()=CwaEarthquakeParser.parse(JSONObject(javaClass.getResourceAsStream("/cwa/eq_reports.json")!!.bufferedReader().readText()),"顯著有感")
    @Test fun cwaReportCarriesCountiesStationsAndImages() {
        val r=reports().first()
        assertEquals("雲林縣古坑鄉",r.shortLocation)
        assertEquals("115079",r.number)
        assertEquals("4",r.countyIntensity["嘉義縣"])
        assertTrue(r.stations.size>=20)
        assertEquals("4",r.stations.first().intensity)
        assertNotNull(r.imageUrl);assertNotNull(r.shakemapUrl)
        assertEquals("5弱",SeismicLiveIntensity.grade(4.7))
    }
    @Test fun listAndDetailRender() {
        val ctx=RuntimeEnvironment.getApplication()
        IOS27AppearanceMode.update(ctx,"light")
        for((name,content) in listOf<Pair<String,@androidx.compose.runtime.Composable ()->Unit>>(
            "report-list" to {Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)){SeismicReportsPanel(reports(),false,null,"中央氣象署官方震後報告",{},{})}},
            "section-picker" to {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                SeismicSectionPicker("監測",{});SeismicSectionPicker("報告",{})}},
            "report-detail" to {SeismicReportDetailPage(reports().first(),SeismicPlace("嘉義市",23.48,120.45,"test"),{},{})})) {
            val c=Robolectric.buildActivity(ComponentActivity::class.java).setup();val a=c.get();val view=ComposeView(a)
            try {
                view.setContent{ThinnAppTheme{content()}}
                val parent=FrameLayout(a).apply{addView(view,ViewGroup.LayoutParams(400,1400))};a.setContentView(parent)
                repeat(4){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                    parent.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.EXACTLY));parent.layout(0,0,400,1400)}
                val b=Bitmap.createBitmap(400,1400,Bitmap.Config.ARGB_8888);parent.draw(Canvas(b))
                File("build/seismic-renders").apply{mkdirs()}.let{File(it,"$name.png").outputStream().use{o->b.compress(Bitmap.CompressFormat.PNG,100,o)}}
            } finally {view.disposeComposition();c.pause().stop().destroy()}
        }
    }
}
