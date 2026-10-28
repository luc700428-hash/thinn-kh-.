package io.atmos.weather.typhoon

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.atmos.weather.maps.*
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
class RegionalVectorRenderingTest {
    @Test fun actualRegionalCoastsContainCountriesAndMinorIslandsAtOverviewAndDeepZoom() {
        val atlas=runBlocking{RegionalVectorAtlas.load(RuntimeEnvironment.getApplication())}
        assertEquals(3178,atlas.polygonCount);assertEquals(1062,atlas.minorIslandCount)
        assertEquals(104907,atlas.vertexCount)
        for((lon,lat) in listOf(121.0 to 23.7,139.7 to 35.7,121.0 to 14.5,144.79 to 13.45,134.55 to 7.5,123.53 to 25.75))
            assertTrue("Missing land near $lon,$lat",atlas.hasLandNear(lon,lat))
        val width=1080f;val height=1260f;val cosine=cos(Math.toRadians(25.0))
        val overview=TyphoonViewport(133.0*111.2*cosine,25.0*111.2,.22,width.toDouble(),height.toDouble(),cosine)
        val output=File("build/regional-renders").apply{mkdirs()}
        fun render(view:TyphoonViewport,dark:Boolean,name:String):Bitmap {
            val image=ImageBitmap(width.toInt(),height.toInt())
            CanvasDrawScope().draw(Density(2.5f),LayoutDirection.Ltr,Canvas(image),Size(width,height)) {
                drawRect(if(dark)Color(0xFF081E31) else Color(0xFFE4F0F7))
                atlas.draw(this,view,dark);drawRegionalPlaceLabels(view,dark)
            }
            val bitmap=image.asAndroidBitmap()
            File(output,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            return bitmap
        }
        for(dark in listOf(false,true)) {
            val b=render(overview,dark,"overview_${if(dark)"dark" else "light"}")
            val sea=if(dark)0xff081e31.toInt() else 0xffe4f0f7.toInt()
            var different=0
            for(y in 0 until b.height step 4)for(x in 0 until b.width step 4)if(b.getPixel(x,y)!=sea)different++
            assertTrue("Regional geography did not draw",different>2000)
            val zoom=TyphoonViewport.forTaiwan(width,height,40f).let{it.copy(pxPerKm=it.pxPerKm*32,
                originXKm=123.53*111.2*it.cosCenter,originYKm=25.75*111.2)}
            val detail=render(zoom,dark,"tiny_islands_32x_${if(dark)"dark" else "light"}")
            assertFalse("Deep zoom lost the source island",detail.sameAs(b))
        }
    }
}
