package io.atmos.weather.data.repository

import io.atmos.weather.data.remote.WeatherProvider
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.repository.WeatherDataRepository
import io.atmos.weather.domain.repository.WeatherLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration

class DefaultWeatherDataRepository(
    private val providers: List<WeatherProvider>,
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofMinutes(15),
    private val diskCache: WeatherDiskCache? = null
) : WeatherDataRepository {
    init { require(providers.isNotEmpty()) }
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, WeatherSnapshot>()
    override suspend fun load(city: WeatherCity, forceRefresh: Boolean): WeatherLoadResult {
        val cached = mutex.withLock { memory["${city.id}@${city.coordinate.latitude},${city.coordinate.longitude}"] } ?: diskCache?.load(city)
        if (!forceRefresh && cached != null && io.atmos.weather.domain.model.WeatherDataQuality.cacheFresh(cached,clock.instant(),ttl)) {
            return WeatherLoadResult.Success(cached, fromCache = true, stale = false)
        }
        var lastError: Throwable? = null
        for (provider in providers) {
            try {
                val result = provider.get(city)
                check(io.atmos.weather.domain.model.WeatherDataQuality.current(result,clock.instant())) {"氣象資料時間或數值異常，暫不視為最新觀測"}
                mutex.withLock { memory["${city.id}@${city.coordinate.latitude},${city.coordinate.longitude}"] = result }
                diskCache?.save(result)
                return WeatherLoadResult.Success(result, fromCache = false, stale = false)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { lastError = error }
        }
        return if (cached != null) WeatherLoadResult.Success(cached, fromCache = true, stale = true)
        else WeatherLoadResult.Failure(lastError?.localizedMessage ?: "目前無法取得氣象資料")
    }
}
