package io.atmos.weather.alerts

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection

/** Complete official W-C0033-002 text, unchanged; county headlines remain W-C0033-001. */
data class CwaBulletin(val title:String,val text:String,val areas:List<String>,val issuedAt:Instant,
    val startsAt:Instant?,val expiresAt:Instant?,val cancelled:Boolean=false) {
    fun active(now:Instant)=!cancelled && startsAt?.let{!now.isBefore(it)}==true &&
        expiresAt?.let{now.isBefore(it)}==true
}
data class CwaBulletinsResult(val bulletins:List<CwaBulletin>,val checkedAt:Instant,
    val failed:Boolean=false,val cached:Boolean=false)

object CwaOfficialPages {
    const val overview=CwaWarningFeed.OFFICIAL_URL
    /** Exact HTTPS authority, not a prefix match such as cwa.gov.tw.attacker.example. */
    fun allowed(raw:String):Boolean=runCatching {
        val u=URI(raw)
        u.scheme=="https" && u.userInfo==null && (u.port==-1||u.port==443) &&
            (u.host=="www.cwa.gov.tw" || u.host=="cwa.gov.tw" || u.host?.endsWith(".cwa.gov.tw")==true)
    }.getOrDefault(false)
    fun forTitle(title:String,notices:List<CwaWarning> = emptyList()):String {
        notices.firstOrNull{allowed(it.link) && (it.title.contains(title)||title.contains(it.title))}?.let{return it.link}
        // W25 is the current official land-wind page; other products retain their official overview.
        return if("強風" in title)"https://www.cwa.gov.tw/V8/C/P/Warning/W25.html" else overview
    }
}

object CwaBulletins {
    private const val source="https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/W-C0033-002?Authorization=rdec-key-123-45678-011121314&format=JSON"
    private val mutex=Mutex()
    private var cached:CwaBulletinsResult?=null
    private fun objects(value:Any?):List<JSONObject> = when(value) {
        is JSONObject->listOf(value)
        is JSONArray->(0 until minOf(value.length(),100)).mapNotNull{value.optJSONObject(it)}
        else->emptyList()
    }
    internal fun time(raw:String):Instant?=runCatching{OffsetDateTime.parse(raw.trim()).toInstant()}.getOrNull()
        ?:runCatching{LocalDateTime.parse(raw.trim().replace(' ','T')).atZone(ZoneId.of("Asia/Taipei")).toInstant()}.getOrNull()
    fun parse(json:String,now:Instant):List<CwaBulletin> {
        val root=JSONObject(json).getJSONObject("cwaopendata")
        require(root.optString("status")=="Actual" && root.optString("scope")=="Public")
        val sent=time(root.optString("sent"))?:error("缺少官方發布時間")
        require(now.epochSecond-sent.epochSecond in -600L..604800L){"官方公告時間無法驗證"}
        return objects(root.opt("dataset")).mapNotNull {d->
            val info=d.optJSONObject("datasetInfo")?:return@mapNotNull null
            val title=info.optString("datasetDescription").trim().take(200)
            val texts=objects(d.optJSONObject("contents")?.opt("content"))
                .filter{it.optString("contentLanguage","zh-TW")=="zh-TW"}
                .map{it.optString("contentText").trim()}.filter{it.isNotEmpty() && it!="null"}
            if(title.isBlank()||texts.isEmpty())return@mapNotNull null
            val issued=time(info.optString("issueTime"))?:sent
            require(issued.epochSecond<=now.epochSecond+600)
            val validity=info.optJSONObject("validTime")
            val start=validity?.let{time(it.optString("startTime"))}
            val end=validity?.let{time(it.optString("endTime"))}
            if(start!=null && end!=null)require(end.isAfter(start))
            val hazards=objects(d.optJSONObject("hazardConditions")?.optJSONObject("hazards")?.opt("hazard"))
            val areas=hazards.flatMap{h->objects(h.optJSONObject("info")?.optJSONObject("affectedAreas")?.opt("location"))}
                .map{it.optString("locationName").trim()}.filter{it.isNotBlank()&&it!="null"}.distinct()
            CwaBulletin(title,texts.joinToString("\n\n").take(100_000),areas,issued,start,end,
                root.optString("msgType").equals("Cancel",true)||listOf("解除","取消").any{it in title})
        }
    }
    suspend fun fetch(force:Boolean=false):CwaBulletinsResult=mutex.withLock {
        val now=Instant.now()
        cached?.takeIf{!force && now.epochSecond-it.checkedAt.epochSecond in 0..300}?.let{return@withLock it}
        try {
            val data=withContext(Dispatchers.IO) {
                val c=URL(source).openConnection() as HttpsURLConnection
                c.connectTimeout=9000;c.readTimeout=12000
                c.setRequestProperty("Accept","application/json")
                try {
                    check(c.responseCode==200 && c.contentLengthLong<=1_000_000)
                    val b=java.io.ByteArrayOutputStream()
                    c.inputStream.use{input->val buf=ByteArray(8192);while(true){
                        currentCoroutineContext().ensureActive();val n=input.read(buf);if(n<0)break
                        check(b.size()+n<=1_000_000);b.write(buf,0,n)
                    }}
                    b.toString("UTF-8")
                }finally{c.disconnect()}
            }
            val parsed=withContext(Dispatchers.Default){parse(data,now)}
            CwaBulletinsResult(parsed,now).also{cached=it}
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){cached?.takeIf{now.epochSecond-it.checkedAt.epochSecond in 0..7200}
            ?.copy(failed=true,cached=true)?:CwaBulletinsResult(emptyList(),now,failed=true)}
    }
}
