package io.atmos.weather.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Official CWA public observation image O-A0058-003, not a forecast product.
 * Geo extent: 118.0..124.0 E, 20.5..26.5 N; image is 3600x3600.
 * A blank area never means guaranteed absence of precipitation.
 */
data class RadarImage(val bitmap: Bitmap, val fetchedAt: Long, val modifiedAt: Long?)
object CwaRadarObservation {
    private const val url="https://cwaopendata.s3.ap-northeast-1.amazonaws.com/Observation/O-A0058-003.png"
    private const val maxBytes=18_000_000
    private var cached: RadarImage? = null
    fun coverage(lat:Double,lon:Double):Boolean = lat in 20.5..26.5 && lon in 118.0..124.0
    fun mapPoint(lat:Double,lon:Double):Pair<Float,Float> =
        Pair(((lon-118.0)/6.0).toFloat(),((26.5-lat)/6.0).toFloat())
    suspend fun latest():RadarImage? = withContext(Dispatchers.IO) {
        synchronized(this@CwaRadarObservation) { cached?.takeIf { System.currentTimeMillis()-it.fetchedAt < 600_000L } }?.let { return@withContext it }
        val result=runCatching {
            val connection=(URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout=8000;readTimeout=12000
                setRequestProperty("Accept","image/png")
                setRequestProperty("User-Agent","thinn-khi/18.0 (weather radar observation)")
            }
            try {
                if(connection.responseCode!=200 || !connection.contentType.orEmpty().contains("image/")) return@runCatching null
                val declared=connection.contentLengthLong
                if(declared>maxBytes) return@runCatching null
                val out=java.io.ByteArrayOutputStream()
                connection.inputStream.use { stream ->
                    val buffer=ByteArray(8192);var bytes=0
                    while(true) {
                        val n=stream.read(buffer); if(n<0) break
                        bytes+=n;if(bytes>maxBytes) return@runCatching null
                        out.write(buffer,0,n)
                    }
                }
                val opts=BitmapFactory.Options().apply { inSampleSize=4 }
                val bmp=BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size(),opts) ?: return@runCatching null
                RadarImage(bmp,System.currentTimeMillis(),connection.lastModified.takeIf { it>0 })
            } finally {connection.disconnect()}
        }.getOrNull()
        if(result!=null) synchronized(this@CwaRadarObservation) {cached=result}
        result ?: synchronized(this@CwaRadarObservation) {cached?.takeIf { System.currentTimeMillis()-it.fetchedAt < 3_600_000L }}
    }
}
