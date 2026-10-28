package io.atmos.weather.ui

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class BubbleSliderReboundTest {
    @Test fun actualReleaseSpringReproducesTheOldNegativeHueAndKeepsNewColorsValid() {
        val release=TargetBasedAnimation(spring<Float>(.62f,240f),Float.VectorConverter,1f,0f)
        var undershoots=0
        for(ms in 0L..1600L step 4L) {
            val lift=release.getValueFromNanos(ms*1_000_000)
            if(lift<0f) {
                undershoots++
                assertTrue(runCatching{Color.hsv((lift*40f)%360f,.38f,.98f)}.isFailure)
            }
            val phase=BubbleSliderGeometry.rimPhase(0f,lift)
            for(i in 0..6)assertTrue(Color.hsv((phase+i*60f)%360f,.38f,.98f).alpha>0f)
        }
        assertTrue("Release must include real spring undershoot",undershoots>0)
    }
    @Test fun lensRimRemainsFiniteAcrossOvershootAndCorruptValues() {
        val values=(-100..200).map{it/100f}+listOf(Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY)
        for(head in values)for(lift in values) {
            val phase=BubbleSliderGeometry.rimPhase(head,lift)
            assertTrue(phase.isFinite() && phase in 0f..200f)
            assertTrue(.10f+BubbleSliderGeometry.unit(lift)*.045f in 0.099999f..0.145001f)
            for(i in 0..6)Color.hsv((phase+i*60f)%360f,.38f,.98f)
        }
    }
}
