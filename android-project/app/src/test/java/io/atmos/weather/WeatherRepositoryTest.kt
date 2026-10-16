package io.atmos.weather

import io.atmos.weather.data.remote.WeatherProvider
import io.atmos.weather.data.repository.DefaultWeatherDataRepository
import io.atmos.weather.domain.model.SampleCities
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.repository.WeatherLoadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant

class WeatherRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-13T06:00:00Z"), java.time.ZoneOffset.UTC)
    private val city = SampleCities.all.first()
    private val sample = WeatherSnapshot(city, clock.instant(), clock.instant(), "fake", 26.0, 27.0,
        60, 12.0, 1012.0, WeatherCondition.CLEAR, true, null, null, emptyList())

    @Test fun providerFallbackWorks() = runBlocking {
        val failed = object: WeatherProvider {
            override val id = "offline"
            override suspend fun get(city: io.atmos.weather.domain.model.WeatherCity): WeatherSnapshot = error("offline")
        }
        val good = object: WeatherProvider {
            override val id = "good"
            override suspend fun get(city: io.atmos.weather.domain.model.WeatherCity) = sample
        }
        val result = DefaultWeatherDataRepository(listOf(failed, good), clock).load(city)
        assertTrue(result is WeatherLoadResult.Success)
        assertEquals("fake", (result as WeatherLoadResult.Success).value.sourceId)
    }

    @Test fun freshCacheAvoidsDuplicateRequests() = runBlocking {
        var calls = 0
        val provider = object: WeatherProvider {
            override val id = "good"
            override suspend fun get(city: io.atmos.weather.domain.model.WeatherCity): WeatherSnapshot {
                calls++; return sample
            }
        }
        val repository = DefaultWeatherDataRepository(listOf(provider), clock)
        repository.load(city)
        val result = repository.load(city)
        assertEquals(1, calls)
        assertTrue((result as WeatherLoadResult.Success).fromCache)
    }
}
