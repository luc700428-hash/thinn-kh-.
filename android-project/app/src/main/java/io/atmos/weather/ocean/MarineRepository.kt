package io.atmos.weather.ocean

import io.atmos.weather.data.remote.CwaSurfaceObservations
import io.atmos.weather.domain.model.WeatherCity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt

data class SeaPlace(val id:String,val name:String,val latitude:Double,val longitude:Double)
data class MarineHour(val at:Instant,val waveHeight:Double?,val waveDirection:Double?,val wavePeriod:Double?,
    val swellHeight:Double?,val swellDirection:Double?,val swellPeriod:Double?,
    val currentMs:Double?,val currentDirection:Double?,val seaTemperature:Double?)
data class MarineSnapshot(val place:SeaPlace,val latitude:Double,val longitude:Double,val current:MarineHour,
    val hours:List<MarineHour>,val fetchedAt:Instant) {
    fun fresh(now:Instant=Instant.now())=now.epochSecond-current.at.epochSecond in -900L..5400L&&now.epochSecond-fetchedAt.epochSecond in -120L..1800L
}
object MarineRepository {
    val places=listOf(SeaPlace("keelung","基隆外海",25.22,121.82),SeaPlace("tamsui","淡水外海",25.25,121.35),
        SeaPlace("yilan","宜蘭外海",24.65,121.95),SeaPlace("hualien","花蓮外海",24.05,121.78),
        SeaPlace("taitung","臺東外海",22.80,121.32),SeaPlace("hengchun","恆春外海",21.85,120.80),
        SeaPlace("kaohsiung","高雄外海",22.55,120.12),SeaPlace("tainan","臺南外海",23.0,120.0),
        SeaPlace("budai","嘉義布袋近海",23.35,120.05),SeaPlace("taichung","臺中外海",24.20,120.42),
        SeaPlace("penghu","澎湖海域",23.60,119.50),SeaPlace("kinmen","金門海域",24.40,118.20),
        SeaPlace("matsu","馬祖海域",26.20,119.85))
    fun nearest(city:WeatherCity)=places.minBy {CwaSurfaceObservations.distanceKm(city.coordinate.latitude,city.coordinate.longitude,it.latitude,it.longitude)}
    private val lock=Mutex();private val cache=mutableMapOf<String,MarineSnapshot>()
    private const val VARIABLES="wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period,ocean_current_velocity,ocean_current_direction,sea_surface_temperature"
    fun url(place:SeaPlace)="https://marine-api.open-meteo.com/v1/marine?latitude=${place.latitude}&longitude=${place.longitude}&current=$VARIABLES&hourly=$VARIABLES&forecast_days=2&timezone=Asia%2FTaipei&cell_selection=sea&timeformat=unixtime"
    suspend fun read(place:SeaPlace,force:Boolean=false):MarineSnapshot=withContext(Dispatchers.IO) {
        lock.withLock {
            val now=Instant.now();val previous=cache[place.id]
            if(!force&&previous?.fresh(now)==true)return@withLock previous
            val c=URL(url(place)).openConnection() as HttpsURLConnection
            c.connectTimeout=7000;c.readTimeout=10000;c.instanceFollowRedirects=false
            c.setRequestProperty("Accept","application/json")
            try {
                check(c.responseCode==200&&c.contentLengthLong<=500_000)
                val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192)
                c.inputStream.use {input->while(true){val n=input.read(b);if(n<0)break;check(out.size()+n<=500_000);out.write(b,0,n)}}
                parse(out.toString("UTF-8"),place,now).also {cache[place.id]=it}
            } catch(cancel:CancellationException){throw cancel}
              catch(error:Exception){previous?.takeIf {now.epochSecond-it.fetchedAt.epochSecond in 0L..6*3600L}?:throw error}
            finally{c.disconnect()}
        }
    }
    fun parse(raw:String,place:SeaPlace,at:Instant):MarineSnapshot {
        require(raw.length<=500_000);val json=JSONObject(raw);val current=json.getJSONObject("current")
        val hourly=json.optJSONObject("hourly");val times=hourly?.optJSONArray("time")
        fun record(o:JSONObject,units:JSONObject?,index:Int?):MarineHour {
            fun n(key:String):Double? {
                val raw=if(index==null)o.opt(key) else o.optJSONArray(key)?.opt(index)
                return (raw as? Number)?.toDouble()?.takeIf{it.isFinite()}
            }
            fun height(key:String)=n(key)?.let {when(units?.optString(key)){"ft"->it*.3048;"m",null->it;else->null}}?.takeIf{it in 0.0..40.0}
            fun angle(key:String)=n(key)?.takeIf{it in 0.0..360.0}
            fun period(key:String)=n(key)?.takeIf{it in 0.0..60.0}
            val epoch=n("time")?.toLong()?.takeIf{it in 946684800L..4102444800L}?:error("缺少海象預報時間")
            val velocity=n("ocean_current_velocity")?.let {when(units?.optString("ocean_current_velocity")){"km/h"->it/3.6;"kn"->it*.514444;"m/s"->it;"mph"->it*.44704;else->null}}?.takeIf{it in 0.0..20.0}
            val temperature=n("sea_surface_temperature")?.let {when(units?.optString("sea_surface_temperature")){"°C","celsius"->it;"°F","fahrenheit"->(it-32)/1.8;else->null}}?.takeIf{it in -3.0..45.0}
            return MarineHour(Instant.ofEpochSecond(epoch),height("wave_height"),angle("wave_direction"),period("wave_period"),
                height("swell_wave_height"),angle("swell_wave_direction"),period("swell_wave_period"),velocity,angle("ocean_current_direction"),
                temperature)
        }
        val now=record(current,json.optJSONObject("current_units"),null)
        val hours=if(hourly==null||times==null)emptyList() else (0 until minOf(times.length(),96)).mapNotNull {i->runCatching{record(hourly,json.optJSONObject("hourly_units"),i)}.getOrNull()}
            .filter{it.at>=now.at.minusSeconds(3600)&&it.at<=now.at.plusSeconds(48*3600)}.distinctBy{it.at}.sortedBy{it.at}
        val lat=json.optDouble("latitude").takeIf{it.isFinite()&&it in -90.0..90.0}?:error("缺少海域座標")
        val lon=json.optDouble("longitude").takeIf{it.isFinite()&&it in -180.0..180.0}?:error("缺少海域座標")
        require(now.waveHeight!=null||hours.any{it.waveHeight!=null}){"目前沒有海象模式資料"}
        return MarineSnapshot(place,lat,lon,now,hours,at)
    }
}
object CompassDirections {
    private val labels=listOf("北","北北東","東北","東北東","東","東南東","東南","南南東","南","南南西","西南","西南西","西","西北西","西北","北北西")
    fun name(degrees:Double?):String=degrees?.takeIf{it.isFinite()}?.let{labels[Math.floorMod((it/22.5).roundToInt(),16)]}?:"未提供"
}
