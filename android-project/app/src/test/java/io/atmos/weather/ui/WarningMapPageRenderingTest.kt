package io.atmos.weather.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import io.atmos.weather.alerts.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WarningMapPageRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun nationalMapCountyChoicesRemainReadableAndUnambiguousAtBothReadingSizesAndThemes() {
        val instant=Instant.parse("2026-10-28T18:30:00Z")
        fun source(name:String)=javaClass.getResourceAsStream("/continuous-radar/$name")!!.bufferedReader().use{it.readText()}
        val bulletins=CwaBulletins.parse(source("cwa-bulletins.json"),instant)
        val sourceAt=CwaBulletins.time(org.json.JSONObject(source("cwa-counties.json")).getJSONObject("cwaopendata").getString("sent"))!!
        val national=CwaNationalWarnings(CwaNationalWarningFeed.parse(source("cwa-counties.json"),instant),instant,sourceAt=sourceAt)
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            render(dark,scale,"national-warning-map",{
                CwaAlertsContent(CwaBulletinsResult(bulletins,instant),emptyList(),emptyList(),false,null,{}, {}, {},{},instant,national)
            }){view,palette->
                val title=nodes(view).single{text(it,"臺灣警戒範圍")};contrast(title,palette.surface)
                val titleLayout=layout(title)
                assertFalse("Warning title clipped at $dark/$scale: ${titleLayout.size}, ${titleLayout.layoutInput.style}",titleLayout.hasVisualOverflow)
                click(view,"屏東縣")
                assertEquals(1,nodes(view).count{it.config.contains(SemanticsProperties.Selected)&&it.config[SemanticsProperties.Selected]})
                click(view,"臺東縣")
                assertEquals(1,nodes(view).count{it.config.contains(SemanticsProperties.Selected)&&it.config[SemanticsProperties.Selected]})
                assertTrue(nodes(view).any{text(it,"陸上強風特報")})
            }
        }
    }
}
