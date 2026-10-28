package io.atmos.weather.radar

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarMotionRenderingTest {
    private val directory=File("build/continuous-radar-renders").apply{mkdirs()}
    private fun save(bitmap:Bitmap,name:String){File(directory,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun cloud(shift:Float)=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).apply {
        android.graphics.Canvas(this).drawRect(96f+shift,192f,224f+shift,336f,Paint().apply{color=0xff1da8f2.toInt()})
    }.asImageBitmap()
    private fun render(camera:RadarCamera,density:Float,scene:RadarScene,base:Map<RadarTile,ImageBitmap> = emptyMap(),dark:Boolean=true,opacity:Float=.75f):Bitmap {
        val out=ImageBitmap(512,512)
        CanvasDrawScope().draw(Density(density),LayoutDirection.Ltr,Canvas(out),Size(512f,512f)) {
            if(base.isNotEmpty()){drawRect(if(dark)RadarMapStyle.darkBackground else RadarMapStyle.lightBackground);drawRadarLayers(camera,density,base,emptyMap(),opacity,dark)}
            RadarTemporalPainter().draw(this,camera,density,scene,opacity)
        }
        return out.asAndroidBitmap()
    }
    @Test fun measuredTranslationHasIntermediatePositionsWithoutGhostEdgesOrDimming() {
        val tile=RadarTile(7,107,55,107);val a=mapOf(tile to cloud(0f));val b=mapOf(tile to cloud(32f))
        val flow=RadarMotionField.estimate(a,b)!!
        assertTrue(flow.forward.values.any{grid->grid.dx.any{it>16f}})
        val camera=RadarCamera(RadarProjection.latitude(55.5/128),RadarProjection.longitude(107.5/128),7.0)
        val frames=listOf(RadarFrame(1000,"a"),RadarFrame(1600,"b"))
        var previous=0.0
        for(fraction in listOf(0f,.25f,.5f,.75f,1f)) {
            val out=render(camera,2f,RadarScene(frames[0],frames[1],fraction,a,b,flow))
            var weighted=0.0;var mass=0.0
            for(y in 0 until 512)for(x in 0 until 512) {
                val alpha=android.graphics.Color.alpha(out.getPixel(x,y));weighted+=x.toDouble()*alpha;mass+=alpha
            }
            val center=weighted/mass;assertTrue(center>=previous-1);previous=center
            val middle=out.getPixel(176,256)
            assertTrue("Temporal interpolation dimmed an unchanged cloud",android.graphics.Color.alpha(middle) in 185..193)
            assertTrue(abs(0x1d-android.graphics.Color.red(middle))<=3);assertTrue(abs(0xa8-android.graphics.Color.green(middle))<=3)
            if(fraction==.5f)assertTrue("The old cloud edge remained as a cross-fade ghost",android.graphics.Color.alpha(out.getPixel(100,256))<15)
            save(out,"synthetic-${(fraction*100).toInt()}.png")
        }
        assertEquals(191.5,previous,3.0)
    }
    @Test fun unmatchedPrecipitationDoesNotInventLongDistanceAdvection() {
        fun patch(x:Float,y:Float)=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).apply {
            android.graphics.Canvas(this).drawRect(x,y,x+60,y+60,Paint().apply{color=android.graphics.Color.CYAN})
        }.asImageBitmap()
        val tile=RadarTile(7,107,55,107)
        val flow=RadarMotionField.estimate(mapOf(tile to patch(60f,60f)),mapOf(tile to patch(360f,360f)))!!
        assertTrue(flow.forward.values.all{it.dx.all{v->v==0f}&&it.dy.all{v->v==0f}})
    }
    @Test fun motionWorkIsCancellableAndRejectsMismatchedWindows() {
        val tile=RadarTile(7,107,55,107)
        assertNull(RadarMotionField.estimate(mapOf(tile to cloud(0f)),emptyMap()))
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            RadarMotionField.estimate(mapOf(tile to cloud(0f)),mapOf(tile to cloud(32f))){throw kotlinx.coroutines.CancellationException()}
        }
    }
    @Test fun realAdjacentTaiwanObservationsKeepGeographyAndRenderBothThemes() {
        val loader=javaClass.classLoader!!
        fun resource(name:String)=loader.getResourceAsStream("continuous-radar/$name")!!.use{it.readBytes()}
        fun images(meta:JSONObject,time:Long?=null):Map<RadarTile,ImageBitmap> {
            val items=meta.getJSONArray("sources")
            return (0 until items.length()).map{items.getJSONObject(it)}.filter{time==null||it.optLong("time")==time}.associate {s->
                val bytes=resource(s.getString("file"));val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
                assertEquals(s.getString("sha256"),digest)
                RadarTile(s.getInt("z"),s.getInt("x"),s.getInt("y"),s.getInt("x")) to RadarRepository.decodeTile(bytes)!!.asImageBitmap()
            }
        }
        val meta=JSONObject(String(resource("sources.json"),Charsets.UTF_8))
        val frames=meta.getJSONArray("frames");val first=frames.getJSONObject(0);val second=frames.getJSONObject(1)
        val a=images(meta,first.getLong("time"));val b=images(meta,second.getLong("time"))
        val base=images(JSONObject(String(resource("base-sources.json"),Charsets.UTF_8)))
        assertEquals(4,a.size);assertEquals(a.keys,b.keys)
        val motion=RadarMotionField.estimate(a,b)
        val camera=RadarCamera(RadarProjection.latitude(55.0/128),RadarProjection.longitude(107.0/128),7.0)
        val one=RadarFrame(first.getLong("time"),first.getString("path"));val two=RadarFrame(second.getLong("time"),second.getString("path"))
        val themes=mutableListOf<Int>()
        for(dark in listOf(false,true)) {
            val clean=render(camera,1f,RadarScene(one,one,0f,emptyMap(),emptyMap(),null),base,dark)
            for(fraction in listOf(0f,.25f,.5f,.75f,1f)) {
                val out=render(camera,1f,RadarScene(one,two,fraction,a,b,motion),base,dark)
                assertEquals(512,out.width);assertEquals(512,out.height)
                save(out,"real-$dark-${(fraction*100).toInt()}.png")
                if(fraction==0f||fraction==1f) {
                    val expected=ImageBitmap(512,512)
                    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(expected),Size(512f,512f)) {
                        drawRect(if(dark)RadarMapStyle.darkBackground else RadarMapStyle.lightBackground)
                        drawRadarLayers(camera,1f,base,if(fraction==0f)a else b,.75f,dark)
                    }
                    val reference=expected.asAndroidBitmap()
                    var error=0L
                    for(y in 0 until 512 step 3)for(x in 0 until 512 step 3) {
                        val actual=out.getPixel(x,y);val original=reference.getPixel(x,y)
                        error+=abs(android.graphics.Color.red(actual)-android.graphics.Color.red(original))+abs(android.graphics.Color.green(actual)-android.graphics.Color.green(original))+abs(android.graphics.Color.blue(actual)-android.graphics.Color.blue(original))
                    }
                    assertTrue("Real observation endpoints changed intensity colours",error<12000)
                }
            }
            themes+=clean.getPixel(0,0)
        }
        assertNotEquals("Dark and light modes must have distinct maps",themes[0],themes[1])
    }
}
