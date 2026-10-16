package io.atmos.weather.location

import android.content.Context
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import org.json.JSONArray
import org.json.JSONObject

/** Stored on-device, no cloud account. Manual locations never overwrite last GPS fix. */
object SavedCityStore {
    private const val KEY = "saved_weather_cities_v10"
    fun all(context: Context): List<WeatherCity> {
        val prefs=context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
        return runCatching {
            val data=JSONArray(prefs.getString(KEY,"[]"))
            (0 until data.length()).mapNotNull { index ->
                val c=data.optJSONObject(index) ?: return@mapNotNull null
                val id=c.optString("id")
                val name=c.optString("name")
                val lat=c.optDouble("lat",Double.NaN)
                val lon=c.optDouble("lon",Double.NaN)
                if(id.isBlank() || name.isBlank() || !lat.isFinite() || !lon.isFinite()) null
                else WeatherCity(id,name,GeoPoint(lat,lon))
            }.distinctBy { it.id }.take(30)
        }.getOrDefault(emptyList())
    }
    fun add(context: Context,city: WeatherCity): List<WeatherCity> {
        if(city.id=="gps") return all(context)
        val others=all(context).filterNot { it.id==city.id }
        val saved=(listOf(city)+others).take(30)
        save(context,saved)
        return saved
    }
    fun remove(context:Context,id:String):List<WeatherCity> {
        val saved=all(context).filterNot{it.id==id}
        save(context,saved)
        return saved
    }
    private fun save(context:Context,items:List<WeatherCity>) {
        val arr=JSONArray()
        items.forEach { city -> arr.put(JSONObject().apply {
            put("id",city.id);put("name",city.name)
            put("lat",city.coordinate.latitude);put("lon",city.coordinate.longitude)
        }) }
        context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).edit().putString(KEY,arr.toString()).apply()
    }
}
