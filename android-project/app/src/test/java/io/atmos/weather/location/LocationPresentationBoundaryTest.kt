package io.atmos.weather.location

import android.app.Application
import android.content.Context
import io.atmos.weather.alarm.WeatherAlarmSpeechCache
import io.atmos.weather.data.remote.WeatherProvider
import io.atmos.weather.data.repository.DefaultWeatherDataRepository
import io.atmos.weather.data.repository.WeatherDiskCache
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.widgets.WidgetSnapshotStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class LocationPresentationBoundaryTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val clock=Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"),ZoneOffset.UTC)
    private val city=WeatherCity("legacy","中華台北 · 台北市",GeoPoint(25.03,121.56))
    private fun sample()=WeatherSnapshot(city,clock.instant(),clock.instant(),"test",24.0,24.0,70,5.0,1012.0,
        WeatherCondition.CLEAR,true,null,null,emptyList())
    @Test fun providerAndMemoryCacheCannotReintroduceAnExternalAlias()=runBlocking {
        var calls=0
        val provider=object:WeatherProvider {
            override val id="test"
            override suspend fun get(city:WeatherCity):WeatherSnapshot {
                calls++;assertEquals("台灣 · 台北市",city.name)
                return sample() // Simulates a provider still returning its original label.
            }
        }
        val repository=DefaultWeatherDataRepository(listOf(provider),clock)
        repeat(2) {
            val result=repository.load(city) as WeatherLoadResult.Success
            assertEquals("台灣 · 台北市",result.value.city.name)
            assertEquals(city.coordinate,result.value.city.coordinate)
        }
        assertEquals(1,calls)
    }
    @Test fun legacyWidgetTargetsAndNewSnapshotsUseTheSameCleanLabel() {
        val p=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE)
        p.edit().putString("widget_target_id",city.id).putString("widget_target_name","中國台灣 · 台北市")
            .putLong("widget_target_lat",city.coordinate.latitude.toBits()).putLong("widget_target_lon",city.coordinate.longitude.toBits())
            .putLong("widget_target_revision",7).commit()
        assertEquals("台灣 · 台北市",WidgetSnapshotStore.target(context).city.name)
        assertTrue(WidgetSnapshotStore.publish(context,sample(),7))
        assertEquals("台灣 · 台北市",p.getString("widget_city",null))
    }
    @Test fun savedCitiesAndOfflineForecastsMigrateAtTheirReadBoundary() {
        val added=SavedCityStore.add(context,city).single()
        assertEquals("台灣 · 台北市",added.name);assertEquals(city.id,added.id)
        val cache=WeatherDiskCache(context);cache.save(sample())
        val restored=cache.load(city)!!
        assertEquals("台灣 · 台北市",restored.city.name)
        assertEquals(sample().temperatureC,restored.temperatureC,0.0)
    }
    @Test fun previouslyCachedAlarmSpeechCannotReadAnUnwantedAliasAloud() {
        WeatherAlarmSpeechCache.save(context,sample())
        assertEquals("台灣 · 台北市",WeatherAlarmSpeechCache.load(context)!!.city)
        val p=context.getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE)
        val old=JSONObject(p.getString("weather_voice_facts_r10",null)!!).put("city","Chinese Taipei · 台北市")
        p.edit().putString("weather_voice_facts_r10",old.toString()).commit()
        assertEquals("台灣 · 台北市",WeatherAlarmSpeechCache.load(context)!!.city)
    }
}
