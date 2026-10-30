package io.atmos.weather.ui

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import io.atmos.weather.alerts.CwaAlertsPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CwaEarthquakeReportRoutingRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun anEarthquakeReportOpensItsOfficialInAppReportAndReturnsWithoutAWeatherMap() {
        val url="https://www.cwa.gov.tw/V8/C/E/EQ/EQ115074-1030-043743.html?T=202610300440"
        fun browsers(v:View):List<WebView> = if(v is WebView)listOf(v) else if(v is ViewGroup)
            (0 until v.childCount).flatMap{browsers(v.getChildAt(it))} else emptyList()
        for(dark in listOf(false,true)) {
            var closed=false
            render(dark,1.5f,"official-earthquake-report",{
                CwaAlertsPage(initialTitle="中央氣象署地震報告",initialUrl=url){closed=true}
            }){view,_->
                assertTrue(nodes(view).any{text(it,"中央氣象署官方資訊")})
                assertFalse(nodes(view).any{text(it,"臺灣警戒範圍")})
                assertEquals(url,browsers(view).single().url)
                val back=nodes(view).single{n->n.config.contains(SemanticsProperties.ContentDescription)&&
                    "返回" in n.config[SemanticsProperties.ContentDescription]&&n.config.contains(SemanticsActions.OnClick)}
                assertTrue(back.config[SemanticsActions.OnClick].action!!.invoke());assertTrue(closed)
            }
        }
    }
}
