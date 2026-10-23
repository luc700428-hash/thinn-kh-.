package io.atmos.weather.typhoon

/** Maps source-provided directions to Traditional Chinese; no geographic inference. */
object TyphoonDirection {
    private val points = mapOf(
        "N" to "北", "NNE" to "北北東", "NE" to "東北", "ENE" to "東北東",
        "E" to "東", "ESE" to "東南東", "SE" to "東南", "SSE" to "南南東",
        "S" to "南", "SSW" to "南南西", "SW" to "西南", "WSW" to "西南西",
        "W" to "西", "WNW" to "西北西", "NW" to "西北", "NNW" to "北北西"
    )
    fun zh(raw: String?): String? {
        val clean=raw?.trim()?.uppercase()?.replace('-', ' ')?.replace('_',' ')
            ?.replace(Regex("\\s+")," ") ?: return null
        if(clean.isBlank()) return null
        val code=clean.replace(" ", "")
        points[code]?.let { return it }
        val full=mapOf(
            "NORTH" to "北", "NORTHEAST" to "東北", "EAST" to "東", "SOUTHEAST" to "東南",
            "SOUTH" to "南", "SOUTHWEST" to "西南", "WEST" to "西", "NORTHWEST" to "西北",
            "WESTNORTHWEST" to "西北西", "NORTHNORTHWEST" to "北北西",
            "EASTNORTHEAST" to "東北東", "NORTHNORTHEAST" to "北北東",
            "EASTSOUTHEAST" to "東南東", "SOUTHSOUTHEAST" to "南南東",
            "WESTSOUTHWEST" to "西南西", "SOUTHSOUTHWEST" to "南南西"
        )
        full[code]?.let { return it }
        // Source wording may include "Moving northwestward at 12 km/h".
        // Translate only a direction explicitly present in the official field;
        // do not infer heading from the geometry or weather model.
        val normalized = code.removePrefix("MOVING").removePrefix("HEADING")
            .removePrefix("TOWARDS").removePrefix("TOWARD")
        val directionPart = normalized.substringBefore("AT")
            .removeSuffix("WARDS").removeSuffix("WARD")
        points[directionPart]?.let { return it }
        full[directionPart]?.let { return it }
        // Handle an official 16-point code with a trailing speed ("NW12KMH").
        val withSpeed = Regex("^(NNE|ENE|ESE|SSE|SSW|WSW|WNW|NNW|NE|SE|SW|NW|N|E|S|W)[0-9]+(?:KMH|KPH|KT|KNOTS)?$")
        withSpeed.matchEntire(directionPart)?.groupValues?.getOrNull(1)?.let { key ->
            points[key]?.let { return it }
        }
        // Never display unrecognized English strings in a Chinese UI.
        return raw.takeIf { it.isNotBlank() && it.all { ch -> ch.code > 127 || ch.isWhitespace() } }
    }
}
