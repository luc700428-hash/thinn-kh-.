package io.atmos.weather.festival

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FestivalTest {
    private val tw=ZoneId.of("Asia/Taipei")
    private fun at(y:Int,m:Int,d:Int,h:Int=12,min:Int=0,s:Int=0)=LocalDateTime.of(y,m,d,h,min,s).atZone(tw).toInstant().toEpochMilli()

    @Test fun lunarFestivalsLandOnTheirTaiwanDates() {
        assertEquals(LocalDate.of(2025,1,29),FestivalCalendar.lunarNewYear(2025))
        assertEquals(LocalDate.of(2026,2,17),FestivalCalendar.lunarNewYear(2026))
        assertEquals(LocalDate.of(2027,2,6),FestivalCalendar.lunarNewYear(2027))
        assertEquals(LocalDate.of(2028,1,26),FestivalCalendar.lunarNewYear(2028))
        assertEquals(LocalDate.of(2026,3,3),FestivalCalendar.lunar(2026,1,15))
        assertEquals(LocalDate.of(2026,6,19),FestivalCalendar.lunar(2026,5,5))
        assertEquals(LocalDate.of(2026,8,19),FestivalCalendar.lunar(2026,7,7))
        assertEquals(LocalDate.of(2026,8,27),FestivalCalendar.lunar(2026,7,15))
        assertEquals(LocalDate.of(2026,9,25),FestivalCalendar.lunar(2026,8,15))
        assertEquals(LocalDate.of(2025,10,6),FestivalCalendar.lunar(2025,8,15))
        assertEquals(LocalDate.of(2025,5,31),FestivalCalendar.lunar(2025,5,5))
        assertEquals(LocalDate.of(2026,4,5),FestivalCalendar.qingming(2026))
        assertEquals(LocalDate.of(2025,4,4),FestivalCalendar.qingming(2025))
        assertEquals(LocalDate.of(2026,12,22),FestivalCalendar.winterSolstice(2026))
        assertEquals(LocalDate.of(2025,12,21),FestivalCalendar.winterSolstice(2025))
    }

    @Test fun everyFestivalHasADayAndTheRightOneWins() {
        val seen=FestivalCalendar.days(2026).map{it.second}.toSet()
        assertEquals(Festival.entries.toSet(),seen)
        assertEquals(Festival.MOTHERS_DAY,FestivalCalendar.at(at(2026,5,10)))
        assertEquals(Festival.CHILDREN,FestivalCalendar.at(at(2026,4,4)))
        assertEquals(Festival.QINGMING,FestivalCalendar.at(at(2026,4,5)))
        assertEquals(Festival.CHILDREN,FestivalCalendar.at(at(2025,4,4)))
        assertEquals(Festival.LUNAR_NEW_YEAR,FestivalCalendar.at(at(2026,2,16,22)))
        assertEquals(Festival.LUNAR_NEW_YEAR,FestivalCalendar.at(at(2027,2,5)))
        assertEquals(Festival.LANTERN,FestivalCalendar.at(at(2026,3,3)))
        assertEquals(Festival.TEACHERS_DAY,FestivalCalendar.at(at(2026,9,28)))
        assertEquals(Festival.NATIONAL_DAY,FestivalCalendar.at(at(2026,10,10)))
        assertEquals(Festival.HALLOWEEN,FestivalCalendar.at(at(2026,10,31,21)))
        assertEquals(Festival.MID_AUTUMN,FestivalCalendar.at(at(2026,9,25)))
        assertNull(FestivalCalendar.at(at(2026,11,25)))
        // Christmas: from Christmas Eve 00:00 to the end of Christmas Day.
        assertNull(FestivalCalendar.at(at(2026,12,23,23,59,59)))
        assertEquals(Festival.CHRISTMAS,FestivalCalendar.at(at(2026,12,24,0,0,0)))
        assertEquals(Festival.CHRISTMAS,FestivalCalendar.at(at(2026,12,25,23,59,59)))
        assertNull(FestivalCalendar.at(at(2026,12,26,0,0,0)))
        // Names never call Taiwan anything but Taiwan.
        Festival.entries.forEach { assertFalse(it.label+it.greeting, (it.label+it.greeting).contains("中國") || (it.label+it.greeting).contains("中華台北")) }
    }

    @Test fun newYearCountsDownTenMinutesThenFiresThreeMinutes() {
        assertNull(FestivalCalendar.countdown(at(2026,12,31,23,49,59)))
        val c=FestivalCalendar.countdown(at(2026,12,31,23,50,0))!!
        assertEquals(at(2027,1,1,0,0,0),c.target)
        assertTrue(c.after.contains("2027"))
        assertNull(FestivalCalendar.show(at(2026,12,31,23,59,59)))
        val show=FestivalCalendar.show(at(2027,1,1,0,0,0))!!
        assertEquals(180,show.seconds); assertFalse(show.firecrackers)
        assertNotNull(FestivalCalendar.show(at(2027,1,1,0,2,59)))
        assertNull(FestivalCalendar.show(at(2027,1,1,0,3,0)))
        assertEquals(Festival.NEW_YEARS_EVE,FestivalCalendar.at(at(2027,1,1,0,1)))
        assertEquals(Festival.NEW_YEAR,FestivalCalendar.at(at(2027,1,1,9)))
        // Lunar new year's eve: the same countdown, a show with firecrackers.
        assertNotNull(FestivalCalendar.countdown(at(2027,2,5,23,55)))
        assertTrue(FestivalCalendar.show(at(2027,2,6,0,1))!!.firecrackers)
        assertNotNull(FestivalCalendar.show(at(2026,10,10,20,1)))
        assertNull(FestivalCalendar.show(at(2026,10,10,19,59)))
    }

    @Test fun theShowIsDeterministicBusyAtTheEndAndOnScreen() {
        val a=Fireworks.show(42L); val b=Fireworks.show(42L)
        assertEquals(a,b)
        assertTrue(a.size in 120..400)
        val early=a.count{it.launch<60f}; val finale=a.count{it.launch>152f}
        assertTrue("finale $finale vs first minute $early",finale>early)
        assertTrue(a.all{it.end<=FestivalCalendar.SHOW_SECONDS+5f})
        assertTrue(Fireworks.show(42L,density=.55f).size<a.size*.75f)
        val out=FloatArray(400)
        for(sh in a) {
            val n=Fireworks.stars(sh,sh.burst+.8f,1080f,2340f,out)
            assertTrue(n>0)
            for(i in 0 until n){ assertTrue(out[i*2] in -400f..1480f); assertTrue(out[i*2+1] in -200f..2340f) }
        }
        assertTrue(Fireworks.cues(a,0f,180f).count{it==Fireworks.Cue.BOOM||it==Fireworks.Cue.BOOM_SMALL}==a.size)
    }

    @Test fun previewStartsAtTheBestMomentAndEnds() {
        val real=at(2026,11,25,9)
        FestivalDirector.startPreview(Festival.NEW_YEARS_EVE,real)
        assertNotNull(FestivalCalendar.countdown(FestivalDirector.now(real)))
        assertNotNull(FestivalCalendar.show(FestivalDirector.now(real+13_000)))
        FestivalDirector.startPreview(Festival.CHRISTMAS,real)
        assertEquals(Festival.CHRISTMAS,FestivalCalendar.at(FestivalDirector.now(real)))
        FestivalDirector.startPreview(Festival.NATIONAL_DAY,real)
        assertNotNull(FestivalCalendar.show(FestivalDirector.now(real+3_000)))
        for(f in Festival.entries){ FestivalDirector.startPreview(f,real)
            val fest=FestivalCalendar.at(FestivalDirector.now(real+1_000))
            assertTrue("$f -> $fest", fest==f || (f==Festival.NEW_YEARS_EVE && fest==Festival.NEW_YEARS_EVE) || (f==Festival.LUNAR_NEW_YEAR && fest==Festival.LUNAR_NEW_YEAR)) }
        FestivalDirector.expire(real+FestivalDirector.PREVIEW_MS+1)
        assertNull(FestivalDirector.preview)
        assertEquals(real,FestivalDirector.now(real))
    }

    @Test fun everySceneDrawsAContactSheet() {
        val cw=400;val ch=820;val cols=6
        val cells=Festival.entries.size+2
        val rows=(cells+cols-1)/cols
        val img=ImageBitmap(cols*cw,rows*ch)
        val scene=FestivalScene(1.5f,false)
        val painted=IntArray(cells)
        CanvasDrawScope().draw(Density(1.5f),LayoutDirection.Ltr,Canvas(img),Size((cols*cw).toFloat(),(rows*ch).toFloat())) {
            for(i in 0 until cells) {
                val x=(i%cols)*cw.toFloat(); val y=(i/cols)*ch.toFloat()
                translate(x,y){ clipRect(0f,0f,cw.toFloat(),ch.toFloat()) {
                    drawRect(if(i%2==0)Color(0xFF9CC7F0) else Color(0xFF14203A),size=Size(cw.toFloat(),ch.toFloat()))
                }}
            }
        }
        val bg=img.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
        val cellScope=CanvasDrawScope()
        for(i in 0 until cells) {
            val x=(i%cols)*cw; val y=(i/cols)*ch
            val cell=ImageBitmap(cw,ch)
            cellScope.draw(Density(1.5f),LayoutDirection.Ltr,Canvas(cell),Size(cw.toFloat(),ch.toFloat())) {
                drawRect(if(i%2==0)Color(0xFF9CC7F0) else Color(0xFF14203A))
                val t=1234.5
                with(scene) {
                    when(if(i<Festival.entries.size)Festival.entries[i] else null) {
                        Festival.CHRISTMAS -> { snow(t); santa(5f,0L,t) }
                        Festival.NEW_YEAR, Festival.NEW_YEARS_EVE -> { sparkles(t); confetti(t) }
                        Festival.LUNAR_NEW_YEAR -> { hangingLanterns(t); sparkles(t); firecrackerSparks(t) }
                        Festival.LANTERN -> { skyLanterns(t); hangingLanterns(t) }
                        Festival.VALENTINE -> hearts(t,false)
                        Festival.QIXI -> hearts(t,true)
                        Festival.CHILDREN -> balloons(t)
                        Festival.QINGMING -> willowLeaves(t)
                        Festival.MOTHERS_DAY -> carnations(t)
                        Festival.DRAGON_BOAT -> dragonBoat(5.2f,0L,t)
                        Festival.FATHERS_DAY -> ties(t)
                        Festival.GHOST_FESTIVAL -> waterLanterns(t)
                        Festival.MID_AUTUMN -> { moon(t); hangingLanterns(t) }
                        Festival.TEACHERS_DAY -> paperPlanes(t)
                        Festival.NATIONAL_DAY -> { confetti(t); flag(t,36f) }
                        Festival.HALLOWEEN -> { halloween(t); ghost(4f,0L,t) }
                        Festival.WINTER_SOLSTICE -> tangyuan(t)
                        null -> { drawRect(Color(0xFF060814),alpha=.55f); fireworks(Fireworks.show(7L),if(i==cells-2)42f else 166f) }
                    }
                }
            }
            val c=cell.asAndroidBitmap(); val base=c.getPixel(1,1)
            var n=0; for(yy in 0 until ch step 2)for(xx in 0 until cw step 2)if(c.getPixel(xx,yy)!=base)n++
            painted[i]=n
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(img),Size((cols*cw).toFloat(),(rows*ch).toFloat())) { drawImage(cell,androidx.compose.ui.geometry.Offset(x.toFloat(),y.toFloat())) }
        }
        File("build/festival").apply{mkdirs()}.let{File(it,"sheet.png").outputStream().use{s->img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,s)}}
        painted.forEachIndexed{i,n-> assertTrue("cell $i painted $n",n>300)}
        assertNotNull(bg)
    }
}
