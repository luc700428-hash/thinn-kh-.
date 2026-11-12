package io.atmos.weather.widgets
import io.atmos.weather.domain.model.WeatherCondition
import org.junit.Assert.*
import org.junit.Test
class AdaptiveWidgetLayoutTest{
    private val data=OpticalWidgetData("新北市 · 秀景里","24°",WeatherCondition.PARTLY_CLOUDY,true,1,
        WidgetPresentationRules.Freshness.FRESH,"40%","最高 26°  最低 21°","68%","2.8 m/s","32","3.2",
        (0..5).map{WidgetHour("${14+it}:00","${24-it}°","20%",2,true)},"07:30 · 早安",null,"外出","尚無近期地震推播","請以官方災防告警為準",false)
    @Test fun extremeHostShapesReflowAndPrimaryFontsGrowMonotonically(){
        for((w,h) in listOf(100f to 60f,1200f to 96f,96f to 1200f,160f to 160f,320f to 320f,320f to 600f,600f to 180f))
        for(family in 0..8)for(system in listOf(1f,1.5f,2f))for(app in listOf(1f,1.5f)){
            var previous=0f
            for(custom in listOf(.8f,1f,1.3f,1.6f)){
                val p=WidgetLayoutPlan.create(w,h,app,system,custom);val regions=AdaptiveWidgetLayout.create(p,family,data,true)
                assertTrue("Empty $family $w/$h $system/$app/$custom",regions.isNotEmpty())
                regions.forEach{r->assertTrue("Out of bounds: $r",r.x>=0&&r.y>=0&&r.width>0&&r.height>0&&r.x+r.width<=p.innerWidth+.1f&&r.y+r.height<=p.innerHeight+.1f)}
                val texts=regions.filter{it.font>0}
                texts.forEachIndexed{i,a->texts.drop(i+1).forEach{b->
                    assertFalse("Text overlaps: $a / $b",a.x<b.x+b.width-.1f&&a.x+a.width>b.x+.1f&&a.y<b.y+b.height-.1f&&a.y+a.height>b.y+.1f)
                }}
                val reading=regions.firstOrNull{it.kind==if(family in listOf(2,6,8))WidgetRegion.Kind.TIME else WidgetRegion.Kind.TEMPERATURE}
                if(family !in listOf(1,3)){
                    assertNotNull("Missing reading $family $w/$h $system/$app/$custom",reading)
                    assertTrue("Preference shrank a reading $family $w/$h $custom",reading!!.font>previous);previous=reading.font
                }
                if(family==1&&!p.strip)assertTrue("Forecast was hidden",regions.any{it.text=="14:00"})
                if(family in listOf(4,5)&&!p.strip)assertTrue("Safety message was hidden",regions.any{it.text==data.alert})
            }
        }
        for((w,h) in listOf(160f to 160f,320f to 320f,96f to 800f,600f to 180f))for(family in listOf(0,2,6,7,8)){
            var before=0f
            for(system in listOf(1f,1.5f,2f)){
                val p=WidgetLayoutPlan.create(w,h,systemScale=system)
                val r=AdaptiveWidgetLayout.create(p,family,data,true).first{it.kind==if(family in listOf(2,6,8))WidgetRegion.Kind.TIME else WidgetRegion.Kind.TEMPERATURE}
                assertTrue("System text scale shrank the primary $family $w/$h/$system",r.font>=before-.001f);before=r.font
            }
        }
        val original=WidgetLayoutPlan.create(320f,320f,appScale=1f)
        val appLarge=WidgetLayoutPlan.create(320f,320f,appScale=1.5f)
        assertEquals("App reading size must not replace launcher typography",original.body,appLarge.body,.001f)
        assertEquals(WidgetLayoutPlan.Mode.PORTRAIT,WidgetLayoutPlan.create(96f,1200f).mode)
        assertEquals(WidgetLayoutPlan.Mode.STRIP,WidgetLayoutPlan.create(1200f,96f).mode)
    }
}
