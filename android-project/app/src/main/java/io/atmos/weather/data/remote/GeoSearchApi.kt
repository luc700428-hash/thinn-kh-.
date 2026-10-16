package io.atmos.weather.data.remote

import com.google.gson.annotations.SerializedName
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.location.LocationLabels
import retrofit2.http.GET
import retrofit2.http.Query

interface GeoSearchApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 20,
        @Query("language") language: String = "zh",
        @Query("format") format: String = "json"
    ): GeoSearchResponse
}
data class GeoSearchResponse(val results: List<GeoResult>?)
data class GeoResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    @SerializedName("country_code") val countryCode: String?,
    @SerializedName("admin1") val admin: String?
) {
    fun asCity() = WeatherCity("geo-$id", LocationLabels.name(countryCode, country, admin, name), GeoPoint(latitude, longitude))
}
