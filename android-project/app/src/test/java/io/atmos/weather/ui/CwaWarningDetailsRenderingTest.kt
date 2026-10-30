package io.atmos.weather.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
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
class CwaWarningDetailsRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun theRealDawnNoticeOpensReadableDetailsAndIncludesItsFutureScopeInAllFourAppearances() {
        val at=Instant.parse("2026-10-29T21:01:00Z")
        fun source(name:String)=javaClass.getResourceAsStream("/cwa-warning-20261030/$name")!!.bufferedReader().use{it.readText()}
        val b=CwaBulletins.parse(source("bulletins.json"),at).single()
        val rss=CwaWarningFeed.parse(source("warning-rss.xml").byteInputStream())
        val national=CwaNationalWarnings(CwaNationalWarningFeed.parse(source("counties.json"),at),at,sourceAt=b.updatedAt)
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            val selected=mutableStateOf<String?>(null);var official=false;var retries=0
            render(dark,scale,"warning-upcoming",{
                CwaAlertsContent(CwaBulletinsResult(listOf(b),at),rss,emptyList(),false,selected.value,
                    {selected.value=it},{retries++},{selected.value=null},{official=true},at,national)
            }){view,palette->
                assertFalse(nodes(view).any{n->n.config.contains(SemanticsProperties.Text)&&n.config[SemanticsProperties.Text].any{it.text.contains("074 號")}})
                click(view,rss.single().title)
                settle(view){nodes(view).any{text(it,"已發布 · 預告時段，尚未生效")}}
                val title=nodes(view).single{text(it,b.title)}
                assertTrue("Detail heading must precede the map",title.boundsInRoot.top<240)
                contrast(title,palette.surface);assertFalse(layout(title).hasVisualOverflow)
                for(label in listOf("已發布 · 預告時段，尚未生效","開始時間 10/30 17:00","有效至 10/30 23:00",b.areas.joinToString("、"),b.text)) {
                    val n=nodes(view).single{text(it,label)}
                    contrast(n,palette.surface);assertFalse("Clipped: $label at $scale",layout(n).hasVisualOverflow)
                }
                settle(view){nodes(view).any{n->n.config.contains(SemanticsProperties.ContentDescription)&&n.config[SemanticsProperties.ContentDescription].any{it.contains("涉及")&&it.contains("苗栗縣")&&it.contains("臺東縣")}}}
                click(view,"在 App 內查看官方圖表與完整頁面");assertTrue(official)
                click(view,"更新警特報");assertEquals(1,retries)
            }
        }
    }
}
