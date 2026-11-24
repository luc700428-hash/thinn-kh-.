package io.atmos.weather.report

import android.content.Context
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * 回報目前天氣. The forecast is for an area and an hour; what is falling on the person right now is
 * the better truth. A report replaces the shown sky for an hour within a few kilometres: the home
 * page, the weather animation and the widgets all follow it, labelled as the person's report.
 * The app then reloads the latest observation, and a new report or the hour running out returns
 * to the official data.
 */
object WeatherReports {
    enum class Kind(val label: String, val condition: WeatherCondition, val code: Int, val wet: Boolean) {
        SUNNY("晴天", WeatherCondition.CLEAR, 0, false),
        CLOUDY("多雲", WeatherCondition.PARTLY_CLOUDY, 2, false),
        OVERCAST("陰天", WeatherCondition.CLOUDY, 3, false),
        FOG("起霧", WeatherCondition.FOG, 45, false),
        DRIZZLE("毛毛雨", WeatherCondition.RAIN, 51, true),
        RAIN("下雨", WeatherCondition.RAIN, 63, true),
        HEAVY_RAIN("大雨", WeatherCondition.RAIN, 65, true),
        THUNDER("雷雨", WeatherCondition.THUNDERSTORM, 95, true),
        HAIL("冰雹", WeatherCondition.HAIL, 96, true),
    }

    data class Report(val kind: Kind, val at: Long, val lat: Double, val lon: Double)

    const val LIFETIME_MS = 60 * 60_000L
    const val RADIUS_KM = 8.0
    private const val STORE = "thinn_weather_reports"
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** The report that applies here and now, if any. */
    fun applicable(reports: List<Report>, lat: Double, lon: Double, now: Long): Report? =
        reports.filter { now - it.at in 0 until LIFETIME_MS && distanceKm(lat, lon, it.lat, it.lon) <= RADIUS_KM }.maxByOrNull { it.at }

    /** The snapshot as the person sees it: their sky, with the measured numbers unchanged. */
    fun apply(w: WeatherSnapshot, r: Report, zone: ZoneId = ZoneId.of("Asia/Taipei")): WeatherSnapshot =
        w.copy(condition = r.kind.condition, weatherCode = r.kind.code,
            observationDescription = "${r.kind.label}（你 ${hm.format(Instant.ofEpochMilli(r.at).atZone(zone))} 回報）")

    /** Whether the report contradicts what the app showed: rain against a dry sky, or the reverse. */
    fun disagrees(w: WeatherSnapshot, kind: Kind): Boolean {
        val wet = w.condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL,
            WeatherCondition.SLEET, WeatherCondition.FREEZING_RAIN, WeatherCondition.SNOW)
        return wet != kind.wet || kind.condition != w.condition && kind in setOf(Kind.THUNDER, Kind.HAIL, Kind.FOG, Kind.HEAVY_RAIN)
    }

    // ---- storage ----
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Report> = runCatching {
        val a = JSONArray(prefs(ctx).getString("reports", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val kind = runCatching { Kind.valueOf(o.getString("k")) }.getOrNull() ?: return@mapNotNull null
            Report(kind, o.getLong("t"), o.getDouble("la"), o.getDouble("lo"))
        }
    }.getOrDefault(emptyList())

    fun save(ctx: Context, report: Report) {
        val kept = (load(ctx) + report).sortedBy { it.at }.takeLast(30)
        val a = JSONArray()
        kept.forEach { a.put(JSONObject().put("k", it.kind.name).put("t", it.at).put("la", it.lat).put("lo", it.lon)) }
        prefs(ctx).edit().putString("reports", a.toString()).apply()
    }

    /** Withdraws the reports for this place (back to the official data). */
    fun clear(ctx: Context, lat: Double, lon: Double) {
        val kept = load(ctx).filter { distanceKm(lat, lon, it.lat, it.lon) > RADIUS_KM }
        val a = JSONArray()
        kept.forEach { a.put(JSONObject().put("k", it.kind.name).put("t", it.at).put("la", it.lat).put("lo", it.lon)) }
        prefs(ctx).edit().putString("reports", a.toString()).apply()
    }
}
