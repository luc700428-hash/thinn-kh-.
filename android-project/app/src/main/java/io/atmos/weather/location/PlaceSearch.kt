package io.atmos.weather.location

import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Landmarks, scenic spots, schools, stations and villages from OpenStreetMap (Nominatim, ODbL),
 * so a search like 「墾丁」「阿里山」「台北101」「清境農場」 finds a place, not only city names.
 * Labels always go through [LocationLabels.clean]: never 中國台灣 / 中華台北 / Taiwan, Province of China.
 */
object PlaceSearch {
    suspend fun search(http: OkHttpClient, query: String, limit: Int = 15): List<WeatherCity> = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query).addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1").addQueryParameter("limit", limit.toString())
            .addQueryParameter("accept-language", "zh-TW,zh,en").build()
        // Nominatim's usage policy requires an identifying User-Agent.
        val body = http.newCall(Request.Builder().url(url).header("User-Agent", "thinn-khi weather (github.com/luc700428-hash/thinn-kh-.)").build())
            .execute().use { r -> if (!r.isSuccessful) return@withContext emptyList(); r.body?.string() ?: "[]" }
        parse(JSONArray(body))
    }

    internal fun parse(items: JSONArray): List<WeatherCity> = (0 until items.length()).mapNotNull { i ->
        val o = items.optJSONObject(i) ?: return@mapNotNull null
        val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
        val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
        val a = o.optJSONObject("address") ?: JSONObject()
        fun s(k: String) = a.optString(k).takeIf { it.isNotBlank() }
        val name = o.optString("name").takeIf { it.isNotBlank() } ?: s("town") ?: s("city") ?: return@mapNotNull null
        // Region + the place itself, so the list shows 「屏東縣 · 墾丁大街」, not just the township.
        val region = s("county") ?: s("state") ?: s("city")
        val label = LocationLabels.name(s("country_code"), s("country"), region, name, GeoPoint(lat, lon))
        LocationLabels.city(WeatherCity("osm-${o.optString("osm_type").take(1)}${o.optLong("osm_id")}", label, GeoPoint(lat, lon)))
    }
}
