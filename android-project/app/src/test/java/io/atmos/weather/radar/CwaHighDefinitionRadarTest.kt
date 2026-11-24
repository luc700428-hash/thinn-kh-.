package io.atmos.weather.radar

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CwaHighDefinitionRadarTest {
    private fun resource(name:String):ByteArray {
        val input=javaClass.getResourceAsStream("/cwa-radar-hd/"+name+if(name.endsWith(".js"))".gz" else "")!!
        return (if(name.endsWith(".js"))java.util.zip.GZIPInputStream(input) else input).use{it.readBytes()}
    }
    // Robolectric 4.16's region decoder returns blank regions, even in native graphics.
    // Decode real PNGs with its native BitmapFactory; only the OS region-reading adapter
    // is substituted. Production projection, colour classification, motion and painting run unchanged.
    private fun decode(name:String,tiles:List<RadarTile>):Map<RadarTile,Bitmap> {
        val bytes=resource(name)
        val original=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)!!
        val source=object:CwaRadarRegions {
            override val width=original.width
            override val height=original.height
            override fun read(rect:android.graphics.Rect,sampleSize:Int):Bitmap {
                if(rect.width()!=1)assertTrue("Unbounded source region",maxOf(rect.width(),rect.height())/sampleSize<=1024)
                val region=Bitmap.createBitmap(original,rect.left,rect.top,rect.width(),rect.height())
                if(sampleSize==1)return region
                return Bitmap.createScaledBitmap(region,(region.width/sampleSize).coerceAtLeast(1),
                    (region.height/sampleSize).coerceAtLeast(1),false).also{if(region!==original)region.recycle()}
            }
            override fun close(){original.recycle()}
        }
        return source.use{CwaHighDefinitionRadar.decode(it,tiles)}
    }
    @Test fun closeUpTilesBlendMeasuredLevelsIntoSharpRampColours() {
        // z11 children of the rainy z9 tile: about 105 source cells across 512 px, so blended.
        val tiles=(0 until 4).flatMap{dy->(0 until 4).map{dx->RadarTile(11,1700+dx,896+dy,1700+dx)}}
        val bytes=resource("CV1_TW_3600_202610290630.png")
        val png=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        val ramp=HashSet<Int>()
        for(y in 2450 until 3580){val c=png.getPixel(3450,y);val r=(c ushr 16)and 255;val g=(c ushr 8)and 255;val b=c and 255
            if(maxOf(r,g,b)-minOf(r,g,b)>60)ramp.add(c and 0xFFFFFF)}
        png.recycle()
        var solid=0;var edge=0
        decode("CV1_TW_3600_202610290630.png",tiles).values.forEach{image->
            assertEquals(512,image.width)
            for(y in 0 until 512)for(x in 0 until 512){
                val v=image.getPixel(x,y);val a=v ushr 24
                if(a==0)continue
                assertTrue("Colour outside the CWA ramp",(v and 0xFFFFFF) in ramp)
                if(a==255)solid++ else edge++
            }
        }
        assertTrue("Close-up lost the rain",solid>5000)
        // Edges fade over about a pixel instead of stair-stepping.
        assertTrue("No soft edge pixels",edge>0)
        assertTrue(edge<solid)
        assertEquals(11,CwaHighDefinitionRadar.MAX_ZOOM)
    }
    @Test fun phonesWithMoreMemoryHoldMoreSourceTiles() {
        assertEquals(2,RadarPlayback.sourceBudget(12_000_000_000L,true))
        assertEquals(9,RadarPlayback.sourceBudget(8_000_000_000L,false))
        assertEquals(6,RadarPlayback.sourceBudget(6_000_000_000L,false))
        assertEquals(4,RadarPlayback.sourceBudget(4_000_000_000L,false))
        val c=RadarCamera(23.5,120.7,10.5)
        assertTrue(RadarPlayback.tiles(c,1080f,1900f,3f,9,11).distinctBy{Triple(it.z,it.x,it.y)}.size<=9)
    }
    @Test fun publicTimelineKeepsOriginalTimesAndRejectsUnexpectedSourcesAndFutureData() {
        val now=Instant.parse("2026-10-28T22:40:00Z").toEpochMilli()
        val timeline=CwaHighDefinitionRadar.parse(String(resource("Observe_radar.js")),now)
        assertEquals(13,timeline.frames.size)
        assertEquals("cwa:CV1_TW_3600_202610290630.png",timeline.frames.last().path)
        assertEquals(Instant.parse("2026-10-28T22:30:00Z").epochSecond,timeline.frames.last().time)
        assertEquals(13,timeline.frames.map{it.time}.distinct().size)
        assertTrue(runCatching{CwaHighDefinitionRadar.parse("var RadarImg={x:'CV1_3600_202610290630.png'}",now)}.isFailure)
        assertTrue(runCatching{CwaHighDefinitionRadar.parse("var RadarImg={x:'CV1_TW_3600_202710290630.png'}",now)}.isFailure)
        assertTrue(CwaHighDefinitionRadar.covers(25.0,121.5));assertFalse(CwaHighDefinitionRadar.covers(35.0,139.0))
    }
    @Test fun originalPixelsReprojectToGeographicLocationWithoutPrintingMapInkAsRain() {
        val tile=RadarTile(9,425,224,425)
        val image=decode("CV1_TW_3600_202610290630.png",listOf(tile)).getValue(tile)
        assertEquals(512,image.width);assertEquals(512,image.height)
        val png=android.graphics.BitmapFactory.decodeByteArray(resource("CV1_TW_3600_202610290630.png"),0,resource("CV1_TW_3600_202610290630.png").size)
        var rain=0
        for(y in 0 until 512)for(x in 0 until 512) {
            val value=image.getPixel(x,y)
            if(value ushr 24==0)continue
            rain++
            val lon=(tile.x+(x+.5)/512)/512*360-180
            val lat=RadarProjection.latitude((tile.y+(y+.5)/512)/512)
            val sx=kotlin.math.floor((lon-118)*600).toInt();val sy=kotlin.math.floor((26.5-lat)*600).toInt()
            assertEquals(png.getPixel(sx,sy),value)
            val r=(value ushr 16)and 255;val g=(value ushr 8)and 255;val b=value and 255
            assertTrue(maxOf(r,g,b)-minOf(r,g,b)>60)
            assertFalse(CwaHighDefinitionRadar.annotation(sx,sy))
        }
        assertTrue("Real southwestern ocean rain was lost",rain>1000)
        png.recycle()
        val directory=File("build/cwa-hd-renders").apply{mkdirs()}
        File(directory,"native-rain-pixels.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun highResolutionHistoryUsesExistingContinuousPainterAndDoesNotRecolourRainInNightMode() {
        val tile=RadarTile(9,425,224,425)
        val a=decode("CV1_TW_3600_202610290620.png",listOf(tile)).mapValues{it.value.asImageBitmap()}
        val b=decode("CV1_TW_3600_202610290630.png",listOf(tile)).mapValues{it.value.asImageBitmap()}
        val motion=RadarMotionField.estimate(a,b)
        val first=RadarFrame(1,"cwa:CV1_TW_3600_202610290620.png");val second=RadarFrame(601,"cwa:CV1_TW_3600_202610290630.png")
        val camera=RadarCamera(RadarProjection.latitude(224.5/512),RadarProjection.longitude(425.5/512),9.0)
        val dir=File("build/cwa-hd-renders").apply{mkdirs()}
        for(dark in listOf(false,true))for(fraction in listOf(0f,.25f,.5f,.75f,1f)) {
            val out=ImageBitmap(512,512)
            CanvasDrawScope().draw(Density(2f),LayoutDirection.Ltr,Canvas(out),Size(512f,512f)) {
                drawRect(if(dark)RadarMapStyle.darkBackground else RadarMapStyle.lightBackground)
                RadarTemporalPainter().draw(this,camera,2f,RadarScene(first,second,fraction,a,b,motion),1f)
            }
            File(dir,"cwa-$dark-$fraction.png").outputStream().use{out.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertEquals(9,RadarPlayback.tiles(camera,512f,512f,2f,4,9).first().z)
        assertTrue(RadarPlayback.tiles(camera,512f,512f,2f,4,9).all{it.z<=9})
    }
    @Test fun repeatedWorldCopiesShareImagesAndWideViewsStayWithinResidentFrameBudget() {
        val tiles=listOf(RadarTile(2,3,1,3),RadarTile(2,3,1,7),RadarTile(2,3,1,-1))
        val images=decode("CV1_TW_3600_202610290630.png",tiles)
        assertEquals(3,images.size)
        assertSame(images[tiles[0]],images[tiles[1]])
        assertSame(images[tiles[0]],images[tiles[2]])
        val c=RadarCamera(23.5,120.7,2.0)
        val window=RadarPlayback.tiles(c,3500f,2200f,1f,4,9)
        assertTrue(window.isNotEmpty())
        assertTrue(window.distinctBy{Triple(it.z,it.x,it.y)}.size<=4)
    }
}
