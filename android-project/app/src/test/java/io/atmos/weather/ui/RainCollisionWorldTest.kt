package io.atmos.weather.ui
import org.junit.Assert.*
import org.junit.Test
class RainCollisionWorldTest {
    private fun world()=RainCollisionWorld(77).apply {width=400f;height=700f;surface(7,40f,200f,360f,360f,24f)}
    @Test fun aRealRoofHitCreatesAnUpwardSplashAtTheHitPosition() {
        val w=world();w.air+=RainCollisionWorld.Air(120f,195f,0f,650f,1f)
        w.step(1f/60,0f,0f)
        assertEquals(1,w.impacts);assertTrue(w.air.isEmpty());assertTrue(w.sparks.isNotEmpty())
        assertTrue(w.sparks.all {it.owner==7 && it.vy<0 && it.y<200f && kotlin.math.abs(it.x-120)<4})
        assertTrue(w.surfaces[7]!!.wet>0f)
        val mass=w.surfaces[7]!!.beads.sumOf {it.mass.toDouble()}+w.sparks.sumOf {it.mass.toDouble()}
        assertEquals(1.0,mass,.005)
    }
    @Test fun noHitMeansNoInventedSplashOrWaterBeads() {
        val w=world();w.air+=RainCollisionWorld.Air(10f,195f,0f,650f,1f);w.step(.05f,0f,0f)
        assertEquals(0,w.impacts);assertTrue(w.sparks.isEmpty());assertTrue(w.surfaces[7]!!.beads.isEmpty())
    }
    @Test fun collisionChoosesTheFirstRoofAlongTheSweep() {
        val w=world();w.surface(8,40f,205f,360f,500f,24f)
        w.air+=RainCollisionWorld.Air(120f,195f,0f,1100f,1f);w.step(1f/60,0f,0f)
        assertEquals(1,w.impacts);assertTrue(w.sparks.all {it.owner==7})
    }
    @Test fun waterRunsAroundTheRimThenDetachesAsARealFallingDrip() {
        val w=world();w.air+=RainCollisionWorld.Air(100f,195f,0f,650f,2.5f)
        repeat(720) {w.step(1f/120,0f,0f)}
        assertTrue(w.emittedDrips>0)
    }
    @Test fun movingACardMovesItsSurfaceWaterWithIt() {
        val w=world();w.air+=RainCollisionWorld.Air(100f,195f,0f,650f,2f);w.step(1f/60,0f,0f)
        val surface=w.surfaces[7]!!;val bead=surface.beads.first();val before=w.beadPoint(surface,bead)
        w.surface(7,57f,160f,377f,320f,24f);val after=w.beadPoint(surface,bead)
        assertEquals(before.x+17,after.x,.001f);assertEquals(before.y-40,after.y,.001f)
    }
    @Test fun sixtyAndOneTwentyHzProduceTheSamePhysics() {
        fun run(fps:Int):RainCollisionWorld=world().also {w->repeat(fps*5){w.step(1f/fps,.6f,20f)}}
        val a=run(60);val b=run(120)
        assertEquals(a.impacts,b.impacts);assertEquals(a.emittedDrips,b.emittedDrips)
        assertEquals(a.air.size,b.air.size)
        a.air.zip(b.air).forEach {(x,y)->assertEquals(x.x,y.x,.05f);assertEquals(x.y,y.y,.05f)}
    }
    @Test fun heavyRainRemainsBoundedAndFinite() {
        val w=world();repeat(120*90){w.step(1f/120,1f,140f)}
        assertTrue(w.air.size<=140);assertTrue(w.sparks.size<=128)
        assertTrue(w.surfaces.values.all {it.beads.size<=24})
        assertTrue(w.air.all {it.x.isFinite() && it.y.isFinite() && it.vy.isFinite()})
    }
    @Test fun scrollingCardsCannotLeaveAirborneRainInsideTheirFaces() {
        val w=world();w.air+=RainCollisionWorld.Air(100f,250f,0f,100f,1f);w.step(1f/120,0f,0f)
        assertTrue(w.air.isEmpty());assertEquals(0,w.impacts)
    }
}
