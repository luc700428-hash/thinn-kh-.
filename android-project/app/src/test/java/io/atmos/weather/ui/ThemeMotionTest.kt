package io.atmos.weather.ui
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
class ThemeMotionTest {
 @Test fun allPastelsHaveReadableSurfaceAndWidgetRoles() {
  assertEquals(12,AppThemeColor.entries.size)
  for(theme in AppThemeColor.entries){
   val p=BrandPalette.scheme(theme,false);assertTrue(p.background.red>.85f&&p.background.green>.85f&&p.background.blue>.85f)
   for(dark in listOf(false,true)){val c=WeatherCardPalettes.fromScheme(BrandPalette.scheme(theme,dark))
    assertTrue(colorContrast(c.ink,c.fill)>=4.5f);assertTrue(colorContrast(c.secondary,c.fill)>=4.5f)}
   for(index in 0..4){val c=io.atmos.weather.widgets.WidgetGlassRenderer.paletteFor(theme,index)
    for(fill in listOf(Color(c.top),Color(c.bottom))){assertTrue(colorContrast(Color(c.ink),fill)>=4.5f);assertTrue(colorContrast(Color(c.secondary),fill)>=4.5f)}}
  }
 }
 @Test fun changingColorsHasIntermediateFramesAndExactEndpoints() {
  val a=ThemeFrame.at(AppThemeColor.FOREST,false);val b=ThemeFrame.at(AppThemeColor.ROSE,false);val mid=a.toward(b,.4f)
  assertNotEquals(a.accent,mid.accent);assertNotEquals(b.accent,mid.accent)
  assertNotEquals(a.brand(0xFFA8D8BF.toInt()),mid.brand(0xFFA8D8BF.toInt()))
  assertEquals(a,a.toward(b,0f));assertEquals(b,a.toward(b,1f))
  assertEquals(mid,mid.toward(ThemeFrame.at(AppThemeColor.OCEAN,false),0f))
 }
 @Test fun textStaysReadableDuringColorAndAppearanceChanges() {
  for(theme in AppThemeColor.entries)for(dark in listOf(false,true))for(step in 0..20){
   val p=ThemeFrame.at(AppThemeColor.FOREST,!dark).toward(ThemeFrame.at(theme,dark),step/20f).scheme()
   for((a,b) in listOf(p.onPrimary to p.primary,p.onSurface to p.surface,p.onSurfaceVariant to p.surfaceVariant,p.onBackground to p.background))assertTrue(colorContrast(a,b)>=4.5f)
  }
 }
 @Test fun closingAnOverlayKeepsTheOriginalThemeHostAlive() {
  val a=Any();val b=Any();val frame=ThemeFrame.at(AppThemeColor.ROSE,false)
  try{ThemeMotion.attach(a,frame);ThemeMotion.attach(b,frame);assertSame(b,ThemeMotion.owner)
   ThemeMotion.detach(b);assertTrue(ThemeMotion.active);assertSame(a,ThemeMotion.owner)
  }finally{ThemeMotion.detach(b);ThemeMotion.detach(a)}
  assertFalse(ThemeMotion.active)
 }
 @Test fun weatherDayNightIsIndependentOfFormAppearanceAndHasReadableInk() {
  assertEquals(WeatherDayPhase.MORNING,WeatherDayPhase.resolve(true,7));assertEquals(WeatherDayPhase.EVENING,WeatherDayPhase.resolve(true,17))
  assertEquals(WeatherDayPhase.NIGHT,WeatherDayPhase.resolve(false,7))
  val day=WeatherSkyPalette.create(WeatherDayPhase.MORNING,0f,0f);val night=WeatherSkyPalette.create(WeatherDayPhase.NIGHT,1f,.7f)
  assertNotEquals(day.top,night.top)
  for(p in listOf(day,night))assertTrue(colorContrast(p.ink,p.top)>=4.5f)
 }
}
