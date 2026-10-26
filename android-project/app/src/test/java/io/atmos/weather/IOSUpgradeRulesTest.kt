package io.atmos.weather
import io.atmos.weather.ui.DrumWheelRules
import io.atmos.weather.next.*
import io.atmos.weather.notifications.IslandInformationPolicy as Island
import io.atmos.weather.widgets.WidgetPresentationRules as Widget
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class IOSUpgradeRulesTest {
 @Test fun boundedMeridiemNeverWraps(){assertEquals(0,DrumWheelRules.move(0,0f,100f,2,false).value);assertEquals(1,DrumWheelRules.move(1,0f,-100f,2,false).value);assertEquals(1,DrumWheelRules.move(0,0f,-1f,2,false).value);assertEquals(0,DrumWheelRules.move(1,0f,1f,2,false).value)}
 @Test fun selectedDetentChangesBeforeRelease(){val v=DrumWheelRules.move(30,0f,-.51f,60,true);assertEquals(31,v.value);assertEquals(.49f,v.offset,.001f)}
 @Test fun minuteAndHourCylindersWrap(){assertEquals(0,DrumWheelRules.move(59,0f,-1f,60,true).value);assertEquals(59,DrumWheelRules.move(0,0f,1f,60,true).value);assertEquals(0,DrumWheelRules.move(11,0f,-1f,12,true).value)}
 @Test fun boundaryResistanceIsBounded(){for(i in 1..1000){val a=DrumWheelRules.move(0,0f,i.toFloat(),201,false);assertEquals(0,a.value);assertTrue(a.offset<.83f)}}
 @Test fun invalidNumericMotionDoesNotCorruptSelection(){assertEquals(5,DrumWheelRules.move(5,0f,Float.NaN,60,true).value)}
 @Test fun monthChangesAndLeapYearsStayValid(){assertEquals(LocalDate.of(2026,2,28),DrumWheelRules.date(2026,2,31));assertEquals(LocalDate.of(2028,2,29),DrumWheelRules.date(2028,2,31));assertEquals(LocalDate.of(1900,2,28),DrumWheelRules.date(1900,2,29))}
 @Test fun noonAndMidnightAreCorrect(){assertEquals(0,DrumWheelRules.hour24(12,false));assertEquals(12,DrumWheelRules.hour24(12,true));assertEquals(13,DrumWheelRules.hour24(1,true))}
 @Test fun everyThemeResolvesAllWeatherArrangements(){assertEquals(6,WeatherWakePolicy.themes.size);for(t in WeatherWakePolicy.themes){for(s in WeatherScene.entries)assertEquals("next_weather/music/$t/${s.name.lowercase()}.wav",WeatherScenes.musicAsset(t,s));assertEquals("next_weather/music/$t/neutral.wav",WeatherScenes.musicAsset(t,null))}}
 @Test fun denseFogSurvivesCachedClassification(){assertEquals(WeatherScene.DENSE_FOG,WeatherScenes.fromCachedName("FOG",45,100.0));assertEquals(WeatherScene.FOG,WeatherScenes.fromCachedName("FOG",45,null))}
 @Test fun personalAudioNeverGetsOverwritten(){assertNull(WeatherWakePolicy.theme("content://my/song"));assertNull(WeatherWakePolicy.theme("thinn://alarm/../../other"));assertEquals("dawn",WeatherWakePolicy.theme(null))}
 @Test fun wakeEnvelopeRisesAndStaysBounded(){var before=0f;for(t in 0L..18_000L step 100){val gain=WeatherWakePolicy.gain(t);assertTrue(gain>=before);before=gain};for(t in 18_000L..600_000L step 113)assertTrue(WeatherWakePolicy.gain(t) in .92f..1f);assertEquals(.18f,WeatherWakePolicy.gain(-100),.0001f)}
 @Test fun stormsDoNotMaskTheMelody(){assertTrue(WeatherWakePolicy.ambienceGain(WeatherScene.SEVERE_THUNDERSTORM)<WeatherWakePolicy.ambienceGain(WeatherScene.SUNNY));assertEquals(0f,WeatherWakePolicy.ambienceGain(null),0f)}
 @Test fun islandShorteningPreservesUnicode(){assertEquals("🌧",Island.short("🌧臺北",1));assertEquals("",Island.short("臺北",-1));assertEquals("臺北",Island.short("臺北",100))}
 @Test fun progressRequiresRealBoundedNumbers(){assertNull(Island.progress(0,0,false));assertNull(Island.progress(50,100,true));assertNull(Island.progress(101,100,false));assertEquals(50,Island.progress(5,10,false))}
 @Test fun oldOrImpossibleWeatherIsExcluded(){val now=2_000_000L;assertTrue(Island.freshWeather(now-1000,now,25));assertFalse(Island.freshWeather(now-1_800_001,now,25));assertFalse(Island.freshWeather(now+1,now,25));assertFalse(Island.freshWeather(now,now,99))}
 @Test fun islandDeadlineRequiresRealFutureTarget(){assertEquals(10_001L,Island.deadline(10_001,10_000));assertNull(Island.deadline(9999,10000));assertNull(Island.deadline(10000+86_400_001,10000))}
 @Test fun widgetAgeNeverClaimsMissingDataIsFresh(){assertEquals(Widget.Freshness.MISSING,Widget.freshness(0,1_000_000));assertEquals(Widget.Freshness.EXPIRED,Widget.freshness(1,90_000_000));assertEquals(Widget.Freshness.CACHED,Widget.freshness(1,6_000_000))}
 @Test fun widgetClockUsesMonotonicBase(){assertEquals(16_000L,Widget.countdownBase(12_000,10_000,14_000));assertNull(Widget.countdownBase(9000,10000,14000));assertNull(Widget.countdownBase(10000+86_400_001,10000,14000))}
 @Test fun widgetBitmapBudgetIsBounded(){assertEquals(640,Widget.pixels(1000f,4f));assertTrue(Widget.pixels(150f,3f)<=450)}
 @Test fun widgetNeverTurnsMissingOrExpiredTemperatureIntoZero(){
  assertEquals("—°",Widget.temperature(Int.MIN_VALUE,true))
  assertEquals("—°",Widget.temperature(27,false))
  assertEquals("0°",Widget.temperature(0,true))
  assertEquals("-8°",Widget.temperature(-8,true))
  assertEquals("—°",Widget.temperature(200,true))
 }
}
