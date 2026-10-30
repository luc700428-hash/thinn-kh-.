package io.atmos.weather.ui

import android.app.Application
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
class CwaWarningFallbackRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun failedFullTextLoadingKeepsTheActualRssSummaryAndWorkingRetryAndOfficialActions() {
        val at=Instant.parse("2026-10-29T21:01:00Z")
        val rss=javaClass.getResourceAsStream("/cwa-warning-20261030/warning-rss.xml")!!.use{CwaWarningFeed.parse(it)}
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            var retries=0;var opened=false
            render(dark,scale,"warning-rss-fallback",{
                CwaAlertsContent(CwaBulletinsResult(emptyList(),at,failed=true),rss,emptyList(),false,rss.single().title,
                    {},{retries++},{},{opened=true},at,CwaNationalWarnings(emptyList(),at,failed=true))
            }){view,palette->
                val summary=nodes(view).single{text(it,rss.single().summary)}
                contrast(summary,palette.surface);assertFalse(layout(summary).hasVisualOverflow)
                assertTrue(nodes(view).any{text(it,"官方公告摘要")})
                assertTrue(nodes(view).any{text(it,"官方近期公告 · 有效狀態待確認")})
                assertFalse(nodes(view).any{text(it,"臺灣警戒範圍")})
                assertTrue("The available summary must precede an unavailable map",summary.boundsInRoot.top<900)
                click(view,"重新取得公告");assertEquals(1,retries)
                click(view,"在 App 內查看官方圖表與完整頁面");assertTrue(opened)
            }
        }
    }
}
