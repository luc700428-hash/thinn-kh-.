package io.atmos.weather.typhoon

import android.app.Application
import io.atmos.weather.maps.RegionalVectorAtlas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TyphoonMapPerformanceTest {
    @Test fun overviewUsesBoundedVectorDetailAndPanningReusesBatches()=runBlocking {
        val atlas=RegionalVectorAtlas.load(RuntimeEnvironment.getApplication())
        val cosine=cos(Math.toRadians(25.0))
        val view=TyphoonViewport(133*111.2*cosine,25*111.2,.22,1080.0,1260.0,cosine)
        val stats=atlas.renderStats(view)
        assertTrue(stats.vertices<atlas.vertexCount)
        assertEquals(2,stats.contourDrawCalls);assertTrue(stats.errorPixels<=.40001f)
        val builds=atlas.batchBuildCount
        repeat(120){atlas.renderStats(view.copy(originXKm=view.originXKm+it*.05))}
        val panBuilds=atlas.batchBuildCount-builds
        assertTrue("Small pan rebuilt the entire regional map",atlas.batchBuildCount-builds<=2)
        val deep=TyphoonViewport.forTaiwan(1080f,1260f,40f).let{it.copy(pxPerKm=it.pxPerKm*32,
            originXKm=123.53*111.2*it.cosCenter,originYKm=25.75*111.2)}
        assertEquals(0f,atlas.renderStats(deep).errorPixels,0f)
        File("build/performance-proof").apply{mkdirs()}.resolve("typhoon-map.txt").writeText(
            "Source vertices=${atlas.vertexCount}\nOverview vertices=${stats.vertices}\nContour draw calls=${stats.contourDrawCalls}\nMax overview error pixels=${stats.errorPixels}\nPan frames=120\nNew pan batches=${panBuilds}\n32x source-detail error=0\n")
    }
}
