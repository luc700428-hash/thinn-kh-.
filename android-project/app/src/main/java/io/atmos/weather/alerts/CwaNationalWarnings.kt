package io.atmos.weather.alerts

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

data class CwaNationalWarnings(val hazards:List<CwaCountyHazard>,val checkedAt:Instant,
    val failed:Boolean=false,val cached:Boolean=false,val sourceAt:Instant=checkedAt)

/** The complete 22-county feed, not the current-location subset shown on the home page. */
object CwaNationalWarningFeed {
    private const val SOURCE="https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/W-C0033-001?Authorization=rdec-key-123-45678-011121314&format=JSON"
    private val mutex=Mutex()
    private var cache:CwaNationalWarnings?=null
    fun parse(json:String,now:Instant):List<CwaCountyHazard> {
        val document=JSONObject(json);val root=document.getJSONObject("cwaopendata")
        require(root.optString("status")=="Actual"&&root.optString("scope")=="Public")
        val sent=CwaBulletins.time(root.getString("sent"))?:error("缺少官方發布時間")
        require(now.epochSecond-sent.epochSecond in -600L..604800L)
        val locations=root.getJSONObject("dataset").getJSONArray("location")
        val names=(0 until locations.length()).map{locations.getJSONObject(it).getString("locationName")}
        require(names.map(CwaWarningGeography::county).toSet()==CwaWarningGeography.counties.toSet()) {"全國縣市警特報資料不完整"}
        require(names.size==22)
        if(root.optString("msgType").equals("Cancel",true))return emptyList()
        val result=mutableListOf<CwaCountyHazard>()
        for(i in 0 until locations.length()) {
            val location=locations.getJSONObject(i)
            require(location.has("hazardConditions")){"缺少縣市警特報狀態"}
            val conditions=location.opt("hazardConditions")
            if(conditions==null||conditions==JSONObject.NULL)continue
            require(conditions is JSONObject)
            val raw=conditions.get("hazards")
            val hazards=when(raw) {
                is JSONObject->listOf(raw)
                is JSONArray->{require(raw.length()<=100);(0 until raw.length()).map{raw.getJSONObject(it)}}
                else->error("縣市警特報格式不完整")
            }
            for(hazard in hazards) {
                val info=hazard.getJSONObject("info");val validity=hazard.getJSONObject("validTime")
                val phenomenon=info.getString("phenomena").trim();val significance=info.getString("significance").trim()
                require(phenomenon.isNotBlank()&&significance.isNotBlank())
                val start=CwaBulletins.time(validity.getString("startTime"))?:error("警特報開始時間無法驗證")
                val end=CwaBulletins.time(validity.getString("endTime"))?:error("警特報結束時間無法驗證")
                require(end.isAfter(start))
                if(now.isBefore(start)||!now.isBefore(end)||listOf("解除","取消","測試","演練").any{it in phenomenon||it in significance})continue
                // This nationwide view displays official scopes; it does not emit CAP alerts.
                result+=CwaCountyHazard(phenomenon+significance,phenomenon,names[i],sent,end)
            }
        }
        return result.distinctBy{it.title to it.county}
    }
    suspend fun fetch(force:Boolean=false):CwaNationalWarnings=mutex.withLock {
        val now=Instant.now()
        cache?.takeIf{!force&&now.epochSecond-it.checkedAt.epochSecond in 0..300}?.let{return@withLock it}
        try {
            val text=withContext(Dispatchers.IO) {
                val connection=URL(SOURCE).openConnection() as HttpsURLConnection
                connection.connectTimeout=9000;connection.readTimeout=12000;connection.instanceFollowRedirects=false
                connection.setRequestProperty("Accept","application/json")
                try {
                    check(connection.responseCode==200&&connection.contentLengthLong<=512_000)
                    val bytes=java.io.ByteArrayOutputStream()
                    connection.inputStream.use{input->val chunk=ByteArray(8192);while(true){
                        currentCoroutineContext().ensureActive();val n=input.read(chunk);if(n<0)break
                        check(bytes.size()+n<=512_000);bytes.write(chunk,0,n)
                    }}
                    bytes.toString("UTF-8")
                }finally{connection.disconnect()}
            }
            val hazards=withContext(Dispatchers.Default){parse(text,now)}
            val sent=CwaBulletins.time(JSONObject(text).getJSONObject("cwaopendata").getString("sent"))?:error("缺少官方發布時間")
            CwaNationalWarnings(hazards,now,sourceAt=sent).also{cache=it}
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){cache?.takeIf{now.epochSecond-it.checkedAt.epochSecond in 0..7200}
            ?.copy(failed=true,cached=true)?:CwaNationalWarnings(emptyList(),now,failed=true)}
    }
}

