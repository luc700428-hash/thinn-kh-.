package io.atmos.weather.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlin.math.*

internal interface CwaRadarRegions:java.io.Closeable {
    val width:Int
    val height:Int
    fun read(rect:Rect,sampleSize:Int=1):Bitmap
}

@Suppress("DEPRECATION")
private class AndroidCwaRadarRegions(file:File):CwaRadarRegions {
    private val decoder=BitmapRegionDecoder.newInstance(file.path,false)?:error("Invalid radar PNG")
    override val width get()=decoder.width
    override val height get()=decoder.height
    override fun read(rect:Rect,sampleSize:Int)=decoder.decodeRegion(rect,BitmapFactory.Options().apply {
        inSampleSize=sampleSize;inPreferredConfig=Bitmap.Config.ARGB_8888;inScaled=false
    })?:error("Missing radar region")
    override fun close()=decoder.recycle()
}

/** CWA's public, time-indexed Taiwan 3600px product; no API key or private endpoint.
 * The 118..124E / 20.5..26.5N chart is geographic, NOT Web Mercator.
 * Extract only exact colours in its own dBZ scale, mask printed annotations, then
 * reproject measured pixels. No smoothing, invented rain, or generative upscaling.
 */
internal object CwaHighDefinitionRadar {
    /** Beyond z9 the source cells are smaller than a screen pixel group, so tiles are drawn by
     * interpolating the measured dBZ levels between neighbouring cells: sharp, smooth edges
     * instead of blocks or a blurry stretch, and never a value outside the cells around it. */
    const val MAX_ZOOM=11
    /** Below this many source pixels across a 512 px tile, levels are interpolated. */
    internal const val INTERPOLATE_BELOW=300
    const val WEST=118.0;const val EAST=124.0;const val SOUTH=20.5;const val NORTH=26.5
    private val lock=Mutex()
    private var latest:RadarTimeline?=null
    private val sourceLock=Mutex()
    private val name=Regex("CV1_TW_3600_(\\d{12})\\.png")
    private val clock=DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    fun covers(latitude:Double,longitude:Double)=latitude in SOUTH..NORTH && longitude in WEST..EAST
    fun parse(index:String,nowMs:Long):RadarTimeline {
        require(index.length in 20..300_000 && index.contains("var RadarImg"))
        val frames=name.findAll(index).mapNotNull { match ->
            runCatching {
                val time=LocalDateTime.parse(match.groupValues[1],clock).atZone(ZoneId.of("Asia/Taipei")).toEpochSecond()
                if(time !in nowMs/1000-10_800..nowMs/1000+600)null
                else RadarFrame(time,"cwa:"+match.value)
            }.getOrNull()
        }.distinctBy{it.time}.sortedBy{it.time}.toList().takeLast(13)
        require(frames.isNotEmpty())
        return RadarTimeline(frames,nowMs)
    }
    suspend fun timeline(force:Boolean=false):RadarTimeline=lock.withLock {
        val now=System.currentTimeMillis()
        latest?.takeIf{now-it.fetchedAt in 0 until if(force)10_000L else 300_000L}?.let{return@withLock it}
        try {
            parse(String(request("https://www.cwa.gov.tw/Data/js/obs_img/Observe_radar.js",300_000),Charsets.UTF_8),now)
                .also{latest=it}
        } catch(cancel:CancellationException){throw cancel}
        catch(e:Exception){latest?.takeIf{now-it.fetchedAt in 0..3_600_000L}?.copy(cached=true)?:throw e}
    }
    private fun filename(frame:RadarFrame):String {
        val file=frame.path.removePrefix("cwa:")
        require(frame.path.startsWith("cwa:") && name.matches(file))
        val time=LocalDateTime.parse(name.matchEntire(file)!!.groupValues[1],clock)
            .atZone(ZoneId.of("Asia/Taipei")).toEpochSecond()
        require(time==frame.time)
        return file
    }
    private suspend fun file(context:Context,frame:RadarFrame):File=sourceLock.withLock {
        val file=File(context.cacheDir,"cwa-radar-hd/${filename(frame)}")
        if(file.isFile && file.length() in 100..8_000_000)return@withLock file
        val bytes=request("https://www.cwa.gov.tw/Data/radar/${filename(frame)}",8_000_000)
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth==3600 && bounds.outHeight==3600 && bounds.outMimeType=="image/png")
        file.parentFile!!.mkdirs()
        val pending=File.createTempFile("radar-",".part",file.parentFile)
        try{pending.writeBytes(bytes);check(pending.renameTo(file))}finally{pending.delete()}
        // Bounded immutable observation cache: ~2h retained, <32MiB total.
        var total=0L
        file.parentFile!!.listFiles().orEmpty().filter{it.extension=="png"}.sortedByDescending{it.name}.forEach{
            total+=it.length()
            if((total>32L*1024*1024 || System.currentTimeMillis()-it.lastModified()>10_800_000L)&&it!=file)it.delete()
        }
        file
    }
    suspend fun tiles(context:Context,frame:RadarFrame,tiles:List<RadarTile>):Map<RadarTile,ImageBitmap> =
        withContext(Dispatchers.IO) {
            require(tiles.distinctBy{Triple(it.z,it.x,it.y)}.size<=RadarPlayback.MAX_SOURCE_TILES_LARGE && tiles.all{RadarTilePolicy.valid(it,MAX_ZOOM)})
            val source=file(context,frame)
            try {
                val job=currentCoroutineContext()[Job]
                decode(source,tiles){job?.ensureActive()}.mapValues{it.value.asImageBitmap()}
            } catch(cancel:CancellationException){throw cancel}
            catch(e:Exception){source.delete();throw e}
        }
    internal fun decode(source:File,tiles:List<RadarTile>,checkActive:()->Unit={}):Map<RadarTile,Bitmap> =
        AndroidCwaRadarRegions(source).use{decode(it,tiles,checkActive)}

    internal fun decode(regions:CwaRadarRegions,tiles:List<RadarTile>,checkActive:()->Unit={}):Map<RadarTile,Bitmap> {
            require(regions.width==3600&&regions.height==3600)
            // Read the source's own 0–65 dBZ colour ramp, excluding text and neutral map ink.
            val scale=regions.read(Rect(3450,2450,3451,3580))
            val colours=HashSet<Int>()
            // The ramp in order, weakest echo first, so a colour can be read back as a level.
            val ramp=ArrayList<Int>()
            for(y in scale.height-1 downTo 0) {
                val c=scale.getPixel(0,y)
                val r=(c ushr 16)and 255;val g=(c ushr 8)and 255;val b=c and 255
                if(maxOf(r,g,b)-minOf(r,g,b)>60&&colours.add(c or (0xFF shl 24)))ramp.add(c or (0xFF shl 24))
            }
            scale.recycle();require(colours.size>=50){"CWA colour ramp invalid"}
            val levelOf=HashMap<Int,Int>(ramp.size*2).apply{ramp.forEachIndexed{i,c->put(c,i)}}
            val rampColours=ramp.toIntArray()
            val result=linkedMapOf<RadarTile,Bitmap>()
            val physical=hashMapOf<Triple<Int,Int,Int>,Bitmap>()
            tiles.forEach{tile ->
                checkActive()
                val identity=Triple(tile.z,tile.x,tile.y)
                physical[identity]?.let{result[tile]=it;return@forEach}
                val n=(1 shl tile.z).toDouble()
                val lonLeft=tile.x/n*360-180;val lonRight=(tile.x+1)/n*360-180
                val latTop=RadarProjection.latitude(tile.y/n);val latBottom=RadarProjection.latitude((tile.y+1)/n)
                val left=floor((max(WEST,lonLeft)-WEST)*600).toInt().coerceIn(0,3600)
                val right=ceil((min(EAST,lonRight)-WEST)*600).toInt().coerceIn(0,3600)
                val top=floor((NORTH-min(NORTH,latTop))*600).toInt().coerceIn(0,3600)
                val bottom=ceil((NORTH-max(SOUTH,latBottom))*600).toInt().coerceIn(0,3600)
                val output=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
                if(right>left && bottom>top && right-left<INTERPOLATE_BELOW) {
                    smooth(regions,tile,n,left,top,right,bottom,levelOf,rampColours,output,checkActive)
                } else if(right>left && bottom>top) {
                    // Decode only the original-resolution region needed by this tile.
                    var sampleSize=1
                    while(max(right-left,bottom-top)/sampleSize>1024)sampleSize*=2
                    val region=regions.read(Rect(left,top,right,bottom),sampleSize)
                    val raw=IntArray(region.width*region.height)
                    region.getPixels(raw,0,region.width,0,0,region.width,region.height)
                    val pixels=IntArray(512*512)
                    val xs=IntArray(512){x->floor(((tile.x+(x+.5)/512)/n*360-180-WEST)*600).toInt()}
                    for(y in 0 until 512) {
                        if(y%32==0)checkActive()
                        val sy=floor((NORTH-RadarProjection.latitude((tile.y+(y+.5)/512)/n))*600).toInt()
                        if(sy !in top until bottom)continue
                        for(x in 0 until 512) {
                            val sx=xs[x]
                            if(sx !in left until right || annotation(sx,sy))continue
                            val rx=((sx-left)/sampleSize).coerceAtMost(region.width-1)
                            val ry=((sy-top)/sampleSize).coerceAtMost(region.height-1)
                            val value=raw[ry*region.width+rx]
                            if(value in colours)pixels[y*512+x]=value
                        }
                    }
                    output.setPixels(pixels,0,512,0,0,512,512);region.recycle()
                }
                physical[identity]=output;result[tile]=output
            }
            return result
    }
    /**
     * Close-up tile: each output pixel takes the four nearest source cells, reads their dBZ level
     * from the ramp, blends the levels bilinearly and paints the ramp colour of the result. Edges
     * of an echo fade over about one output pixel so outlines are crisp, not stair-stepped.
     */
    private fun smooth(regions:CwaRadarRegions,tile:RadarTile,n:Double,left:Int,top:Int,right:Int,bottom:Int,
        levelOf:Map<Int,Int>,ramp:IntArray,output:Bitmap,checkActive:()->Unit) {
        val l=(left-1).coerceAtLeast(0);val t=(top-1).coerceAtLeast(0)
        val r=(right+1).coerceAtMost(3600);val b=(bottom+1).coerceAtMost(3600)
        val region=regions.read(Rect(l,t,r,b),1)
        val w=region.width;val h=region.height
        val raw=IntArray(w*h);region.getPixels(raw,0,w,0,0,w,h);region.recycle()
        val levels=IntArray(w*h){i->
            val sx=l+i%w;val sy=t+i/w
            if(annotation(sx,sy))-1 else levelOf[raw[i]]?:-1
        }
        fun level(x:Int,y:Int):Float {
            val cx=x-l;val cy=y-t
            return if(cx<0||cy<0||cx>=w||cy>=h)-1f else levels[cy*w+cx].toFloat()
        }
        val pixels=IntArray(512*512)
        val fxs=DoubleArray(512){x->((tile.x+(x+.5)/512)/n*360-180-WEST)*600-.5}
        for(y in 0 until 512) {
            if(y%32==0)checkActive()
            val fy=(NORTH-RadarProjection.latitude((tile.y+(y+.5)/512)/n))*600-.5
            if(fy< -1||fy>3600)continue
            val y0=floor(fy).toInt();val ty=(fy-y0).toFloat()
            for(x in 0 until 512) {
                val fx=fxs[x];if(fx< -1||fx>3600)continue
                val x0=floor(fx).toInt();val tx=(fx-x0).toFloat()
                val a=level(x0,y0);val bb=level(x0+1,y0);val c=level(x0,y0+1);val d=level(x0+1,y0+1)
                if(a<0&&bb<0&&c<0&&d<0)continue
                val v=(a*(1-tx)+bb*tx)*(1-ty)+(c*(1-tx)+d*tx)*ty
                if(v<= -.5f)continue
                // Measured cells only: never brighter than the strongest of the four neighbours.
                val top4=maxOf(maxOf(a,bb),maxOf(c,d))
                val idx=kotlin.math.round(v.coerceIn(0f,top4)).toInt().coerceIn(0,ramp.size-1)
                val alpha=((v+.5f)/.5f).coerceIn(0f,1f)
                pixels[y*512+x]=(ramp[idx] and 0xFFFFFF) or ((alpha*255).toInt() shl 24)
            }
        }
        output.setPixels(pixels,0,512,0,0,512,512)
    }
    internal fun annotation(x:Int,y:Int)= (x<1250&&y<760)||(x>2600&&y<300)||
        (x<660&&y>3180)||(x>3375&&y>2400)
    private suspend fun request(url:String,limit:Int):ByteArray=withContext(Dispatchers.IO) {
        val u=URL(url);require(u.protocol=="https"&&u.host=="www.cwa.gov.tw")
        val c=(u.openConnection() as HttpsURLConnection).apply {
            connectTimeout=7000;readTimeout=8000;instanceFollowRedirects=false
            setRequestProperty("User-Agent","thinn-khi/25 (+https://github.com/luc700428-hash/thinn-kh-.)")
        }
        try {
            check(c.responseCode==200&&c.contentLengthLong<=limit)
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use{input->val b=ByteArray(8192);while(true){
                currentCoroutineContext().ensureActive();val count=input.read(b);if(count<0)break
                check(out.size()+count<=limit);out.write(b,0,count)
            }}
            out.toByteArray()
        } finally{c.disconnect()}
    }
}
