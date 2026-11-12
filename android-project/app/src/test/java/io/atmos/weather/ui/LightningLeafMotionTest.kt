package io.atmos.weather.ui

import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LightningLeafMotionTest {
 @Test fun coherentNoiseIsSmoothBoundedAndDifferentForEachLeaf() {
  for(seed in 1..20)for(step in 0..1000){
   val t=step/100f;val a=MotionNoise.value(t,seed,3f);val b=MotionNoise.value(t+.0001f,seed,3f)
   assertTrue(a in -1f..1f);assertTrue(abs(a-b)<.003f)
  }
  assertNotEquals(MotionNoise.value(2.3f,4),MotionNoise.value(2.3f,5))
  assertEquals(0f,MotionNoise.value(Float.NaN,3),0f)
 }
 @Test fun flashProfilesHaveIrregularIntervalsAndAtMostThreeReturnStrokes() {
  val intervals=mutableSetOf<Int>()
  for(seed in 1..100){val p=LightningProfile.fromSeed(seed)
   assertTrue(p.pulses.size in 2..3)
   p.pulses.zipWithNext().forEach{(a,b)->assertTrue(b.start>a.start+a.duration);intervals+=(1000*(b.start-a.start)).toInt()}
   for(i in 0..200){val v=p.intensity(i*.01f);assertTrue(v.isFinite()&&v in -.19f..1f)}
   assertEquals(0f,p.intensity(p.duration+.01f),0f)
  }
  assertTrue(intervals.size>15)
 }
 @Test fun lightningBranchesAreDeterministicAndEndOnTheActualImpact() {
  val a=LightningGeometry.create(99,400f,800f,280f,520f)
  assertEquals(a,LightningGeometry.create(99,400f,800f,280f,520f))
  assertNotEquals(a,LightningGeometry.create(100,400f,800f,280f,520f))
  assertEquals(280f,a.trunk.last().x,0f);assertEquals(520f,a.trunk.last().y,0f)
  assertTrue(a.branches.size in 2..4)
  assertTrue((a.trunk+a.branches.flatten()).all{it.x.isFinite()&&it.y.isFinite()})
 }
 @Test fun attachedLeavesFlutterIndependentlyAroundTheirContact() {
  val w=WeatherSurfacePhysics();val s=RainCollisionWorld.Surface(1,50f,200f,450f,400f,24f)
  for(key in 1..3)w.bodies+=WeatherSurfacePhysics.Body(WeatherSurfacePhysics.Kind.LEAF,150f,200f,0f,0f,4f,0f,0f,owner=1,u=.3f,v=0f,hold=300f,noiseSeed=key)
  val tracks=List(3){mutableListOf<Float>()}
  repeat(240){w.step(1f/60,WeatherPhysicsStyle(),55f,true,600f,900f,listOf(s));w.bodies.take(3).forEachIndexed{i,p->tracks[i]+=p.angle}}
  assertTrue(tracks[0].zip(tracks[1]).any{(a,b)->abs(a-b)>1f})
  assertTrue(w.bodies.take(3).all{it.owner==1&&it.angle.isFinite()&&it.flex.isFinite()})
 }
 @Test fun leafPhysicsAgreesAtSixtyAndOneHundredSixtySixHz() {
  fun run(hz:Int):WeatherSurfacePhysics.Body {
   val w=WeatherSurfacePhysics();val p=WeatherSurfacePhysics.Body(WeatherSurfacePhysics.Kind.LEAF,100f,100f,40f,25f,4f,0f,30f,noiseSeed=17)
   w.bodies+=p;repeat(hz*3){w.step(1f/hz,WeatherPhysicsStyle(),20f,false,1400f,900f,emptyList())};return p
  }
  val a=run(60);val b=run(166)
  assertEquals(a.x,b.x,.08f);assertEquals(a.y,b.y,.08f);assertEquals(a.angle,b.angle,.15f)
 }
 @Test fun navigationForegroundsRemainReadableInEveryThemeAndAppearance() {
  for(theme in AppThemeColor.entries)for(dark in listOf(false,true)){
   val p=BrandPalette.scheme(theme,dark);val n=FluidGlassStyle.navigation(theme,dark)
   val selected=n.selection.compositeOver(n.underlay.compositeOver(p.background))
   assertTrue(colorContrast(n.selectedInk,selected)>=4.5f)
   assertTrue(colorContrast(n.ink,n.underlay.compositeOver(p.background))>=4.5f)
  }
 }
}
