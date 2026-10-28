package io.atmos.weather.next

import android.app.Application
import io.atmos.weather.domain.model.WeatherCondition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class AmbiencePlaybackTest {
    @Test fun dryWeatherHasDecodableNonSilentDayNightLoops() {
        val context=RuntimeEnvironment.getApplication()
        val paths=mutableSetOf<String>()
        for(condition in WeatherCondition.entries.filter{it!=WeatherCondition.UNKNOWN})for(night in listOf(false,true)) {
            val scene=WeatherScenes.classify(condition,null,null)
            paths+=AmbientSoundPolicy.path(condition,scene,night)!!
        }
        for(path in paths) {
            val decoded=LoopWav.read(context,path)?:error("Cannot decode $path")
            assertTrue(decoded.frames>=decoded.sampleRate*8)
            var sum=0.0;var samples=0;var peak=0
            // Check the whole loop at bounded sampling cost, not just a fade-in frame.
            for(i in decoded.pcm.indices step 128) {
                if(i+1>=decoded.pcm.size)break
                val sample=((decoded.pcm[i].toInt() and 255) or (decoded.pcm[i+1].toInt() shl 8)).toShort().toInt()
                sum+=sample.toDouble()*sample;samples++;peak=maxOf(peak,kotlin.math.abs(sample))
            }
            assertTrue("Silent ambience asset $path",sqrt(sum/samples)/32768.0>.001)
            assertTrue("Clipped PCM asset $path",peak<32767)
        }
        assertNotEquals(AmbientSoundPolicy.path(WeatherCondition.CLEAR,WeatherScene.SUNNY,false),
            AmbientSoundPolicy.path(WeatherCondition.CLEAR,WeatherScene.SUNNY,true))
        assertNotEquals(AmbientSoundPolicy.path(WeatherCondition.CLOUDY,null,false),
            AmbientSoundPolicy.path(WeatherCondition.CLOUDY,null,true))
    }
    @Test fun statusDistinguishesUnknownWeatherMutedOffAndBackground()=runBlocking {
        val controller=NextAmbienceController(RuntimeEnvironment.getApplication())
        val path=AmbientSoundPolicy.path(WeatherCondition.CLEAR,WeatherScene.SUNNY,false)!!
        try {
            controller.foreground(true)
            controller.configure(null,true,.5f)
            assertEquals("waiting",controller.state.value.code)
            controller.configure(path,true,0f)
            assertEquals("muted",controller.state.value.code)
            controller.configure(path,false,.7f)
            assertEquals("off",controller.state.value.code)
            controller.foreground(false)
            controller.configure(path,true,0f)
            assertEquals("paused",controller.state.value.code)
        } finally {controller.close()}
    }
}
