package io.atmos.weather.life

import android.content.Context
import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.Instant

/** Gathers what the two-sentence digest needs: warnings and closures for the county, and the pressure. */
object LifeDigest {
    data class Context2(val warnings: List<String>, val closures: List<String>, val pressure: PressurePain.Outlook?)

    suspend fun gather(ctx: Context, w: WeatherSnapshot, placeName: String, officialWarnings: List<String> = emptyList()): Context2 {
        val now = Instant.now()
        val county = LifeSources.county(placeName)
        val alerts = LifeSources.alerts(ctx).filter { it.activeAt(now) && NcdrFeed.mentions(it, county) }
        val closures = alerts.filter { it.category in NcdrFeed.closureCategories }
            .map { WorkSchoolClosure.sentenceFor(it.summary, county ?: "") }.distinct()
        val fromFeed = alerts.filter { it.category !in NcdrFeed.closureCategories && it.category != "地震" &&
                // The page passes the CWA's own county warnings; the feed's copies would repeat them.
                !(officialWarnings.isNotEmpty() && it.author.contains("氣象署")) }
            .map { if (it.author.contains("氣象署")) "${it.category}特報" else "${it.category}警戒" }
        val warnings = (officialWarnings + fromFeed).distinct()
        val pressure = LifeSources.pressure(w.city.coordinate.latitude, w.city.coordinate.longitude)?.let { PressurePain.assess(it, now) }
        return Context2(warnings, closures, pressure)
    }

    fun text(ctx: Context, w: WeatherSnapshot, c: Context2): String =
        DailyDigest.build(DailyDigest.Inputs(w, c.warnings, c.pressure?.level, c.closures, LifeStore.comfortBias(ctx)))

    suspend fun forNotification(ctx: Context, w: WeatherSnapshot, placeName: String): String = text(ctx, w, gather(ctx, w, placeName))
}
