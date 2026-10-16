package io.atmos.weather.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import io.atmos.weather.BuildConfig

/** Consent, anonymous Firebase identity and authenticated callable Functions. */
object SeismicPushRegistration {
    private const val PREF="thinn_push_preferences_v22"
    fun optedIn(ctx:Context)=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("enabled",false)
    fun configured():Boolean = listOf(BuildConfig.THINN_FIREBASE_APP_ID,BuildConfig.THINN_FIREBASE_API_KEY,
        BuildConfig.THINN_FIREBASE_SENDER_ID,BuildConfig.THINN_FIREBASE_PROJECT_ID).all { it.isNotBlank() }
    fun init(ctx:Context):Boolean {
        if(!configured()) return false
        if(FirebaseApp.getApps(ctx).isEmpty()) {
            val opts=FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.THINN_FIREBASE_APP_ID)
                .setApiKey(BuildConfig.THINN_FIREBASE_API_KEY)
                .setGcmSenderId(BuildConfig.THINN_FIREBASE_SENDER_ID)
                .setProjectId(BuildConfig.THINN_FIREBASE_PROJECT_ID).build()
            FirebaseApp.initializeApp(ctx,opts)
        }
        return true
    }
    fun setEnabled(ctx:Context,enabled:Boolean,onResult:(String)->Unit) {
        if(enabled && !init(ctx)) {onResult("尚未設定 Firebase 專案；不能啟用遠端地震推播"); return}
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("enabled",enabled).apply()
        if(enabled) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { register(ctx,it,onResult) }
                .addOnFailureListener {onResult("推播註冊失敗：${it.message.orEmpty()}")}
        } else {
            // Delete server record first; token deletion follows as a best effort.
            runCatching { FirebaseFunctions.getInstance(region()).getHttpsCallable("unregisterDevice").call() }
            FirebaseMessaging.getInstance().deleteToken()
            onResult("已停用遠端推播（裝置通知權限不受影響）")
        }
    }
    fun register(ctx:Context,token:String,onResult:(String)->Unit={}) {
        if(!init(ctx)) {onResult("Firebase 設定不完整");return}
        val auth=FirebaseAuth.getInstance()
        val done:()->Unit={
            val region=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("region","TAIWAN") ?: "TAIWAN"
            FirebaseFunctions.getInstance(region()).getHttpsCallable("registerDevice")
                .call(mapOf("token" to token,"region" to region,"locale" to "zh-TW"))
                .addOnSuccessListener {onResult("遠端地震通知已登記")}
                .addOnFailureListener {onResult("遠端登記失敗：${it.message.orEmpty()}")}
        }
        if(auth.currentUser!=null) done() else auth.signInAnonymously()
            .addOnSuccessListener {done()}.addOnFailureListener {onResult("匿名身分驗證失敗")}
    }
    fun setRegion(ctx:Context,region:String,onResult:(String)->Unit={}) {
        val safe=if(region in setOf("TAIWAN","TAIPEI","NEW_TAIPEI","TAOYUAN","TAICHUNG","TAINAN","KAOHSIUNG","PENGHU","KINMEN","LIENCHIANG","HUALIEN","TAITUNG")) region else "TAIWAN"
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("region",safe).apply()
        if(optedIn(ctx)) FirebaseMessaging.getInstance().token.addOnSuccessListener {register(ctx,it,onResult)}
    }
    private fun region()=BuildConfig.THINN_PUSH_FUNCTIONS_REGION.ifBlank {"asia-east1"}
}
