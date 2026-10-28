package io.atmos.weather.data.remote

import android.content.Context
import android.util.AtomicFile
import io.atmos.weather.domain.model.WeatherCity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlin.math.*

data class SurfaceObservation(val id:String,val name:String,val lon:Double,val lat:Double,val altitude:Double,
    val at:Instant,val temperature:Double,val humidity:Int,val windMs:Double,val pressure:Double?,
    val direction:Int?,val description:String?,val distanceKm:Double=0.0)

/** Fresh, nearby WGS84 station readings. Distant/higher, missing and stale stations cannot override a city. */
class CwaSurfaceObservations(context:Context) {
    private val cacheFile=File(context.applicationContext.cacheDir,"cwa_surface_v1.json.gz")
    private val lock=Mutex()
    private var cached:List<SurfaceObservation> = emptyList()
    private var fetchedAt=0L;private var attemptedAt=0L
    suspend fun nearest(city:WeatherCity,elevation:Double?,now:Instant):SurfaceObservation?=withContext(Dispatchers.IO) {
        if(!city.name.contains("台灣")&&!city.name.contains("臺灣"))return@withContext null
        if(elevation==null||!elevation.isFinite())return@withContext null
        val time=now.toEpochMilli()
        val values=lock.withLock {
            if(cached.isEmpty()&&cacheFile.exists()&&time-cacheFile.lastModified() in 0..600_000L) {
                cached=runCatching {GZIPInputStream(cacheFile.inputStream()).use {parse(readBounded(it,3_000_000))}}.getOrDefault(emptyList())
                if(cached.isNotEmpty())fetchedAt=cacheFile.lastModified()
            }
            if(time-fetchedAt>=600_000L&&time-attemptedAt>=30_000L) {
                attemptedAt=time
                try {
                    val raw=download();val parsed=parse(raw)
                    if(parsed.isNotEmpty()) {
                        cached=parsed;fetchedAt=time
                        runCatching {
                            val bytes=ByteArrayOutputStream().also {out->GZIPOutputStream(out).use{it.write(raw.toByteArray(Charsets.UTF_8))}}.toByteArray()
                            val atomic=AtomicFile(cacheFile);val stream=atomic.startWrite()
                            try {stream.write(bytes);atomic.finishWrite(stream)}catch(e:Exception){atomic.failWrite(stream);throw e}
                            cacheFile.setLastModified(time)
                        }
                    }
                } catch(cancel:CancellationException){throw cancel}
                  catch(_:Exception){/* The forecast remains usable when observations cannot be fetched. */}
            };cached
        }
        choose(values,city.coordinate.latitude,city.coordinate.longitude,elevation,now)
    }
    companion object {
        const val SOURCE_URL="https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/O-A0001-001?Authorization=rdec-key-123-45678-011121314&format=JSON"
        fun parse(raw:String):List<SurfaceObservation> {
            require(raw.length<=3_000_000)
            val root=JSONObject(raw)
            val data=root.optJSONObject("cwaopendata")?.let {it.optJSONObject("dataset")?:it.optJSONObject("Dataset")}
                ?:root.optJSONObject("records")?:return emptyList()
            val stations=data.optJSONArray("Station")?:return emptyList()
            fun JSONObject.number(key:String)=opt(key)?.toString()?.toDoubleOrNull()?.takeIf{it.isFinite()}
            return (0 until minOf(stations.length(),2000)).mapNotNull {i->
                val s=stations.optJSONObject(i)?:return@mapNotNull null
                val geo=s.optJSONObject("GeoInfo")?:return@mapNotNull null
                val coordinates=geo.optJSONArray("Coordinates")?:return@mapNotNull null
                val point=(0 until coordinates.length()).mapNotNull{coordinates.optJSONObject(it)}
                    .firstOrNull{it.optString("CoordinateName").equals("WGS84",true)}?:return@mapNotNull null
                val lat=point.number("StationLatitude")?.takeIf{it in 17.0..28.0}?:return@mapNotNull null
                val lon=point.number("StationLongitude")?.takeIf{it in 114.0..125.0}?:return@mapNotNull null
                val altitude=geo.number("StationAltitude")?.takeIf{it in -100.0..4500.0}?:return@mapNotNull null
                val at=runCatching{OffsetDateTime.parse(s.optJSONObject("ObsTime")?.optString("DateTime")).toInstant()}.getOrNull()?:return@mapNotNull null
                val w=s.optJSONObject("WeatherElement")?:return@mapNotNull null
                val temperature=w.number("AirTemperature")?.takeIf{it in -60.0..60.0}?:return@mapNotNull null
                val humidity=w.number("RelativeHumidity")?.takeIf{it in 0.0..100.0}?.roundToInt()?:return@mapNotNull null
                val wind=w.number("WindSpeed")?.takeIf{it in 0.0..100.0}?:return@mapNotNull null
                SurfaceObservation(s.optString("StationId").take(30),s.optString("StationName").take(40),lon,lat,altitude,at,temperature,humidity,wind,
                    w.number("AirPressure")?.takeIf{it in 300.0..1100.0},w.number("WindDirection")?.takeIf{it in 0.0..360.0}?.roundToInt(),
                    w.optString("Weather").takeIf{it.isNotBlank()&&it!="-99"&&it!="X"&&it.length<=24})
            }
        }
        fun choose(stations:List<SurfaceObservation>,lat:Double,lon:Double,elevation:Double,now:Instant):SurfaceObservation? {
            if(!lat.isFinite()||!lon.isFinite()||!elevation.isFinite())return null
            return stations.mapNotNull {s->
                val age=now.epochSecond-s.at.epochSecond
                val distance=distanceKm(lat,lon,s.lat,s.lon)
                if(age !in -120L..1800L||abs(s.altitude-elevation)>200||distance>15)null else s.copy(distanceKm=distance)
            }.minByOrNull{it.distanceKm+abs(it.altitude-elevation)*.003}
        }
        /** CWA's wind/humidity apparent-temperature formula; wind input is metres per second. */
        fun apparent(temperature:Double,humidity:Int,windMs:Double):Double? {
            if(!temperature.isFinite()||temperature !in -60.0..60.0||humidity !in 0..100||!windMs.isFinite()||windMs !in 0.0..100.0)return null
            val e=humidity/100.0*6.105*exp(17.27*temperature/(237.7+temperature))
            return (1.04*temperature+.2*e-.65*windMs-2.7).takeIf{it.isFinite()}
        }
        fun distanceKm(lat:Double,lon:Double,y:Double,x:Double):Double {
            val a=sin(Math.toRadians(y-lat)/2).pow(2)+cos(Math.toRadians(lat))*cos(Math.toRadians(y))*sin(Math.toRadians(x-lon)/2).pow(2)
            return 12742*asin(sqrt(a.coerceIn(0.0,1.0)))
        }
        private fun readBounded(input:java.io.InputStream,limit:Int):String {
            val out=ByteArrayOutputStream();val b=ByteArray(8192)
            while(true){val n=input.read(b);if(n<0)break;check(out.size()+n<=limit);out.write(b,0,n)}
            return out.toString("UTF-8")
        }
        private fun download():String {
            val c=URL(SOURCE_URL).openConnection() as HttpsURLConnection
            c.connectTimeout=6000;c.readTimeout=8000;c.instanceFollowRedirects=false
            c.setRequestProperty("Accept","application/json")
            try {check(c.responseCode==200&&c.contentLengthLong<=3_000_000);return c.inputStream.use{readBounded(it,3_000_000)}}
            finally{c.disconnect()}
        }
    }
}
