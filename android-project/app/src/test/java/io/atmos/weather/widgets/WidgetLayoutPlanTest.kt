package io.atmos.weather.widgets
import org.junit.Assert.*
import org.junit.Test
class WidgetLayoutPlanTest {
    @Test fun sizeChangesInformationDensityInsteadOfScalingOneLayout() {
        assertTrue(WidgetLayoutPlan.create(320f,90f).strip)
        assertEquals(WidgetLayoutPlan.Mode.SQUARE,WidgetLayoutPlan.create(160f,160f).mode)
        assertEquals(WidgetLayoutPlan.Mode.WIDE,WidgetLayoutPlan.create(320f,180f).mode)
        assertEquals(WidgetLayoutPlan.Mode.PORTRAIT,WidgetLayoutPlan.create(180f,320f).mode)
        assertEquals(WidgetLayoutPlan.Mode.EXPANDED,WidgetLayoutPlan.create(360f,360f).mode)
    }
    @Test fun largeTextIsFittedToAvailableSpaceAfterSystemScaling() {
        for(w in listOf(100f,160f,320f,600f))for(h in listOf(60f,160f,180f,360f)) {
            val p=WidgetLayoutPlan.create(w,h,1.5f,1.5f)
            assertTrue(p.hero*1.14f<=p.innerHeight)
            assertTrue(p.icon<=p.innerHeight)
            assertTrue(p.hours in 1..6)
            assertEquals(p.body,p.sp(p.body)*p.systemFontScale,.001f)
        }
    }
    @Test fun resizingGrowsTheTypographyAsWellAsTheAvailableSpace() {
        val sizes=listOf(180f,320f,480f,720f).map{WidgetLayoutPlan.create(it,it)}
        sizes.zipWithNext().forEach{(a,b)->
            assertTrue(b.hero>a.hero);assertTrue(b.city>a.city);assertTrue(b.body>a.body)
            assertTrue(b.caption>a.caption);assertTrue(b.clock>a.clock)
        }
    }
    @Test fun missingAndInvalidMeasurementsStayFinite() {
        val p=WidgetLayoutPlan.create(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN,Float.NaN)
        assertTrue(p.width.isFinite()&&p.height.isFinite()&&p.clock.isFinite())
        assertTrue(p.hero>0&&p.dial>0)
    }
}
