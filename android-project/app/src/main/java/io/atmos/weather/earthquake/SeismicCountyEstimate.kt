package io.atmos.weather.earthquake

/**
 * When an early warning arrives without per-county intensities, each county gets a clearly
 * labelled local estimate instead of a grey map: the highest intensity expected at any of a few
 * towns in it, from the same PGA attenuation used for the person's own location
 * ([SeismicLocalPrediction.coarseIntensity]: CWA intensity bands 0–4 by PGA, no site factor).
 * Above 80 gal (PGA alone cannot separate 5弱 and stronger) the estimate is "4" and the label says
 * it may be stronger. Upstream county values are always used when present; alert eligibility
 * never depends on these estimates.
 */
object SeismicCountyEstimate {
    data class View(val values: Map<String, String>, val estimated: Boolean, val atLeast: Set<String> = emptySet())

    /** A few towns per county (lat, lon): the county's strongest one stands for it. */
    internal val towns: Map<String, List<Pair<Double, Double>>> = mapOf(
        "基隆市" to listOf(25.13 to 121.74),
        "臺北市" to listOf(25.04 to 121.56, 25.12 to 121.50),
        "新北市" to listOf(25.01 to 121.46, 25.17 to 121.44, 24.86 to 121.55, 25.12 to 121.85, 24.95 to 121.35),
        "桃園市" to listOf(24.99 to 121.31, 24.86 to 121.22, 24.75 to 121.35),
        "新竹市" to listOf(24.80 to 120.97),
        "新竹縣" to listOf(24.83 to 121.01, 24.68 to 121.17, 24.58 to 121.10),
        "苗栗縣" to listOf(24.56 to 120.82, 24.69 to 120.88, 24.40 to 120.77, 24.43 to 121.00),
        "臺中市" to listOf(24.15 to 120.67, 24.25 to 120.53, 24.25 to 121.10, 24.07 to 120.70),
        "彰化縣" to listOf(24.08 to 120.54, 23.86 to 120.30, 23.90 to 120.58),
        "南投縣" to listOf(23.91 to 120.68, 23.97 to 120.97, 23.62 to 120.82, 23.70 to 121.07, 24.05 to 121.15),
        "雲林縣" to listOf(23.71 to 120.43, 23.70 to 120.25, 23.60 to 120.60),
        "嘉義市" to listOf(23.48 to 120.45),
        "嘉義縣" to listOf(23.46 to 120.25, 23.52 to 120.42, 23.45 to 120.75),
        "臺南市" to listOf(22.99 to 120.21, 23.30 to 120.32, 23.16 to 120.45),
        "高雄市" to listOf(22.63 to 120.30, 22.88 to 120.48, 23.10 to 120.75, 23.03 to 120.66),
        "屏東縣" to listOf(22.67 to 120.49, 22.47 to 120.55, 22.07 to 120.74, 22.38 to 120.62),
        "宜蘭縣" to listOf(24.75 to 121.75, 24.60 to 121.85, 24.50 to 121.45),
        "花蓮縣" to listOf(23.99 to 121.60, 23.67 to 121.42, 23.30 to 121.30, 24.15 to 121.55),
        "臺東縣" to listOf(22.76 to 121.14, 23.10 to 121.21, 22.37 to 120.89, 23.35 to 121.32, 22.05 to 121.55, 22.66 to 121.49),
        "澎湖縣" to listOf(23.57 to 119.58),
        "金門縣" to listOf(24.43 to 118.32),
        "連江縣" to listOf(26.16 to 119.95),
    )

    /** Estimated intensity per county; counties below 1 are left out, like an official report. */
    fun estimate(magnitude: Double?, depthKm: Double?, latitude: Double?, longitude: Double?): View {
        if (magnitude == null || latitude == null || longitude == null) return View(emptyMap(), true)
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in 15.0..32.0 || longitude !in 110.0..135.0) return View(emptyMap(), true)
        // A warning without a depth (early first reports) is treated as shallow, the usual case near Taiwan.
        val depth = depthKm?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 10.0
        val values = LinkedHashMap<String, String>(); val strong = HashSet<String>()
        for ((county, points) in towns) {
            var best = -1; var plus = false
            for ((lat, lon) in points) {
                val raw = SeismicLocalPrediction.coarseIntensity(magnitude.coerceAtMost(7.5), depth,
                    SeismicWavePhysics.epicentralKm(latitude, longitude, lat, lon).coerceAtMost(600.0)) ?: continue
                val grade = if (raw == "4級以上") 4 else raw.toIntOrNull() ?: continue
                if (grade > best || (grade == best && raw == "4級以上")) { best = grade; plus = raw == "4級以上" }
            }
            if (best >= 1) { values[county] = best.toString(); if (plus) strong += county }
        }
        return View(values, true, strong)
    }

    /** What to show for an incident: the source's county values, or the estimate when it sent none. */
    fun view(incident: EewIncident): View {
        val source = SeismicWarningCounties.fromProvider(incident.localIntensityByCounty)
        if (source.isNotEmpty()) return View(source.associate { it.county to it.intensity!! }, false)
        return estimate(incident.magnitude, incident.depthKm, incident.latitude, incident.longitude)
    }

    /** The estimate for one place name (county or town inside it). */
    fun forPlace(view: View, place: String?): String? {
        val name = place?.replace('台', '臺')?.removeSuffix("附近")?.trim().orEmpty()
        if (name.isBlank()) return null
        return view.values.entries.firstOrNull { name == it.key || name.contains(it.key) }?.value
    }

    /** "推算 4 級（可能更強）" or "4 級". */
    fun label(view: View, county: String, grade: String): String {
        val text = SeismicDisplayFormat.symbol(grade) + " 級"
        return if (!view.estimated) text else if (county in view.atLeast) "推算 $text 以上" else "推算 $text"
    }
}
