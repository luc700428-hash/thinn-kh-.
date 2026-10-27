package io.atmos.weather.location

import android.content.Context
import android.location.Geocoder
import io.atmos.weather.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object ReverseGeocoder {
    @Suppress("DEPRECATION")
    suspend fun resolve(context: Context, point: GeoPoint): String = withContext(Dispatchers.IO) {
        val fallback = LocationLabels.fallback(point)
        try {
            if (!Geocoder.isPresent()) return@withContext fallback
            val result = Geocoder(context, Locale.TRADITIONAL_CHINESE)
                .getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
                ?: return@withContext fallback
            val country = result.countryName
            val countryCode = result.countryCode
            val admin=result.adminArea
            val subAdmin=result.subAdminArea
            val county=sequenceOf(admin,subAdmin,result.locality).filterNotNull()
                .firstOrNull {it.endsWith("縣") || it.endsWith("市")} ?: admin
            val district=sequenceOf(result.subLocality,result.locality,subAdmin)
                .filterNotNull().firstOrNull {it!=county && it!="台灣" && it!="臺灣"}
            val label = LocationLabels.name(countryCode, country, county, district,point)
            if (label == "未知地點") fallback else label
        } catch (_: Exception) { fallback }
    }
}
