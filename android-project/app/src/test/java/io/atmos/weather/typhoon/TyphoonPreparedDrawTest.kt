package io.atmos.weather.typhoon
import android.app.Application
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.atmos.weather.maps.RegionalVectorAtlas
import io.atmos.weather.maps.TaiwanVectorAtlas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TyphoonPreparedDrawTest {
    @Test fun gestureDrawNeverBuildsBatchesAndTaiwanUsesTwoContourDraws()=runBlocking {
        val ctx=RuntimeEnvironment.getApplication();val atlas=RegionalVectorAtlas.load(ctx);val taiwan=TaiwanVectorAtlas.load(ctx)
        val camera=TyphoonViewport.forTaiwan(600f,600f,24f)
        val bitmap=ImageBitmap(600,600);val canvas=Canvas(bitmap)
        val count=atlas.batchBuildCount
        repeat(144){CanvasDrawScope().draw(Density(1.5f),LayoutDirection.Ltr,canvas,Size(600f,600f)){
            atlas.draw(this,camera.copy(originXKm=camera.originXKm+it*.01),true,preparedOnly=true)
        }}
        assertEquals("Gesture fallback must not build source paths on the UI thread",count,atlas.batchBuildCount)
        atlas.prepare(camera);assertTrue(atlas.prepared(camera))
        val prepared=atlas.batchBuildCount
        repeat(144){CanvasDrawScope().draw(Density(1.5f),LayoutDirection.Ltr,canvas,Size(600f,600f)){
            atlas.draw(this,camera,true,preparedOnly=true)
        }}
        assertEquals(prepared,atlas.batchBuildCount)
        assertEquals(2,taiwan.contourDrawCalls);assertTrue(taiwan.sourceDrawCalls>100)
        File("build/performance-proof").apply{mkdirs()}.resolve("typhoon-prepared-draw.txt").writeText(
            "Native Android graphics / Robolectric; not a device frame-rate benchmark\nFallback gesture draws=144\nUI path builds=${atlas.batchBuildCount-prepared}\nPrepared draws=144\nTaiwan previous contour calls=${taiwan.sourceDrawCalls}\nTaiwan current contour calls=${taiwan.contourDrawCalls}\n")
    }
}
