package io.atmos.weather.radar
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class RadarFrame(val time:Long,val path:String)
data class RadarTimeline(val frames:List<RadarFrame>,val fetchedAt:Long,val cached:Boolean=false)
object RadarRepository {
    private val mutex=Mutex();private var timeline:RadarTimeline?=null
    private val gate=Semaphore(4)
    private var lastTrim=0L
    private fun trimCache(directory:java.io.File) = synchronized(this) {
        val now=System.currentTimeMillis();if(now-lastTrim<60000)return@synchronized
        lastTrim=now
        val files=directory.listFiles()?.sortedBy{it.lastModified()}.orEmpty();var total=files.sumOf{it.length()}
        for(f in files){if(total<=48L*1024*1024&&now-f.lastModified()<7*86400000L)break;val length=f.length();if(f.delete())total-=length}
    }
    private val images=object:LruCache<String,Bitmap>(12*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    fun parse(json:String,now:Long):RadarTimeline {
        val j=JSONObject(json);require(j.optString("host")=="https://tilecache.rainviewer.com")
        val a=j.getJSONObject("radar").getJSONArray("past")
        val frames=(0 until a.length()).mapNotNull {i->
            val v=a.getJSONObject(i);val t=v.optLong("time");val p=v.optString("path")
            if(t<=0||t>now/1000+600||t<now/1000-10800||!Regex("/v2/radar/[A-Za-z0-9/_-]{1,150}").matches(p))null else RadarFrame(t,p)
        }.distinctBy{it.time}.sortedBy{it.time}.takeLast(18)
        require(frames.isNotEmpty());return RadarTimeline(frames,now)
    }
    suspend fun timeline(force:Boolean=false):RadarTimeline=mutex.withLock {
        val now=System.currentTimeMillis()
        timeline?.takeIf{now-it.fetchedAt<(if(force)10000 else 300000)}?.let{return@withLock it}
        try {
            val bytes=request("https://api.rainviewer.com/public/weather-maps.json",300000)
            parse(String(bytes,Charsets.UTF_8),now).also{timeline=it}
        } catch(cancel:CancellationException){throw cancel}
        catch(e:Exception){timeline?.takeIf{now-it.fetchedAt<3600000}?.copy(cached=true)?:throw e}
    }
    suspend fun image(context:Context,frame:RadarFrame?,tile:RadarTile):Bitmap? {
        val key=(frame?.path?:"base")+"/${tile.z}/${tile.x}/${tile.y}"
        synchronized(images){images.get(key)}?.let{return it}
        return gate.withPermit {withContext(Dispatchers.IO){
            synchronized(images){images.get(key)}?.let{return@withContext it}
            try {
                val url=if(frame==null)"https://tile.openstreetmap.org/${tile.z}/${tile.x}/${tile.y}.png" else
                    "https://tilecache.rainviewer.com${frame.path}/256/${tile.z}/${tile.x}/${tile.y}/2/1_1.png"
                // On-demand only. Cache base tiles >=7 days and radar frames for 3 hours;
                // a playback loop never repeatedly downloads the same frame's tiles.
                val digest=java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}
                val file=java.io.File(context.cacheDir,"radar-tiles/$digest.png")
                val ttl=if(frame==null)7*86400000L else 3*3600000L
                val bytes=if(file.isFile&&System.currentTimeMillis()-file.lastModified()<ttl)file.readBytes() else {
                    request(url,1500000).also {payload->
                        file.parentFile?.mkdirs()
                        val pending=java.io.File.createTempFile("tile-",".pending",file.parentFile)
                        try{pending.writeBytes(payload);if(!pending.renameTo(file))file.writeBytes(payload)}finally{pending.delete()}
                        trimCache(file.parentFile!!)
                    }
                }
                currentCoroutineContext().ensureActive()
                decodeTile(bytes)?.also{synchronized(images){images.put(key,it)}}
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){null}
        }}
    }
    internal fun decodeTile(bytes:ByteArray):Bitmap? {
        if(bytes.size !in 8..1500000)return null
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth !in 1..512||bounds.outHeight !in 1..512)return null
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size)
    }
    private suspend fun request(url:String,limit:Int):ByteArray=withContext(Dispatchers.IO) {
        val connection=(URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout=7000;readTimeout=8000;instanceFollowRedirects=false
            setRequestProperty("User-Agent","thinn-khi/25 personal-weather-map (+https://github.com/luc700428-hash/thinn-kh-.)")
        }
        try {
            check(connection.responseCode==200)
            check(connection.contentLengthLong<=limit)
            val bytes=java.io.ByteArrayOutputStream()
            connection.inputStream.use {input->val buffer=ByteArray(8192);while(true){
                currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                check(bytes.size()+n<=limit);bytes.write(buffer,0,n)
            }};bytes.toByteArray()
        } finally{connection.disconnect()}
    }
}