internal object CwaWarningGeography {
    val counties=listOf("基隆市","臺北市","新北市","桃園市","新竹市","新竹縣","苗栗縣","臺中市",
        "彰化縣","南投縣","雲林縣","嘉義市","嘉義縣","臺南市","高雄市","屏東縣","宜蘭縣","花蓮縣",
        "臺東縣","澎湖縣","金門縣","連江縣")
    fun county(raw:String)=raw.trim().replace('台','臺')
    fun resolve(raw:String):Set<String> {
        val normalized=county(raw)
        if(normalized=="新竹縣市")return setOf("新竹縣","新竹市")
        if(normalized=="嘉義縣市")return setOf("嘉義縣","嘉義市")
        counties.filter{it in normalized}.takeIf{it.isNotEmpty()}?.let{return it.toSet()}
        // Only official, unambiguous named regional aliases; broader prose is left as text.
        return when(normalized) {
            "恆春半島"->setOf("屏東縣")
            "蘭嶼綠島","蘭嶼","綠島"->setOf("臺東縣")
            "基隆北海岸"->setOf("基隆市","新北市")
            else->emptySet()
        }
    }
}

internal data class CwaWarningMapState(val titles:Map<String,List<String>>,val complete:Boolean,
    val checkedAt:Instant?,val cached:Boolean,val expiredSelection:Boolean,val unresolved:List<String>,
    val sourceAt:Instant?=checkedAt,val sourceMismatch:Boolean=false) {
    companion object {
        fun build(national:CwaNationalWarnings?,bulletins:List<CwaBulletin>,local:List<CwaCountyHazard>,selected:String?,now:Instant):CwaWarningMapState {
            val relevant=bulletins.filter{selected==null||selected.contains(it.title)||it.title.contains(selected)}
            val expired=selected!=null&&relevant.isNotEmpty()&&relevant.none{it.active(now)}
            val mapped=linkedMapOf<String,MutableList<String>>()
            fun add(county:String,title:String){mapped.getOrPut(CwaWarningGeography.county(county)){mutableListOf()}.add(title)}
            val age=national?.let{now.epochSecond-it.checkedAt.epochSecond}?:Long.MAX_VALUE
            var complete=national!=null&&!national.failed&&age in 0..600
            val usable=national!=null&&age in 0..7200&&(!national.failed||national.cached)
            var mismatch=false
            val newer=relevant.filter{national!=null&&it.updatedAt.isAfter(national.sourceAt)}
            val cancellations=newer.filter{it.cancelled}
            if(!expired) {
                if(usable)national!!.hazards.filter{now.isBefore(it.expiresAt)&&(selected==null||selected.contains(it.phenomenon)||it.title.contains(selected))}
                    .forEach{h->if(cancellations.any{h.phenomenon in it.title})mismatch=true else add(h.county,h.title)}
                else {
                    local.filter{now.isBefore(it.expiresAt)&&(selected==null||selected.contains(it.phenomenon))}.forEach{add(it.county,it.title)}
                    relevant.filter{it.active(now)}.forEach{b->b.areas.flatMap{CwaWarningGeography.resolve(it)}.distinct().forEach{add(it,b.title)}}
                }
                if(usable)newer.filter{it.active(now)}.forEach{b->
                    val counties=b.areas.flatMap{CwaWarningGeography.resolve(it)}.distinct()
                    counties.forEach{county->if(mapped[county].orEmpty().none{title->title==b.title||title.removeSuffix("特報").removeSuffix("警報") in b.title}) {
                        add(county,b.title);mismatch=true
                    }}
                    if(counties.isEmpty()&&b.areas.isNotEmpty())mismatch=true
                }
            }
            if(mismatch)complete=false
            val unresolved=if(usable&&!mismatch)emptyList() else relevant.filter{it.active(now)}.flatMap{it.areas}.filter{CwaWarningGeography.resolve(it).isEmpty()}.distinct()
            return CwaWarningMapState(mapped.mapValues{it.value.distinct()},complete,national?.checkedAt,national?.cached==true||(usable&&!complete&&!mismatch),expired,unresolved,national?.sourceAt,mismatch)
        }
    }
}
