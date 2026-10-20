package io.atmos.weather.maps

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import java.util.Locale

/** Licensed-to-display NLSC (Taiwan) WMS raster, no redistributed tiles or bulk caching.
 *  Project every weather/seismic pin with the same map bounds used for this raster.
 *  Failure leaves the existing georeferenced vectors readable, never blank.
 */
object NlscTerrain {
    data class Bounds(val west:Double,val south:Double,val east:Double,val north:Double) {
        fun valid() = west.isFinite() && south.isFinite() && east.isFinite() && north.isFinite() &&
            west < east && south < north && west >= 75.0 && east <= 160.0 && south >= -5.0 && north <= 60.0
    }
    fun DrawScope.drawCredit() {
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=android.graphics.Color.WHITE
            textSize=12f*density
            setShadowLayer(3f,0f,1f,android.graphics.Color.BLACK)
        }
        drawContext.canvas.nativeCanvas.drawText(
            "地形底圖 © 內政部國土測繪中心",8f*density,size.height-9f*density,paint)
    }

    // EMAP5 was the older contour-heavy backdrop; EMAP6 is cleaner and easier to read.
    private const val MAX_IMAGE_BYTES = 4_000_000
    suspend fun fetch(bounds: Bounds, width:Int, height:Int):ImageBitmap? = withContext(Dispatchers.IO) {
        if (!bounds.valid() || width < 50 || height < 50) return@withContext null
        val scale = minOf(1.5, 1600.0 / maxOf(width,height))
        val w = (width*scale).toInt().coerceAtLeast(50)
        val h = (height*scale).toInt().coerceAtLeast(50)
        val bbox=String.format(Locale.US,"%.6f,%.6f,%.6f,%.6f",bounds.west,bounds.south,bounds.east,bounds.north)
        // WMS 1.1.1 and EPSG:4326 use longitude,latitude order.
        val url="https://wms.nlsc.gov.tw/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap"+
            "&LAYERS=EMAP6&STYLES=&SRS=EPSG%3A4326&FORMAT=image%2Fjpeg"+
            "&BBOX="+URLEncoder.encode(bbox,"UTF-8")+"&WIDTH=$w&HEIGHT=$h"
        var conn:HttpsURLConnection?=null
        try {
            conn=(URL(url).openConnection() as HttpsURLConnection).apply {
                connectTimeout=7_000; readTimeout=9_000; instanceFollowRedirects=false
                setRequestProperty("Accept","image/jpeg")
            }
            if(conn.responseCode!=200 || !conn.contentType.orEmpty().contains("image",true)) return@withContext null
            if(conn.contentLengthLong > MAX_IMAGE_BYTES) return@withContext null
            val bytes=ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf=ByteArray(8192)
                while(true) {
                    val n=input.read(buf)
                    if(n<0) break
                    if(bytes.size()+n>MAX_IMAGE_BYTES) return@withContext null
                    bytes.write(buf,0,n)
                }
            }
            val bitmap=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size()) ?: return@withContext null
            bitmap.asImageBitmap()
        } catch (_:Exception) { null }
        finally { conn?.disconnect() }
    }
}

/** Backdrop is fixed to the visible geographic viewport; no tiles outlive the composable. */
@Composable
fun rememberNlscMap(bounds:NlscTerrain.Bounds?,width:Int,height:Int):ImageBitmap? {
    val map=produceState<ImageBitmap?>(initialValue=null,bounds,width,height) {
        value=null
        if(bounds!=null) value=NlscTerrain.fetch(bounds,width,height)
    }
    return map.value
}
