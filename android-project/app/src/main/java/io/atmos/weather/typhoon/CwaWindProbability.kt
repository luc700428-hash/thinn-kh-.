package io.atmos.weather.typhoon

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection

/** CWA W-C0034-003: official 7-BF wind-circle strike-probability KMZ.
 *  No track-derived probabilities, no 0% for missing coverage.
 *  Public *demo* credential is a temporary availability fallback, NOT a production API key.
 */
object CwaWindProbability {
    private const val MAX_DOWNLOAD=6_000_000
    private const val MAX_KML=8_000_000
    private const val MAX_POLYGONS=1000
    private const val MAX_VERTICES=80_000
    private const val CACHE_MS=20L*60_000L
    private const val URL_KMZ="https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/"+
        "W-C0034-003?Authorization=rdec-key-123-45678-011121314&downloadType=WEB&format=KMZ"

    data class Coordinate(val lon:Double,val lat:Double)
    data class Region(val label:String,val outer:List<Coordinate>,val holes:List<List<Coordinate>>)
    data class Snapshot(val regions:List<Region>,val fetchedAt:Long,val sourceUpdatedAt:Long?) {
        /** Returns official interval verbatim, never an invented point probability. */
        fun at(lon:Double,lat:Double):String? {
            if(!lon.isFinite() || !lat.isFinite()) return null
            val contained=regions.filter { polygonContains(it.outer,lon,lat) &&
                it.holes.none { hole -> polygonContains(hole,lon,lat) } }
            return contained.maxByOrNull { labelScore(it.label) }?.label
        }
    }
    @Volatile private var lastAttempt=0L
    @Volatile private var cached:Snapshot?=null

