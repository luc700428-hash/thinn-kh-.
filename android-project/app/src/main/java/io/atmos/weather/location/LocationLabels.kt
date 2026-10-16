package io.atmos.weather.location

import io.atmos.weather.domain.model.GeoPoint

/** Presentation policy: always use「台灣」, never the spurious province suffix. */
object LocationLabels {
    private val unwanted = Regex("(?i)(Taiwan\\s*,?\\s*Province\\s*of\\s*China|Taiwan Province|Province of Taiwan|Taiwan, China|臺灣省|台灣省|台湾省)")
    private val taiwanNames = setOf("TW", "Taiwan", "台灣", "臺灣", "台湾", "Taiwan, Province of China")
    fun isTaiwan(countryCode: String?, countryName: String?): Boolean =
        countryCode.equals("TW", ignoreCase = true) || countryName?.trim() in taiwanNames ||
            countryName?.contains("Taiwan", ignoreCase = true) == true ||
            countryName?.contains("台灣") == true || countryName?.contains("臺灣") == true

    fun clean(raw: String): String = unwanted.replace(raw, "台灣")
        .replace("臺灣", "台灣")
        .replace(Regex("台灣(?:\\s*[,·/-]\\s*台灣)+"), "台灣")
        .trim(' ', ',', '·', '-', '/')

    fun name(countryCode: String?, country: String?, region: String?, city: String?): String {
        val parts = listOfNotNull(region, city).map { clean(it) }
            .filter { it.isNotBlank() && it != "台灣" }.distinct()
        return if (isTaiwan(countryCode, country)) {
            (listOf("台灣") + parts).joinToString(" · ")
        } else {
            (listOfNotNull(country?.let(::clean)) + parts).distinct().joinToString(" · ")
                .ifBlank { "未知地點" }
        }
    }

    /** Presentation only. Keep full county/district label in storage for weather alerts. */
    fun compact(raw: String): String {
        val tokens = clean(raw).replace(Regex("(?i)\\s+or\\s+"), " · ")
            .split(Regex("\\s*[·,/]\\s*"))
            .map { clean(it).trim() }.filter { it.isNotEmpty() }
            .filterNot { it.equals("or", ignoreCase=true) }
        val unique = tokens.distinct().filterNot { it == "台灣" }
        val tail = unique.lastOrNull()?.takeIf { it != "台灣" && it.isNotBlank() }
        return if (tokens.any { it == "台灣" }) "台灣 · ${tail ?: "目前位置"}"
            else tail ?: "目前位置"
    }

    /** Main location line: Taiwan + municipality/county + actual town/district.
     * For non-Taiwan locations preserve the available country/region/city layers.
     */
    fun display(raw: String): String {
        val parts=clean(raw).replace(Regex("(?i)\\s+or\\s+")," · ")
            .split(Regex("\\s*[·,/]\\s*"))
            .map { clean(it).trim() }.filter {it.isNotEmpty() && !it.equals("or",true)}
            .distinct()
        return if (parts.any { it=="台灣" }) {
            (listOf("台灣") + parts.filterNot{it=="台灣"}.take(2)).joinToString(" · ")
        } else parts.takeLast(3).joinToString(" · ").ifBlank {"目前位置"}
    }

    /** Microcity caption preserves county + district while omitting country-level repetition. */
    fun microcity(raw: String): String {
        val parts = clean(raw).replace(Regex("(?i)\\s+or\\s+"), " · ")
            .split(Regex("\\s*[·,/]\\s*"))
            .map { clean(it).trim() }
            .filter { it.isNotBlank() && it != "台灣" && !it.equals("or",true) }
            .distinct()
        return parts.takeLast(2).joinToString(" · ").ifBlank { "目前位置" }
    }

    /** Approximate county only when a reliable administrative name was unavailable.
     * This fallback is not a substitute for authoritative administrative boundaries. */
    fun countyFromPoint(point: GeoPoint): String? = if (probablyTaiwan(point)) {
        countyCenters.minByOrNull {
            val x=(it.second-point.latitude)*111.0
            val y=(it.third-point.longitude)*101.0
            x*x+y*y
        }?.first?.replace('臺','台')
    } else null

    /** Offline county-level fallback when Geocoder or Google services are unavailable. */
    private val countyCenters = listOf(
        Triple("基隆市",25.1283,121.7419), Triple("臺北市",25.0330,121.5654),
        Triple("新北市",25.0111,121.4657), Triple("桃園市",24.9936,121.3010),
        Triple("新竹市",24.8138,120.9675), Triple("新竹縣",24.8387,121.0177),
        Triple("苗栗縣",24.5602,120.8214), Triple("臺中市",24.1477,120.6736),
        Triple("彰化縣",24.0753,120.5413), Triple("南投縣",23.9609,120.9719),
        Triple("雲林縣",23.7092,120.4313), Triple("嘉義市",23.4801,120.4491),
        Triple("嘉義縣",23.4518,120.2555), Triple("臺南市",22.9997,120.2270),
        Triple("高雄市",22.6273,120.3014), Triple("屏東縣",22.5519,120.5488),
        Triple("宜蘭縣",24.7021,121.7378), Triple("花蓮縣",23.9911,121.6112),
        Triple("臺東縣",22.7583,121.1444), Triple("澎湖縣",23.5712,119.5793),
        Triple("金門縣",24.4493,118.3768), Triple("連江縣",26.1602,119.9499)
    )
    fun probablyTaiwan(point: GeoPoint): Boolean =
        point.latitude in 21.7..26.5 && point.longitude in 118.0..122.2

    fun fallback(point: GeoPoint): String = if (probablyTaiwan(point)) {
        val nearest = countyCenters.minByOrNull {
            val x = (it.second - point.latitude) * 111.0
            val y = (it.third - point.longitude) * 101.0
            x*x + y*y
        }
        "台灣 · ${nearest?.first ?: "目前位置"}"
    } else "目前位置 · ${"%.2f".format(java.util.Locale.US, point.latitude)}, ${"%.2f".format(java.util.Locale.US, point.longitude)}"
}
