package io.atmos.weather.alarm
import io.atmos.weather.R
import io.atmos.weather.next.*
import org.junit.Assert.*
import org.junit.Test
class PopWeatherRoutingTest {
    @Test fun catalogueAndPcmResolverUseTheSameTwentyIdentities() {
        assertEquals(20,R10AlarmTones.choices.size)
        assertEquals(WeatherWakePolicy.themes,R10AlarmTones.choices.map{it.id}.toSet())
        for(id in WeatherWakePolicy.popThemes)for(scene in WeatherScene.entries) {
            assertEquals(id,WeatherWakePolicy.theme("thinn://alarm/$id"))
            assertEquals("next_weather/music/$id/${WeatherWakePolicy.paletteArrangement(scene)}.wav",WeatherScenes.musicAsset(id,scene))
        }
    }
    @Test fun invalidUrisCannotTurnIntoAnAssetPath() {
        for(uri in listOf("thinn://alarm/../citypop","thinn://alarm/citypop/clear","content://local/music"))assertNull(WeatherWakePolicy.theme(uri))
        assertEquals("dawn",WeatherWakePolicy.theme(null))
    }
    @Test fun everyPopThemeHasItsOwnNeutralRawFallback() {
        val neutral=WeatherWakePolicy.popThemes.map{R10AlarmTones.resource("thinn://alarm/$it",null,1000000)}
        assertEquals(6,neutral.toSet().size);assertFalse(neutral.contains(null))
        assertEquals(R.raw.thinn_alarm_citypop,neutral[WeatherWakePolicy.popThemes.indexOf("citypop")])
    }
}
