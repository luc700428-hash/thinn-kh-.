package io.atmos.weather.ui

import org.junit.Assert.*
import org.junit.Test

class WeatherSurfacePhysicsTest {
    @Test fun longRainRemainsBoundedAndFinite() {
        val water=WeatherSurfacePhysics(123)
        repeat(18_000) {frame ->
            water.advance(frame/60f,360f,220f,1f,true)
            assertTrue(water.drops.size<=32)
            assertTrue(water.splashes.size<=21)
            water.drops.forEach {assertTrue(it.x.isFinite() && it.y.isFinite() && it.speed.isFinite() && it.radius.isFinite())}
        }
    }
    @Test fun nearbyBeadsMergeConservingArea() {
        val water=WeatherSurfacePhysics()
        water.advance(0f,360f,220f,0f,true);water.drops.clear()
        water.drops+=WeatherSurfacePhysics.Drop(50f,50f,2f,0f)
        water.drops+=WeatherSurfacePhysics.Drop(50f,50f,3f,0f)
        water.advance(.01f,360f,220f,0f,true)
        assertEquals(1,water.drops.size)
        assertEquals(13f,water.drops.single().radius.let {it*it},.001f)
    }
    @Test fun clearedWeatherDrainsAndEvaporatesWater() {
        val water=WeatherSurfacePhysics()
        repeat(600) {water.advance(it/60f,360f,220f,1f,true)}
        repeat(18_000) {water.advance(10f+it/60f,360f,220f,0f,false)}
        assertTrue(water.drops.isEmpty());assertTrue(water.splashes.isEmpty())
    }
    @Test fun returningFromBackgroundDoesNotTeleportByElapsedHours() {
        val water=WeatherSurfacePhysics()
        water.advance(0f,360f,220f,0f,true);water.drops.clear()
        water.drops+=WeatherSurfacePhysics.Drop(50f,50f,5f,100f)
        water.advance(3600f,360f,220f,0f,false)
        assertTrue(water.drops.single().y<60f)
    }
}
