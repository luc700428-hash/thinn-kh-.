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
}
