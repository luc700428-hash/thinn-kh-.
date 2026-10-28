package io.atmos.weather.typhoon

import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlin.math.*

/** Official CWA 120h city/reference-point table. An explicitly published zero is valid. */
object CwaWindCityTable {
    const val INDEX_URL="https://www.cwa.gov.tw/Data/js/typhoon/TY_NEWS-Data.js"
    data class Reference(val key:String,val name:String,val lon:Double,val lat:Double)
    val references=listOf(
        Reference("Keelung","基隆市",121.74,25.13),Reference("TaipeiCity","臺北市",121.56,25.03),
        Reference("NewTaipei","新北市",121.46,25.01),Reference("Taoyuan","桃園市",121.30,24.99),
        Reference("HsinchuCity","新竹市",120.97,24.81),Reference("HsinchuCounty","新竹縣",121.01,24.84),
        Reference("Miaoli","苗栗縣",120.82,24.56),Reference("Taichung","臺中市",120.68,24.15),
        Reference("Changhua","彰化縣",120.54,24.07),Reference("Nantou","南投縣",120.69,23.91),
        Reference("Yunlin","雲林縣",120.54,23.71),Reference("ChiayiCity","嘉義市",120.45,23.48),
        Reference("ChiayiCounty","嘉義縣",120.29,23.46),Reference("Tainan","臺南市",120.21,22.99),
        Reference("Kaohsiung","高雄市",120.30,22.63),Reference("Pingtung","屏東縣",120.49,22.67),
        Reference("Hengchun","恆春",120.74,22.00),Reference("Yilan","宜蘭縣",121.75,24.76),
        Reference("Hualien","花蓮縣",121.61,23.99),Reference("Taitung","臺東縣",121.15,22.75),
        Reference("Lanyu","蘭嶼",121.55,22.04),Reference("Ludao","綠島",121.49,22.67),
        Reference("LienChiang","連江縣",119.95,26.16),Reference("Kinmen","金門縣",118.32,24.43),
        Reference("Penghu","澎湖縣",119.57,23.57))
    data class Snapshot(val issuedAt:Long,val percentages:Map<String,Int>,val url:String) {
        fun reference(lon:Double,lat:Double,place:String?=null):Reference? {
            if(!lon.isFinite()||!lat.isFinite()||lon !in 117.0..123.8||lat !in 21.0..27.0)return null
            val text=place.orEmpty().replace("台","臺")
            val specific=references.firstOrNull{it.key in setOf("Lanyu","Ludao","Hengchun")&&text.contains(it.name)}
            val named=specific?:references.firstOrNull {text.contains(it.name)}?:references.firstOrNull {
                it.name in listOf("連江縣","澎湖縣","金門縣")&&text.contains(if(it.key=="LienChiang")"馬祖" else it.name.dropLast(1))}
            if(named!=null&&percentages.containsKey(named.key))return named
            return references.filter {percentages.containsKey(it.key)}.minByOrNull {distance(lon,lat,it.lon,it.lat)}
                ?.takeIf {distance(lon,lat,it.lon,it.lat)<=20.0}
        }
        fun value(lon:Double,lat:Double,place:String?=null):String?=reference(lon,lat,place)?.let {percentages[it.key]}?.let {"$it%"}
    }
    internal data class Index(val path:String,val stamp:String,val issuedAt:Long) {
        val url get()="https://www.cwa.gov.tw/Data/${path}WSP-AREA_${stamp}_WHOLE-DURATION.json"
    }
    internal fun parseIndex(script:String,now:Long):Index? {
        if(script.length>300_000)return null
        val path=Regex("var\\s+DataPath\\s*=\\s*['\"](typhoon/[A-Za-z0-9_/-]{1,80}/)['\"]").find(script)?.groupValues?.get(1)?:return null
        if(path.contains(".."))return null
        val stamp=Regex("var\\s+TY_DataTime\\s*=\\s*['\"]([0-9]{12})['\"]").find(script)?.groupValues?.get(1)?:return null
        val at=runCatching{LocalDateTime.parse(stamp,DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toInstant(ZoneOffset.UTC).toEpochMilli()}.getOrNull()?:return null
        if(now-at !in -600_000L..36*3_600_000L)return null
        return Index(path,stamp,at)
    }
    internal fun parse(json:String,index:Index):Snapshot? {
        val o=JSONObject(json);if(o.optInt("DURING_THE_HOURS")!=120)return null
        val area=o.optJSONObject("AREA")?:return null
        val values=references.mapNotNull {r ->val raw=area.opt(r.key)
            (raw as? Number)?.toDouble()?.takeIf {it.isFinite()&&it in 0.0..100.0&&it==floor(it)}?.toInt()?.let {r.key to it}
        }.toMap()
        return values.takeIf{it.isNotEmpty()}?.let {Snapshot(index.issuedAt,it,index.url)}
    }
    internal fun read(now:Long=System.currentTimeMillis()):Snapshot? {
        val index=parseIndex(download(INDEX_URL,300_000),now)?:return null
        return parse(download(index.url,100_000),index)
    }
    private fun download(url:String,limit:Int):String {
        val c=URL(url).openConnection() as HttpsURLConnection
        c.connectTimeout=6500;c.readTimeout=9000;c.instanceFollowRedirects=false
        c.setRequestProperty("User-Agent","thinn-khi/25 (CWA typhoon probability)")
        try {
            check(c.responseCode==200&&c.contentLengthLong<=limit)
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use {input->val buffer=ByteArray(8192);while(true) {
                val n=input.read(buffer);if(n<0)break;check(out.size()+n<=limit);out.write(buffer,0,n)
            }}
            return out.toString("UTF-8")
        } finally{c.disconnect()}
    }
    private fun distance(lon:Double,lat:Double,x:Double,y:Double):Double {
        val dy=Math.toRadians(y-lat);val dx=Math.toRadians(x-lon)
        val a=sin(dy/2).pow(2)+cos(Math.toRadians(lat))*cos(Math.toRadians(y))*sin(dx/2).pow(2)
        return 12742*asin(sqrt(a.coerceIn(0.0,1.0)))
    }
}
