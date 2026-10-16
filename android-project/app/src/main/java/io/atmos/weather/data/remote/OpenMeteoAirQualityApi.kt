package io.atmos.weather.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/** Air-quality model estimates, not Taiwan EPA station measurements. */
interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun currentAir(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi,pm2_5,pm10,sulphur_dioxide,nitrogen_dioxide,ozone,carbon_monoxide,aerosol_optical_depth,dust",
        @Query("timezone") timezone: String = "auto"
    ): AirQualityResponse
}
data class AirQualityResponse(val current: AirQualityCurrent?)
data class AirQualityCurrent(
    @SerializedName("us_aqi") val usAqi: Int?,
    @SerializedName("pm2_5") val pm25: Double?,
    @SerializedName("pm10") val pm10: Double?,
    @SerializedName("sulphur_dioxide") val sulfurDioxide: Double?,
    @SerializedName("nitrogen_dioxide") val nitrogenDioxide: Double?,
    @SerializedName("ozone") val ozone: Double?,
    @SerializedName("carbon_monoxide") val carbonMonoxide: Double?,
    @SerializedName("aerosol_optical_depth") val aerosolOpticalDepth: Double?,
    @SerializedName("dust") val dust: Double?
)
