package io.atmos.weather.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,surface_pressure,uv_index,wind_direction_10m,visibility,wind_gusts_10m,snowfall,snow_depth",
        @Query("hourly") hourly: String = "temperature_2m,apparent_temperature,precipitation_probability,uv_index,relative_humidity_2m,wind_speed_10m,weather_code,snowfall,snowfall_probability,freezing_rain_probability,wind_gusts_10m",
        @Query("daily") daily: String = "sunrise,sunset,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 3,
        @Query("temperature_unit") temperatureUnit:String="celsius",
        @Query("wind_speed_unit") windSpeedUnit:String="kmh"

    ): OpenMeteoResponse
}

data class OpenMeteoResponse(val timezone:String?,val current:CurrentDto?,val hourly:HourlyDto?,val daily:DailyDto?,
    val elevation:Double?=null,
    @SerializedName("current_units") val currentUnits:Map<String,String>?=null,
    @SerializedName("hourly_units") val hourlyUnits:Map<String,String>?=null)
data class CurrentDto(
    val time: String?,
    @SerializedName("temperature_2m") val temperature: Double?,
    @SerializedName("relative_humidity_2m") val humidity: Int?,
    @SerializedName("apparent_temperature") val feelsLike: Double?,
    @SerializedName("is_day") val isDay: Int?,
    @SerializedName("weather_code") val code: Int?,
    @SerializedName("wind_speed_10m") val wind: Double?,
    @SerializedName("surface_pressure") val pressure: Double?,
    @SerializedName("uv_index") val uv: Double?,
    @SerializedName("wind_direction_10m") val windDirection: Int?,
    @SerializedName("visibility") val visibility: Double?,
    @SerializedName("wind_gusts_10m") val gusts: Double?,
    @SerializedName("snowfall") val snowfall: Double?,
    @SerializedName("snow_depth") val snowDepth: Double?
)
data class HourlyDto(
    val time: List<String>?,
    @SerializedName("temperature_2m") val temperatures: List<Double?>?,
    @SerializedName("precipitation_probability") val rainProbabilities: List<Int?>?,
    @SerializedName("uv_index") val uvIndices: List<Double?>?,
    @SerializedName("relative_humidity_2m") val humidities: List<Int?>?,
    @SerializedName("wind_speed_10m") val winds: List<Double?>?,
    @SerializedName("weather_code") val weatherCodes: List<Int?>?,
    @SerializedName("snowfall_probability") val snowProbabilities: List<Int?>?,
    @SerializedName("freezing_rain_probability") val freezingRainProbabilities: List<Int?>?,
    @SerializedName("snowfall") val snowfalls: List<Double?>?,
    @SerializedName("wind_gusts_10m") val gusts: List<Double?>?,
    @SerializedName("apparent_temperature") val feelsLike:List<Double?>?=null
)
data class DailyDto(
    val sunrise: List<String>?,
    val sunset: List<String>?,
    @SerializedName("temperature_2m_max") val highs: List<Double?>?,
    @SerializedName("temperature_2m_min") val lows: List<Double?>?
)
