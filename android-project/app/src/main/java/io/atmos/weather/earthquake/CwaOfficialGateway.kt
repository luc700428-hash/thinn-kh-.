package io.atmos.weather.earthquake

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import io.atmos.weather.BuildConfig
import io.atmos.weather.push.SeismicPushRegistration
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Authenticated callable Firebase proxy. The shared CWA API key never enters the APK. */
object CwaOfficialGateway {
    private val datasets=setOf("E-A0015-001","E-A0016-001",
        "W-C0034-001","W-C0034-002","W-C0034-003","W-C0034-005")

    suspend fun read(context:Context,dataset:String):JSONObject {
        require(dataset in datasets) { "不支援的中央氣象署資料集" }
        if(dataset=="W-C0034-005") {
            try { return readProxy(context,dataset) }
            catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
            catch(_:Exception) { return readPublicTyphoon() }
        }
        return readProxy(context,dataset)
    }
    private suspend fun readProxy(context:Context,dataset:String):JSONObject {
        check(SeismicPushRegistration.init(context)) { "尚未設定 Firebase 後端，無法使用官方 API 代理" }
        val auth=FirebaseAuth.getInstance()
        if(auth.currentUser==null) suspendCancellableCoroutine<Unit> { cont ->
            auth.signInAnonymously().addOnSuccessListener {
                if(cont.isActive) cont.resume(Unit)
            }.addOnFailureListener { if(cont.isActive) cont.resumeWithException(it) }
        }
        val region=BuildConfig.THINN_PUSH_FUNCTIONS_REGION.ifBlank { "asia-east1" }
        val response=suspendCancellableCoroutine<Any?> { cont ->
            FirebaseFunctions.getInstance(region).getHttpsCallable("readOfficialCwa")
                .call(mapOf("dataset" to dataset))
                .addOnSuccessListener { if(cont.isActive) cont.resume(it.data) }
                .addOnFailureListener { if(cont.isActive) cont.resumeWithException(it) }
        }
        val data=response as? Map<*,*> ?: error("中央氣象署資料代理回覆格式錯誤")
        check(data["dataset"]==dataset && data["source"]=="CWA_OFFICIAL_POSTED_DATA") {
            "官方資料來源驗證失敗"
        }
        val raw=data["payload"] as? String ?: error("官方資料內容不存在")
        return JSONObject(raw)
    }
    /** Public trial endpoint for CWA typhoon tracks only. */
    private suspend fun readPublicTyphoon():JSONObject = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url="https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/W-C0034-005?Authorization=rdec-key-123-45678-011121314&format=JSON"
        val c=java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
        c.connectTimeout=10_000
        c.readTimeout=15_000
        c.instanceFollowRedirects=true
        c.setRequestProperty("Accept","application/json")
        try {
            check(c.responseCode==200) { "中央氣象署公開資料 HTTP ${c.responseCode}" }
            val limit=1_500_000
            check(c.contentLengthLong<0 || c.contentLengthLong<=limit) { "颱風資料過大" }
            val buffer=java.io.ByteArrayOutputStream()
            c.inputStream.use { input ->
                val bytes=ByteArray(8192)
                while(true) {
                    val n=input.read(bytes)
                    if(n<0) break
                    check(buffer.size()+n<=limit) { "颱風資料過大" }
                    buffer.write(bytes,0,n)
                }
            }
            val envelope=JSONObject(buffer.toString("UTF-8")).optJSONObject("cwaopendata")
                ?: error("中央氣象署回應格式錯誤")
            val sent=io.atmos.weather.typhoon.VerifiedTrackPlanner.parseTime(envelope.optString("Sent"))
                ?: error("中央氣象署資料缺少發布時間")
            val age=java.time.Instant.now().epochSecond-sent.epochSecond
            check(age in -600L..(36L*3600L)) { "中央氣象署資料已過期" }
            val dataset=envelope.optJSONObject("Dataset") ?: envelope.optJSONObject("dataset")
                ?: error("中央氣象署資料集不存在")
            val group=dataset.optJSONObject("TropicalCyclones")
                ?: error("中央氣象署熱帶氣旋資料不存在")
            JSONObject().put("records",JSONObject().put("TropicalCyclones",group))
        } finally { c.disconnect() }
    }
}
