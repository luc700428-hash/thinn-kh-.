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
        // Always use our authenticated gateway. CWA requires a valid member API key;
        // a sample/embedded key in a public APK is not a reliable or safe fallback.
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
        val json=JSONObject(raw)
        // The W-C0034-005 FILE API returns cwaopendata.Dataset.TropicalCyclones;
        // the rest of the app expects records.TropicalCyclones. Normalize *only*
        // verified official payloads and never manufacture a storm, fix or warning.
        if(dataset=="W-C0034-005") {
            if(json.optJSONObject("records")?.optJSONObject("TropicalCyclones") != null) return json
            val envelope=json.optJSONObject("cwaopendata")
                ?: error("中央氣象署熱帶氣旋回應缺少 cwaopendata")
            val issued=io.atmos.weather.typhoon.VerifiedTrackPlanner.parseTime(envelope.optString("Sent"))
                ?: error("中央氣象署熱帶氣旋資料缺少有效發布時間")
            val age=java.time.Instant.now().epochSecond-issued.epochSecond
            check(age in -600L..(36L*3600L)) { "中央氣象署熱帶氣旋資料過期" }
            val datasetNode=envelope.optJSONObject("Dataset") ?: envelope.optJSONObject("dataset")
                ?: error("中央氣象署熱帶氣旋資料結構不符")
            val cyclones=datasetNode.optJSONObject("TropicalCyclones")
                ?: error("中央氣象署熱帶氣旋欄位不存在")
            return JSONObject().put("records",JSONObject().put("TropicalCyclones",cyclones))
        }
        return json
    }
    /** User-facing operational diagnosis. Never show CWA key, Firebase credentials or stack traces. */
    fun describeFailure(error: Exception): String = when {
        !SeismicPushRegistration.configured() -> "官方資料代理尚未設定；需要檢查 Firebase 專案設定。"
        error is com.google.firebase.functions.FirebaseFunctionsException -> when(error.code) {
            com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND ->
                "官方資料代理服務尚未部署，請檢查 readOfficialCwa 與 Functions 區域。"
            com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "官方資料登入驗證失敗，請檢查 Firebase 驗證服務。"
            com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                "官方資料查詢額度不足，請稍後重試。"
            else -> "官方資料服務暫時無法使用，請稍後重試。"
        }
        error is java.io.IOException -> "網路連線失敗，請確認網路後重試。"
        else -> "官方資料格式或服務異常，請稍後重試。"
    }
}
