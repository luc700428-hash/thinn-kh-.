package io.atmos.weather.visual

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandmarkArtRenderingTest {
    @Test fun everyLandmarkDrawsInsideItsFrameDayAndNight() {
        val cols=6;val cell=260;val all=LandmarkArt.all
        for(night in listOf(false,true)) {
            val rows=(all.size+cols-1)/cols
            val out=ImageBitmap(cols*cell,rows*cell)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(out),Size((cols*cell).toFloat(),(rows*cell).toFloat())) {
                drawRect(if(night)Color(0xFF16213A) else Color(0xFF8DB8E8))
                val ink=LandmarkInk(if(night)Color(0xFF0B1222).copy(alpha=.9f) else Color(0xFF2E4258).copy(alpha=.75f),
                    Color.White.copy(alpha=.35f),Color(0xFFFFD58A),night)
                all.forEachIndexed{i,spec->
                    val cx=(i%cols)*cell+cell/2f;val ground=(i/cols)*cell+cell*.86f
                    val unit=minOf(cell*.78f/100f,cell*.9f/spec.width)
                    LandmarkPen(this,cx,ground,unit,ink).apply(spec.draw)
                }
            }
            val bmp=out.asAndroidBitmap()
            File("build/landmarks").apply{mkdirs()}.let{File(it,"sheet-$night.png").outputStream().use{s->bmp.compress(Bitmap.CompressFormat.PNG,100,s)}}
            // Every cell has ink in it.
            all.indices.forEach{i->
                var painted=0;val x0=(i%cols)*cell;val y0=(i/cols)*cell
                val bg=bmp.getPixel(x0+2,y0+2)
                for(y in y0 until y0+cell step 3)for(x in x0 until x0+cell step 3)if(bmp.getPixel(x,y)!=bg)painted++
                assertTrue("${all[i].name} drew nothing",painted>60)
            }
        }
        assertEquals(22,LandmarkPlaces.places.count{it.taiwan})
        assertEquals("嘉義市",LandmarkPlaces.find("嘉義市 東區",0.0,0.0)?.name)
        assertEquals("新竹縣",LandmarkPlaces.find("新竹縣竹北市",0.0,0.0)?.name)
        assertEquals("臺北市",LandmarkPlaces.find("台北市信義區",0.0,0.0)?.name)
        assertEquals("澎湖縣",LandmarkPlaces.find("馬公",23.57,119.58)?.name)
        assertEquals("巴黎",LandmarkPlaces.find("Paris",48.86,2.35)?.name)
        assertNull(LandmarkPlaces.find("Nowhere",-60.0,-120.0))
    }
}
