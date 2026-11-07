package io.atmos.weather.ui
import io.atmos.weather.next.WeatherScene
import org.junit.Assert.*
import org.junit.Test

class WeatherSurfacePhysicsTest {
 private fun surface()=RainCollisionWorld.Surface(1,100f,200f,300f,400f,24f)
 private fun run(w:WeatherSurfacePhysics,seconds:Float,style:WeatherPhysicsStyle=WeatherPhysicsStyle(),s:RainCollisionWorld.Surface=surface(),wind:Float=0f,strong:Boolean=false){
  repeat((seconds*120).toInt()){w.step(1f/120,style,wind,strong,400f,700f,listOf(s))}
 }
 @Test fun rainAndSnowLevelsHaveDistinctPhysicalProfiles() {
  val rain=listOf(WeatherScene.DRIZZLE,WeatherScene.LIGHT_RAIN,WeatherScene.MODERATE_RAIN,WeatherScene.HEAVY_RAIN).map{WeatherPhysicsStyle.forScene(it)}
  rain.zipWithNext().forEach{(a,b)->assertTrue(a.rain<b.rain&&a.speed<b.speed&&a.radius<b.radius&&a.trail<b.trail)}
  val snow=listOf(WeatherScene.LIGHT_SNOW,WeatherScene.MODERATE_SNOW,WeatherScene.HEAVY_SNOW).map{WeatherPhysicsStyle.forScene(it)}
  assertTrue(snow[0].snow<snow[1].snow&&snow[1].snow<snow[2].snow)
  assertTrue(snow.all{it.rain==0f});assertFalse(WeatherPhysicsStyle.forScene(WeatherScene.SUNNY).active)
 }
 @Test fun roundedCornersAndFastSideHitsUseContinuousCollision() {
  val s=surface()
  val corner=WeatherSurfacePhysics.sweep(s,104f,150f,104f,260f)!!
  assertTrue(corner.y>200f&&corner.y<224f);assertTrue(corner.nx<0&&corner.ny<0)
  val side=WeatherSurfacePhysics.sweep(s,10f,300f,380f,300f)!!
  assertEquals(100f,side.x,.001f);assertEquals(-1f,side.nx,0f)
  assertNull(WeatherSurfacePhysics.sweep(s,0f,190f,90f,190f))
  s.clipBottom=250f
  assertNull("Hidden card sides below Dock cannot receive impacts",WeatherSurfacePhysics.sweep(s,10f,300f,380f,300f))
  s.clipBottom=199f
  assertNull(WeatherSurfacePhysics.sweep(s,150f,150f,150f,240f))
 }
 @Test fun snowAccumulatesOnTheActualRoofAndShedsWhenThePileIsHeavy() {
  val w=WeatherSurfacePhysics();val s=surface();w.caps[1]=FloatArray(16).also{it[8]=9.5f}
  w.bodies+=WeatherSurfacePhysics.Body(WeatherSurfacePhysics.Kind.SNOW,200f,199f,0f,150f,2f,0f,0f,1f)
  run(w,.1f,s=s)
  assertTrue(w.snowDeposits>0);assertTrue(w.snowEjections>0)
  assertTrue(w.caps[1]!![8]<9f);assertTrue(w.bodies.any{it.mass>1f})
 }
 @Test fun scrollingEitherWayEjectsAccumulationWithTheCardsMomentum() {
  for(shift in listOf(-35f,35f)) {
   val w=WeatherSurfacePhysics();val s=surface();run(w,.02f,s=s)
   w.caps[1]=FloatArray(16){2f};s.top+=shift;s.bottom+=shift
   run(w,.02f,s=s)
   assertTrue(w.snowEjections>0)
   assertTrue(w.bodies.any{if(shift<0)it.vy<0 else it.vy>0})
   assertTrue(w.caps[1]!!.sum()<32f)
  }
 }
 @Test fun theSameSnowSimulationAgreesAtSixtyAndOneTwentyHz() {
  fun simulate(hz:Int):WeatherSurfacePhysics {
   val w=WeatherSurfacePhysics(77);repeat(hz*4){w.step(1f/hz,WeatherPhysicsStyle(snow=.5f),20f,false,400f,700f,emptyList())};return w
  }
  val a=simulate(60);val b=simulate(120)
  assertEquals(a.bodies.size,b.bodies.size)
  a.bodies.zip(b.bodies).forEach{(x,y)->assertEquals(x.x,y.x,.1f);assertEquals(x.y,y.y,.1f)}
 }
 @Test fun dryStrongWindEmitsLeavesWithoutInventingRainOrSnow() {
  val w=WeatherSurfacePhysics(1);run(w,8f,WeatherPhysicsStyle(),wind=55f,strong=true)
  assertTrue(w.bodies.any{it.kind==WeatherSurfacePhysics.Kind.LEAF})
  assertTrue(w.bodies.none{it.kind==WeatherSurfacePhysics.Kind.SNOW})
  assertTrue(WeatherPhysicsStyle.windPixels(60f,true,1f)>100f)
 }
 @Test fun anAttachedLeafLastsMinutesThenBlowsAwayWithoutResticking() {
  val w=WeatherSurfacePhysics();val s=surface()
  val leaf=WeatherSurfacePhysics.Body(WeatherSurfacePhysics.Kind.LEAF,200f,200f,0f,0f,4f,0f,10f,owner=1,u=.5f,v=0f,hold=120f)
  w.bodies+=leaf;run(w,119f,s=s,wind=55f)
  assertEquals(1,leaf.owner);run(w,2f,s=s,wind=55f)
  assertNull(leaf.owner);assertEquals(1,w.leafReleases);assertTrue(leaf.vx>100f)
 }
 @Test fun strikesSelectExistingCardCornersAndFadeWithoutPersistentDamage() {
  val w=WeatherSurfacePhysics(2);val s=surface();val d=w.strike(listOf(s),400f,700f)!!
  val p=w.strikePoint(d,s);assertTrue(p.x<125f||p.x>275f);assertTrue(p.y in 200f..224f)
  run(w,11f,s=s);assertTrue(w.damage.isEmpty());assertNull(w.bolt)
  s.active=false;assertNull(w.strike(listOf(s),400f,700f))
  s.active=true;s.clipBottom=199f;assertNull(w.strike(listOf(s),400f,700f))
 }
 @Test fun aFastScrollAlsoDetachesWaterIntoMovingDroplets() {
  val w=RainCollisionWorld();w.width=400f;w.height=700f;w.surface(1,100f,200f,300f,400f,24f)
  val s=w.surfaces[1]!!;s.beads+=RainCollisionWorld.Bead(true,-25f,2f,0f)
  w.surface(1,100f,170f,300f,370f,24f);w.step(1f/60,0f,0f)
  assertTrue(w.emittedDrips>0);assertTrue(w.air.any{it.vy<0})
 }
}
