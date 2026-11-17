package io.atmos.weather.widgets
import io.atmos.weather.domain.model.WeatherCondition
import org.junit.Assert.*
import org.junit.Test

/** Every Q-version widget, at every launcher size from 2×2 to 10×10, in both orientations. */
class QBuddyLayoutTest {
    private val data=OpticalWidgetData("新北市 · 秀景里","24°",WeatherCondition.PARTLY_CLOUDY,true,1,
        WidgetPresentationRules.Freshness.FRESH,"40%","最高 26°  最低 21°","68%","2.8 m/s","32","3.2",
        (0..7).map{WidgetHour("${14+it}:00","${24-it}°","20%",2,true)},"07:30 · 早安",System.currentTimeMillis()+3_600_000,"外出",
        "花蓮縣近海 規模 5.2","最大震度 4 級 · 深度 18 km",true,2,
        listOf("今天","明天","週四","週五","週六","週日","週一").mapIndexed{i,l->WidgetDay(l,"${18+i}–${25+i}°",if(i%2==0)"60%" else "",2)},
        "預計 15:00 前後可能下雨（降雨機率 70%）","明天 18–23°，比今天涼 3°","帶把折疊傘比較安心 · 長袖或薄外套")
    // Launcher cells are roughly 70–90 dp wide and 80–110 dp tall depending on the phone and grid.
    private val sizes=buildList{for(cols in 2..10)for(rows in 2..10)for((cw,ch) in listOf(72f to 84f,88f to 108f))add(cols*cw to rows*ch)}

    @Test fun nothingOverlapsOrLeavesTheWidgetAtAnyCellSize(){
        for((w,h) in sizes)for(family in 7..11)for(system in listOf(1f,1.3f,2f))for(custom in listOf(.8f,1f,1.6f)){
            val p=WidgetLayoutPlan.create(w,h,1f,system,custom)
            val regions=AdaptiveWidgetLayout.create(p,family,data,true)
            val tag="family $family ${w}x$h system $system custom $custom"
            regions.forEach{r->assertTrue("Out of bounds $tag: $r",r.x>=-.1f&&r.y>=-.1f&&r.width>0&&r.height>0&&r.x+r.width<=p.innerWidth+.2f&&r.y+r.height<=p.innerHeight+.2f)}
            regions.forEachIndexed{i,a->regions.drop(i+1).forEach{b->
                assertFalse("Overlap $tag: $a / $b",a.x<b.x+b.width-.2f&&a.x+a.width>b.x+.2f&&a.y<b.y+b.height-.2f&&a.y+a.height>b.y+.2f)
            }}
            // The essentials are always there: the buddy, and the time or the temperature.
            assertTrue("No buddy $tag",regions.any{it.kind==WidgetRegion.Kind.ART&&it.width>=20f})
            assertTrue("No reading $tag",regions.any{it.kind==if(family==8)WidgetRegion.Kind.TIME else WidgetRegion.Kind.TEMPERATURE})
            if(family==8)assertTrue("No date $tag",regions.any{it.kind==WidgetRegion.Kind.DATE})
            regions.filter{it.font>0}.forEach{assertTrue("Unreadable text $tag: $it",it.font>=7f)}
        }
    }
    @Test fun eachWidgetShowsItsOwnContentOnceThereIsRoom(){
        fun at(family:Int,w:Float,h:Float)=AdaptiveWidgetLayout.create(WidgetLayoutPlan.create(w,h),family,data,true)
        // 4×4 and up: the widget's own block is visible.
        for((w,h) in listOf(320f to 360f,400f to 600f,720f to 900f,880f to 1080f)){
            assertTrue("forecast days ${w}x$h",at(9,w,h).count{it.kind==WidgetRegion.Kind.DAY_ART}>=2)
            assertTrue("outing advice ${w}x$h",at(10,w,h).any{it.text==data.outlook})
            assertTrue("quake message ${w}x$h",at(11,w,h).any{it.text==data.alert})
            assertTrue("clock alarm ${w}x$h",at(8,w,h).any{it.kind==WidgetRegion.Kind.COUNTDOWN})
            assertTrue("weather clock ${w}x$h",at(7,w,h).any{it.kind==WidgetRegion.Kind.DATE})
        }
        for((w,h) in listOf(400f to 600f,720f to 900f))assertTrue("weather hours ${w}x$h",at(7,w,h).any{it.kind==WidgetRegion.Kind.HOUR_ART})
        // The biggest sizes use the room: more days and two columns.
        val huge=at(9,880f,1080f)
        assertEquals(7,huge.count{it.kind==WidgetRegion.Kind.DAY_ART})
        assertTrue("two columns",at(7,880f,1080f).map{it.x}.any{it>400f})
        // Bigger widgets get a bigger buddy.
        val small=at(7,160f,180f).first{it.kind==WidgetRegion.Kind.ART}.width
        val large=at(7,720f,900f).first{it.kind==WidgetRegion.Kind.ART}.width
        assertTrue("buddy grows $small -> $large",large>small*2)
    }
}
