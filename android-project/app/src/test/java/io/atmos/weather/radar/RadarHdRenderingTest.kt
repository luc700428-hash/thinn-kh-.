package io.atmos.weather.radar

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Size
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
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarHdRenderingTest {
    @Test fun deepZoomLoadsDetailedBaseWithoutExceedingRadarProviderLimit() {
        val c=RadarCamera(25.033,121.565,14.0)
        val base=RadarProjection.tiles(c,1200f,1800f,3f,RadarTilePolicy.BASE_MAX_ZOOM)
        val radar=RadarProjection.tiles(c,1200f,1800f,3f)
        assertTrue(base.isNotEmpty()&&radar.isNotEmpty())
        assertTrue(base.all{it.z==14});assertTrue(radar.all{it.z==7})
        assertTrue(base.size<=36&&radar.size<=4)
        base.forEach{assertTrue(RadarTilePolicy.imageUrl(null,it).startsWith("https://tile.openstreetmap.org/14/"))}
        val frame=RadarFrame(1,"/v2/radar/abcd1234")
        radar.forEach{assertTrue(RadarTilePolicy.imageUrl(frame,it).endsWith("/2/0_1.png"));assertTrue(RadarTilePolicy.imageUrl(frame,it).contains("/512/7/"))}
        assertTrue(runCatching{RadarTilePolicy.imageUrl(frame,base.first())}.isFailure)
    }
    @Test fun pinchAnchorStaysFixedAtStreetZoomAndLimitsAreFinite() {
        val c=RadarCamera(25.033,121.565,12.0)
        val after=c.zoomAt(4f,100f,90f,1200f,1800f,3f)
        fun x(cam:RadarCamera)=RadarProjection.x(cam.longitude)+(100-600)/cam.world(3f)
        fun y(cam:RadarCamera)=RadarProjection.y(cam.latitude)+(90-900)/cam.world(3f)
        assertEquals(14.0,after.zoom,1e-8);assertEquals(x(c),x(after),1e-9);assertEquals(y(c),y(after),1e-9)
        assertEquals(16.0,after.zoomAt(100f,600f,900f,1200f,1800f,3f).zoom,0.0)
        assertTrue(c.pan(Float.NaN,0f,3f)==c)
    }
    @Test fun visibleTileBudgetNeverLeavesAnUncoveredBottomRow() {
        for(zoom in listOf(2.0,7.0,12.0,16.0))for(lon in listOf(-179.9,121.565,179.9)) {
            val c=RadarCamera(25.033,lon,zoom)
            val tiles=RadarProjection.tiles(c,3500f,2200f,1f,RadarTilePolicy.BASE_MAX_ZOOM)
            assertTrue(tiles.isNotEmpty()&&tiles.size<=36)
            val span=256.0*2.0.pow(c.zoom-tiles.first().z)
            val world=c.world(1f)
            val left=tiles.minOf{it.column*span}-RadarProjection.x(lon)*world+1750
            val right=tiles.maxOf{(it.column+1)*span}-RadarProjection.x(lon)*world+1750
            assertTrue(left<=0&&right>=3500)
            val top=tiles.minOf{it.y*span}-RadarProjection.y(c.latitude)*world+1100
            val bottom=tiles.maxOf{(it.y+1)*span}-RadarProjection.y(c.latitude)*world+1100
            assertTrue(top<=maxOf(0.0,1100-RadarProjection.y(c.latitude)*world)+.01)
            assertTrue(bottom>=minOf(2200.0,1100+(1-RadarProjection.y(c.latitude))*world)-.01)
        }
    }
    @Test fun hdCacheDoesNotReuseOldSmoothedLowResolutionRadar() {
        val frame=RadarFrame(1,"/v2/radar/abcd1234");val tile=RadarTile(7,107,54,107)
        assertTrue(RadarTilePolicy.imageKey(frame,tile).contains("hd-raw-512"))
        assertEquals("base/7/107/54",RadarTilePolicy.imageKey(null,tile))
        assertTrue(runCatching{RadarTilePolicy.imageUrl(RadarFrame(1,"/v2/radar/../other"),tile)}.isFailure)
    }
    @Test fun overzoomRendersInsideViewportWithoutEnlargingTheSourceBitmap() {
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.RED)}
        val out=ImageBitmap(300,500);val camera=RadarCamera(25.033,121.565,16.0)
        val tile=RadarProjection.tiles(camera,300f,500f,3f).first()
        CanvasDrawScope().draw(Density(3f),LayoutDirection.Ltr,Canvas(out),Size(300f,500f)) {
            drawRadarLayers(camera,3f,emptyMap(),mapOf(tile to bitmap.asImageBitmap()),1f,true)
        }
        assertEquals(android.graphics.Color.RED,out.asAndroidBitmap().getPixel(150,250))
        assertEquals(512*512*4,bitmap.allocationByteCount)
        assertEquals(300*500*4,out.asAndroidBitmap().allocationByteCount)
    }
    @Test fun realTileFixtureRendersReadableLightAndDarkMaps() {
        val loader=javaClass.classLoader!!
        val meta=org.json.JSONObject(loader.getResourceAsStream("radar-hd/preview.json")!!.bufferedReader().readText())
        val c=meta.getJSONObject("camera");val camera=RadarCamera(c.getDouble("latitude"),c.getDouble("longitude"),c.getDouble("zoom"))
        val sources=meta.getJSONArray("sources")
        fun tile(i:Int):Pair<RadarTile,ImageBitmap> {
            val a=sources.getJSONObject(i);val bytes=loader.getResourceAsStream("radar-hd/"+a.getString("file"))!!.readBytes()
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            assertEquals(a.getString("sha256"),digest)
            val bitmap=RadarRepository.decodeTile(bytes)!!
            if(i==1)assertEquals(512,bitmap.width)
            val x=a.getInt("x");return RadarTile(a.getInt("z"),x,a.getInt("y"),x) to bitmap.asImageBitmap()
        }
        val base=tile(0);val echo=tile(1);val directory=File("build/radar-renders").apply{mkdirs()}
        for(dark in listOf(false,true)) {
            val out=ImageBitmap(512,512)
            CanvasDrawScope().draw(Density(2f),LayoutDirection.Ltr,Canvas(out),Size(512f,512f)) {
                drawRect(if(dark)RadarMapStyle.darkBackground else RadarMapStyle.lightBackground)
                drawRadarLayers(camera,2f,mapOf(base),mapOf(echo),.75f,dark)
            }
            val bitmap=out.asAndroidBitmap()
            File(directory,if(dark)"radar-dark.png" else "radar-light.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun darkModeChangesOnlyBasePixelsAndKeepsWeatherColours() {
        val white=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.WHITE)}.asImageBitmap()
        val red=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.RED)}.asImageBitmap()
        val camera=RadarCamera(0.0,0.0,2.0);val tile=RadarTile(2,2,2,2)
        fun draw(dark:Boolean,weather:Boolean):Bitmap {
            val out=ImageBitmap(512,512)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(out),Size(512f,512f)) {
                drawRect(if(dark)RadarMapStyle.darkBackground else RadarMapStyle.lightBackground)
                drawRadarLayers(camera,1f,mapOf(tile to white),if(weather)mapOf(tile to red) else emptyMap(),1f,dark)
            }
            return out.asAndroidBitmap()
        }
        val light=draw(false,false);val dark=draw(true,false)
        assertEquals(android.graphics.Color.WHITE,light.getPixel(384,384))
        val night=dark.getPixel(384,384);assertTrue(android.graphics.Color.red(night)<40&&android.graphics.Color.blue(night)<45)
        assertEquals(android.graphics.Color.RED,draw(false,true).getPixel(384,384))
        assertEquals(android.graphics.Color.RED,draw(true,true).getPixel(384,384))
        val directory=File("build/radar-renders").apply{mkdirs()}
        File(directory,"base-light.png").outputStream().use{light.compress(Bitmap.CompressFormat.PNG,100,it)}
        File(directory,"base-dark.png").outputStream().use{dark.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
