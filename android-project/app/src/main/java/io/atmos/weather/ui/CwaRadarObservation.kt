package io.atmos.weather.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Official O-A0058-003 observation, not a forecast. 118..124 E / 20.5..26.5 N. */
data class RadarImage(val bitmap: Bitmap, val fetchedAt: Long, val modifiedAt: Long?,
    val observedAt: Long? = null, val cached: Boolean = false)

object CwaRadarObservation {
    internal const val IMAGE_URL="https://cwaopendata.s3.ap-northeast-1.amazonaws.com/Observation/O-A0058-003.png"
    private const val METADATA_URL="https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/O-A0058-003?Authorization=rdec-key-123-45678-011121314&format=JSON"
    private const val maxBytes=18_000_000
    private val mutex=Mutex()
    private var cached: RadarImage? = null
    fun coverage(lat:Double,lon:Double):Boolean = lat in 20.5..26.5 && lon in 118.0..124.0
    fun mapPoint(lat:Double,lon:Double):Pair<Float,Float> =
        Pair(((lon-118.0)/6.0).toFloat(),((26.5-lat)/6.0).toFloat())

    /** CWA S3 serves genuine PNGs as binary/octet-stream. Verify bytes and bounds,
     * rather than rejecting this official response based on its MIME label. */
    internal fun decode(bytes:ByteArray):Bitmap? {
        if(bytes.size !in 24..maxBytes || !bytes.copyOfRange(0,8).contentEquals(
                byteArrayOf(-119,80,78,71,13,10,26,10)))return null
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth!=3600 || bounds.outHeight!=3600)return null
        // 1800px preview: four times the former pixel count, bounded to ~13 MiB.
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=2})
    }
    internal fun observationTime(json:String,now:Long):Long? = runCatching {
        val root=JSONObject(json).getJSONObject("cwaopendata")
        require(root.optString("status")=="Actual" && root.optString("dataid")=="O-A0058-003")
        val dataset=root.getJSONObject("dataset")
        require(dataset.getJSONObject("resource").getString("ProductURL")==IMAGE_URL)
        Instant.parse(dataset.getString("DateTime")).toEpochMilli()
            .takeIf{now-it in -600_000L..3_600_000L}
    }.getOrNull()

    private suspend fun read(url:String,limit:Int):Pair<ByteArray,Long?> = withContext(Dispatchers.IO) {
        val c=(URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout=8000;readTimeout=12000
            setRequestProperty("Accept",if(url==IMAGE_URL)"image/png, application/octet-stream" else "application/json")
            setRequestProperty("User-Agent","thinn-khi/25 (+https://github.com/luc700428-hash/thinn-kh-.)")
        }
        try {
            check(c.responseCode==200 && c.contentLengthLong<=limit)
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use {input->val buffer=ByteArray(8192);while(true){
                currentCoroutineContext().ensureActive()
                val n=input.read(buffer);if(n<0)break
                check(out.size()+n<=limit);out.write(buffer,0,n)
            }}
            out.toByteArray() to c.lastModified.takeIf{it>0}
        } finally{c.disconnect()}
    }
    suspend fun latest(force:Boolean=false):RadarImage? = mutex.withLock {
        val now=System.currentTimeMillis()
        cached?.takeIf{!force && now-it.fetchedAt in 0L..300_000L}?.let{return@withLock it}
        val result=try {
            coroutineScope {
                val metadata=async(Dispatchers.IO) {
                    try {observationTime(String(read(METADATA_URL,64_000).first,Charsets.UTF_8),now)}
                    catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){null}
                }
                val (bytes,modified)=read(IMAGE_URL,maxBytes)
                val bitmap=withContext(Dispatchers.Default){decode(bytes)}
                bitmap?.let{RadarImage(it,now,modified,metadata.await())}
            }
        } catch(cancel:CancellationException){throw cancel}
          catch(_:Exception){null}
        if(result!=null)cached=result
        result ?: cached?.takeIf{now-(it.observedAt?:it.modifiedAt?:it.fetchedAt) in 0L..3_600_000L}?.copy(cached=true)
    }
}
