package io.atmos.weather.typhoon

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** CWA W-C0034-003: official 7-BF wind-circle strike-probability KMZ.
 *  No track-derived probabilities, no 0% for missing coverage.
 *  Requires deployed authenticated Firebase KMZ proxy and CWA secret; returns unavailable otherwise.
 */
object CwaWindProbability {
    private const val MAX_DOWNLOAD=6_000_000
    private const val MAX_KML=8_000_000
    private const val MAX_POLYGONS=1000
    private const val MAX_VERTICES=80_000
    private const val CACHE_MS=20L*60_000L
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
    enum class FetchState { NOT_CHECKED, CURRENT, CACHED, UNAVAILABLE, NO_USABLE_REGIONS, STALE }
    @Volatile var fetchState:FetchState=FetchState.NOT_CHECKED
        private set
    @Volatile private var lastAttempt=0L
    @Volatile private var cached:Snapshot?=null
    private fun fallback(now:Long,reason:FetchState):Snapshot? {
        val previous=validCache(now)
        // A cached snapshot older than the usable cache window must never be
        // presented as a fresh official probability update after network failure.
        val last=cached
        fetchState=when {
            previous!=null -> FetchState.CACHED
            last!=null && now-last.fetchedAt >= 60L*60_000L -> FetchState.STALE
            else -> reason
        }
        return previous
    }

    /** Server-authenticated CWA KMZ bytes; no production key is stored in the APK. */
    suspend fun read(context:android.content.Context):Snapshot? = withContext(Dispatchers.IO) {
        val now=System.currentTimeMillis()
        if(now-lastAttempt<CACHE_MS) return@withContext validCache(now)
        lastAttempt=now
        try {
            val bytes=io.atmos.weather.earthquake.CwaOfficialGateway.readTyphoonProbabilityKmz(context)
            val regions=parseKmz(bytes)
            if(regions.isEmpty()) return@withContext fallback(now,FetchState.NO_USABLE_REGIONS)
            Snapshot(regions,now,null).also { cached=it; fetchState=FetchState.CURRENT }
        } catch (cancel:CancellationException) { throw cancel }
          catch (_:Exception) { fallback(now,FetchState.UNAVAILABLE) }
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
