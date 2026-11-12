package io.atmos.weather.ui
import android.app.Application
import androidx.compose.ui.semantics.*
import io.atmos.weather.typhoon.*
import io.atmos.weather.maps.RegionalVectorAtlas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.Instant
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rTW-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TyphoonTimelineRenderingTest:WeatherInformationRenderingFixture(){
    @Test fun actualTimelineKeepsItsSourceMapAndNeverBuildsPathsOnTheUiThreadWhileScrubbing(){
        val ctx=RuntimeEnvironment.getApplication();val atlas=runBlocking{RegionalVectorAtlas.load(ctx)}
        val uiBuilds=atlas.mainThreadBatchBuildCount
        val storm=StormTimeline("render-only","路徑測試","TEST",(0..5).map{
            StormForecastFix(19.0+it*.8,129.0-it*1.1,Instant.parse("2026-11-12T00:00:00Z").plusSeconds(it*6*3600L),windMs=35.0,radius7Km=180.0)
        })
        for(dark in listOf(false,true))render(dark,1f,"typhoon-timeline",{StormTimelineOverlay(storm,{})}){view,_->
            settle(view){nodes(view).any{text(it,"Natural Earth · 國土測繪中心")}}
            val map=nodes(view).single{it.config.contains(SemanticsProperties.TestTag)&&it.config[SemanticsProperties.TestTag]=="storm-timeline-map"}
            assertTrue(map.boundsInRoot.width>=300&&map.boundsInRoot.height>=300)
            val slider=nodes(view).single{it.config.contains(SemanticsActions.SetProgress)}
            val initial=pump(view)
            for(step in (0..15).map{it/3f})assertTrue(slider.config[SemanticsActions.SetProgress].action!!.invoke(step))
            pump(view).recycle()
            assertTrue(nodes(view).any{text(it,"6 / 6")})
            val after=pump(view);assertFalse("Cursor did not move",initial.sameAs(after));initial.recycle();after.recycle()
            assertEquals("Map path preparation blocked the UI",uiBuilds,atlas.mainThreadBatchBuildCount)
            save(view,"typhoon-timeline-final-$dark")
        }
    }
}
