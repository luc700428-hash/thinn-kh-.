package io.atmos.weather.typhoon

import org.json.JSONArray
import org.json.JSONObject

/** Parse published CWA W-C0034-005 fix fields, without creating any artificial storm position/time. */
object CwaCycloneForecast {
    /** null means different source schema; empty list means recognized schema with no valid fixes. */
    fun parse(root: JSONObject, max: Int = 240): List<TyphoonFix>? {
        val records = root.optJSONObject("records") ?: return null
        val group = records.optJSONObject("TropicalCyclones") ?: return null
        val raw = group.opt("TropicalCyclone")
        val cyclones = when (raw) {
            is JSONArray -> (0 until minOf(raw.length(), 100)).mapNotNull { raw.optJSONObject(it) }
            is JSONObject -> listOf(raw)
            else -> emptyList()
        }
        val points = mutableListOf<TyphoonFix>()
        fun fixes(group: JSONObject?): List<JSONObject> {
            val rawFix = group?.opt("Fix")
            return when (rawFix) {
                is JSONArray -> (0 until minOf(rawFix.length(), 300)).mapNotNull { rawFix.optJSONObject(it) }
                is JSONObject -> listOf(rawFix)
                else -> emptyList()
            }
        }
        for (tc in cyclones) {
            val year = tc.optString("Year").trim()
            val td = tc.optString("CwaTdNo").trim()
            val numbered = tc.optString("CwaTyNo").trim()
            val name = tc.optString("TyphoonName").trim()
            val identity = td.ifBlank { numbered.ifBlank { name } }
            if (identity.isBlank() || identity.length > 64 || year.length > 8) continue
            val stormId = year + ":" + identity
            fun add(fix: JSONObject, kind: TyphoonFix.Kind, time: String) {
                if (points.size >= max || time.isBlank()) return
                val lat = fix.optString("CoordinateLatitude").toDoubleOrNull() ?: return
                val lon = fix.optString("CoordinateLongitude").toDoubleOrNull() ?: return
                if (!lat.isFinite() || !lon.isFinite() || lat !in 0.0..55.0 || lon !in 100.0..180.0) return
                if (VerifiedTrackPlanner.parseTime(time) == null) return
                points += TyphoonFix(lat, lon, kind, time, stormId)
            }
            for (fix in fixes(tc.optJSONObject("AnalysisData"))) {
                add(fix, TyphoonFix.Kind.OBSERVED, fix.optString("DateTime"))
            }
            for (fix in fixes(tc.optJSONObject("ForecastData"))) {
                val initial = VerifiedTrackPlanner.parseTime(fix.optString("InitialTime")) ?: continue
                val hours = fix.optString("ForecastHour").toDoubleOrNull() ?: continue
                if (!hours.isFinite() || hours !in 0.0..168.0) continue
                add(fix, TyphoonFix.Kind.FORECAST, initial.plusSeconds((hours * 3600.0).toLong()).toString())
            }
            if (points.size >= max) break
        }
        return points.distinctBy { listOf(it.stormId, it.timestamp, it.latitude, it.longitude, it.kind) }.take(max)
    }
}