    suspend fun read():Snapshot? = withContext(Dispatchers.IO) {
        val now=System.currentTimeMillis()
        if(now-lastAttempt<CACHE_MS) return@withContext cached?.takeIf { now-it.fetchedAt<60L*60_000L }
        lastAttempt=now
        var conn:HttpsURLConnection?=null
        try {
            conn=URL(URL_KMZ).openConnection() as HttpsURLConnection
            conn.connectTimeout=10_000; conn.readTimeout=12_000
            conn.instanceFollowRedirects=false
            conn.setRequestProperty("Accept","application/vnd.google-earth.kmz, application/zip, */*")
            if(conn.responseCode!=200 || conn.contentLengthLong>MAX_DOWNLOAD) return@withContext validCache(now)
            val buf=ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val chunk=ByteArray(8192)
                while(true) {
                    val n=input.read(chunk)
                    if(n<0) break
                    if(buf.size()+n>MAX_DOWNLOAD) return@withContext validCache(now)
                    buf.write(chunk,0,n)
                }
            }
            val regions=parseKmz(buf.toByteArray())
            if(regions.isEmpty()) return@withContext validCache(now)
            // A stale Last-Modified timestamp is not a live probability product.
            val m=conn.lastModified.takeIf { it>0 }
            if(m!=null && now-m>48L*3600_000L) return@withContext validCache(now)
            Snapshot(regions,now,m).also { cached=it }
        } catch (cancel:CancellationException) { throw cancel }
          catch (_:Exception) { validCache(now) }
        finally { conn?.disconnect() }
    }

    private fun validCache(now:Long)=cached?.takeIf { now-it.fetchedAt<60L*60_000L }

    internal fun parseKmz(bytes:ByteArray):List<Region> {
        if(bytes.size>MAX_DOWNLOAD) return emptyList()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry=zip.nextEntry
            while(entry!=null) {
                if(!entry.isDirectory && entry.name.endsWith(".kml",ignoreCase=true) &&
                    entry.size <= MAX_KML) {
                    val buf=ByteArrayOutputStream()
                    val chunk=ByteArray(8192)
                    while(true) {
                        val n=zip.read(chunk)
                        if(n<0) break
                        if(buf.size()+n>MAX_KML) return emptyList()
                        buf.write(chunk,0,n)
                    }
                    return parseKml(buf.toByteArray())
                }
                zip.closeEntry(); entry=zip.nextEntry
            }
        }
        return emptyList()
    }

    internal fun parseKml(xml:ByteArray):List<Region> {
        if(xml.size>MAX_KML) return emptyList()
        val parser=Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
        parser.setInput(ByteArrayInputStream(xml),"UTF-8")
        val result=mutableListOf<Region>()
        var name=""
        var inMark=false
        var inPolygon=false
        var inOuter=false
        var inInner=false
        var outer=emptyList<Coordinate>()
        val holes=mutableListOf<List<Coordinate>>()
        var totalVertices=0
        var evt=parser.eventType
        while(evt!=XmlPullParser.END_DOCUMENT) {
            when(evt) {
                XmlPullParser.START_TAG -> when(parser.name) {
                    "Placemark" -> {inMark=true;name=""}
                    "name" -> if(inMark && !inPolygon) name=parser.nextText().trim().take(48)
                    "Polygon" -> if(inMark) {inPolygon=true;outer=emptyList();holes.clear()}
                    "outerBoundaryIs" -> if(inPolygon) inOuter=true
                    "innerBoundaryIs" -> if(inPolygon) inInner=true
                    "coordinates" -> if(inPolygon) {
                        val coords=parseCoordinates(parser.nextText())
                        totalVertices+=coords.size
                        if(totalVertices>MAX_VERTICES) return emptyList()
                        if(inOuter) outer=coords
                        else if(inInner && coords.size>=3) holes.add(coords)
                    }
                }
                XmlPullParser.END_TAG -> when(parser.name) {
                    "outerBoundaryIs" -> inOuter=false
                    "innerBoundaryIs" -> inInner=false
                    "Polygon" -> {
                        if(outer.size>=3 && verifiedPercentLabel(name)!=null) {
                            result+=Region(verifiedPercentLabel(name)!!,outer,holes.toList())
                            if(result.size>=MAX_POLYGONS) return result
                        }
                        inPolygon=false;outer=emptyList();holes.clear()
                    }
                    "Placemark" -> inMark=false
                }
            }
            evt=parser.next()
        }
        return result
    }

    private fun verifiedPercentLabel(s:String):String? {
        // Official CWA categories: <20%, 20~40%, 40~60%, 60~80%, 80~100%.
        // Never turn "<20%" into the falsely precise "20%".
        val x=s.replace('％','%').replace('～','~').replace('至','~')
        val less=Regex("(?:<|＜|小於|未滿)\\s*([0-9]{1,3})\\s*%").find(x)
        if(less!=null) {
            val high=less.groupValues[1].toIntOrNull() ?: return null
            if(high in 1..100) return "低於${high}%"
        }
        val band=Regex("([0-9]{1,3})\\s*%?\\s*[-~]\\s*([0-9]{1,3})\\s*%").find(x)
        if(band!=null) {
            val low=band.groupValues[1].toIntOrNull() ?: return null
            val high=band.groupValues[2].toIntOrNull() ?: return null
            if(low in 0..99 && high in 1..100 && low<high) return "${low}～${high}%"
        }
        val one=Regex("(?:100|[0-9]{1,2})\\s*%").find(x) ?: return null
        return one.value.replace(" ","")
    }
    private fun labelScore(s:String) = if(s.startsWith("低於")) 0
        else Regex("[0-9]{1,3}").find(s)?.value?.toIntOrNull() ?: -1
    private fun parseCoordinates(text:String):List<Coordinate> {
        val tokens=text.trim().split(Regex("\\s+"))
        if(tokens.size>5000) return emptyList()
        return tokens.mapNotNull { raw ->
            val p=raw.split(',')
            if(p.size<2) null else {
                val lon=p[0].toDoubleOrNull();val lat=p[1].toDoubleOrNull()
                if(lon!=null && lat!=null && lon.isFinite() && lat.isFinite() && lon in 100.0..180.0 && lat in 0.0..55.0)
                    Coordinate(lon,lat) else null
            }
        }
    }
    private fun polygonContains(poly:List<Coordinate>,lon:Double,lat:Double):Boolean {
        if(poly.size<3) return false
        var inside=false
        var j=poly.lastIndex
        for(i in poly.indices) {
            val a=poly[i];val b=poly[j]
            if((a.lat>lat)!=(b.lat>lat) &&
                lon<(b.lon-a.lon)*(lat-a.lat)/(b.lat-a.lat)+a.lon) inside=!inside
            j=i
        }
        return inside
    }
}
