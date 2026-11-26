package io.atmos.weather.life

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.atmos.weather.AppContainer
import io.atmos.weather.domain.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 行事曆疊加天氣. Reads (never writes) the phone's calendars for the next 7 days, keeps events
 * that have a place, finds the place, and puts the forecast for that hour beside the event.
 */
object CalendarWeather {
    data class Event(val id: Long, val title: String, val location: String, val begin: Instant, val end: Instant, val allDay: Boolean)
    data class Place(val name: String, val lat: Double, val lon: Double)
    data class Forecast(val temperature: Int?, val rain: Int?, val code: Int?, val note: String)
    data class Row(val event: Event, val place: Place?, val forecast: Forecast?)

    fun allowed(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun events(ctx: Context, from: Instant = Instant.now(), days: Long = 7): List<Event> {
        if (!allowed(ctx)) return emptyList()
        val to = from.plusSeconds(days * 86_400)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from.toEpochMilli()); android.content.ContentUris.appendId(it, to.toEpochMilli())
        }.build()
        val cols = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                val out = mutableListOf<Event>()
                while (c.moveToNext() && out.size < 60) {
                    val loc = c.getString(2)?.trim().orEmpty()
                    if (loc.isEmpty()) continue
                    out += Event(c.getLong(0), c.getString(1)?.trim().orEmpty().ifBlank { "（未命名行程）" }, loc,
                        Instant.ofEpochMilli(c.getLong(3)), Instant.ofEpochMilli(c.getLong(4)), c.getInt(5) == 1)
                }
                out
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** "臺南市美術館二館 (台南市中西區南門路37號)" → something a geocoder understands. */
    fun cleanPlace(text: String): String = text.substringBefore('\n').replace(Regex("[（(].*?[）)]"), " ").trim().ifBlank { text.trim() }.take(80)

    private val places = HashMap<String, Place?>()
    @Suppress("DEPRECATION")
    suspend fun locate(ctx: Context, text: String): Place? = withContext(Dispatchers.IO) {
        val key = cleanPlace(text)
        synchronized(places) { if (places.containsKey(key)) return@withContext places[key] }
        val viaGeocoder = runCatching {
            if (!Geocoder.isPresent()) null
            else Geocoder(ctx, Locale.TAIWAN).getFromLocationName(key, 1)?.firstOrNull()?.let { Place(key, it.latitude, it.longitude) }
        }.getOrNull()
        val found = viaGeocoder ?: runCatching {
            AppContainer.geoApi.search(key, 1, "zh").results?.firstOrNull()?.let { Place(it.name ?: key, it.latitude, it.longitude) }
        }.getOrNull()
        synchronized(places) { places[key] = found }
        found
    }

    fun forecast(w: WeatherSnapshot, e: Event): Forecast? {
        if (e.allDay) {
            val zone = java.time.ZoneId.of(w.timeZoneId ?: "Asia/Taipei")
            val d = e.begin.atZone(zone).toLocalDate()
            val day = w.daily.firstOrNull { it.date == d }
            val hours = w.hourly.filter { it.at.atZone(zone).toLocalDate() == d }
            val high = day?.highC ?: hours.mapNotNull { it.temperatureC }.maxOrNull()
            val pop = day?.precipitationProbabilityPct ?: hours.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
            if (high == null && pop == null) return null
            return Forecast(high?.roundToInt(), pop, day?.weatherCode, "全天")
        }
        val h = w.hourly.nearest(e.begin)
        if (h != null) return Forecast(h.temperatureC?.roundToInt(), h.precipitationProbabilityPct, h.weatherCode, "行程開始時")
        val zone = java.time.ZoneId.of(w.timeZoneId ?: "Asia/Taipei")
        val day = w.daily.firstOrNull { it.date == e.begin.atZone(zone).toLocalDate() } ?: return null
        return Forecast(day.highC?.roundToInt(), day.precipitationProbabilityPct, day.weatherCode, "當天")
    }

    fun advice(f: Forecast): String = when {
        (f.rain ?: 0) >= 60 -> "很可能下雨，記得帶傘"
        (f.rain ?: 0) >= 30 -> "可能下雨，帶把折傘"
        (f.temperature ?: 20) >= 33 -> "很熱，注意補水防曬"
        (f.temperature ?: 20) <= 14 -> "偏冷，多帶件外套"
        else -> "天氣不錯"
    }
}
