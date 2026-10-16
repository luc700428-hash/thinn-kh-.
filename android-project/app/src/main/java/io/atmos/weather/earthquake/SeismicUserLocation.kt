package io.atmos.weather.earthquake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/** GPS-first, manual location is an explicit user choice. No automatic city fallback. */
data class SeismicPlace(val name:String, val lat:Double, val lon:Double, val source:String, val ageMs:Long=0L)
object SeismicUserLocation {
    private const val PREFS="thinn_seismic_v15"
    private val counties=listOf(
        Triple("臺北市",25.04,121.54),Triple("新北市",25.01,121.46),Triple("基隆市",25.13,121.74),
        Triple("桃園市",24.99,121.31),Triple("新竹市",24.81,120.97),Triple("新竹縣",24.84,121.02),
        Triple("苗栗縣",24.56,120.82),Triple("臺中市",24.15,120.67),Triple("彰化縣",24.07,120.54),
        Triple("南投縣",23.91,120.68),Triple("雲林縣",23.71,120.54),Triple("嘉義市",23.48,120.45),
        Triple("嘉義縣",23.46,120.29),Triple("臺南市",22.99,120.21),Triple("高雄市",22.63,120.31),
        Triple("屏東縣",22.68,120.49),Triple("宜蘭縣",24.75,121.75),Triple("花蓮縣",23.99,121.60),
        Triple("臺東縣",22.76,121.14),Triple("澎湖縣",23.57,119.56),Triple("金門縣",24.45,118.37),
        Triple("連江縣",26.16,119.95))
    fun options():List<String> = listOf("跟隨手機定位")+counties.map { it.first }
    fun chosen(ctx:Context):String = ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("seismic_location_override", "") ?: ""
    fun choose(ctx:Context,city:String?) {
        val name=city?.takeIf { proposed -> counties.any { it.first==proposed } } ?: ""
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("seismic_location_override",name).apply()
    }
    private fun locationPermission(ctx:Context):Boolean = listOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION).any {
        ContextCompat.checkSelfPermission(ctx,it)==PackageManager.PERMISSION_GRANTED
    }
    @Suppress("MissingPermission")
    fun current(ctx:Context):SeismicPlace? {
        val manual=chosen(ctx)
        counties.find { it.first==manual }?.let { return SeismicPlace(it.first,it.second,it.third,"使用者自選地區（地標中心示意）") }
        if(!locationPermission(ctx)) return null
        val manager=ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val available=runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val now=System.currentTimeMillis()
        val loc=available.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { now-it.time in 0..900_000L && it.latitude in 18.0..28.0 && it.longitude in 115.0..124.0 }
            .maxByOrNull { it.time } ?: return null
        val county=counties.minByOrNull { SeismicWavePhysics.epicentralKm(loc.latitude,loc.longitude,it.second,it.third) }
        return SeismicPlace(county?.first?.plus("附近") ?: "使用者所在地",loc.latitude,loc.longitude,"手機定位（縣市名稱依鄰近中心推估）",now-loc.time)
    }
}
