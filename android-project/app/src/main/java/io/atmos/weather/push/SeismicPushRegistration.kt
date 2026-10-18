package io.atmos.weather.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.atmos.weather.BuildConfig

/**
 * Spark-compatible, user-opt-in FCM topic transport.
 * A successful topic subscription is NOT evidence that any official EEW source,
 * Cloudflare Worker, or backend publisher has been configured.
 */
object SeismicPushRegistration {
    private const val PREF = "thinn_push_preferences_v22"
    private const val TOPIC = "thinn_eew_tw_v1"

    fun optedIn(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun configured(): Boolean = listOf(
        BuildConfig.THINN_FIREBASE_APP_ID,
        BuildConfig.THINN_FIREBASE_API_KEY,
        BuildConfig.THINN_FIREBASE_SENDER_ID,
        BuildConfig.THINN_FIREBASE_PROJECT_ID
    ).all { it.isNotBlank() }

    fun init(ctx: Context): Boolean {
        if (!configured()) return false
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            val opts = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.THINN_FIREBASE_APP_ID)
                .setApiKey(BuildConfig.THINN_FIREBASE_API_KEY)
                .setGcmSenderId(BuildConfig.THINN_FIREBASE_SENDER_ID)
                .setProjectId(BuildConfig.THINN_FIREBASE_PROJECT_ID)
                .build()
            FirebaseApp.initializeApp(ctx, opts)
        }
        return true
    }

    fun setEnabled(ctx: Context, enabled: Boolean, onResult: (String) -> Unit) {
        if (enabled && !init(ctx)) {
            onResult("FCM 專案設定不足，尚未訂閱")
            return
        }
        // Never route the FCM registration token to a public endpoint.
        val pref = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (enabled) {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
                .addOnSuccessListener {
                    pref.edit().putBoolean("enabled", true).apply()
                    onResult("已訂閱台灣與離島地震通知主題；後端來源尚須獨立驗證")
                }
                .addOnFailureListener {
                    // Fail closed on initial subscription failure. The remote service is
                    // not marked as operational merely because the toggle was pressed.
                    pref.edit().putBoolean("enabled", false).apply()
                    onResult("FCM 主題訂閱失敗：${it.message.orEmpty()}")
                }
        } else {
            pref.edit().putBoolean("enabled", false).apply()
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC)
                .addOnSuccessListener { onResult("已停用台灣與離島地震主題通知") }
                .addOnFailureListener {
                    // Incoming messages are also blocked locally by optedIn().
                    onResult("本機已停用；雲端取消訂閱待重試：${it.message.orEmpty()}")
                }
        }
    }

    /** Called after an FCM token refresh. Firebase automatically persists topics,
     * but re-subscribing makes our consent intent explicit and self-healing.
     */
    fun register(ctx: Context, token: String, onResult: (String) -> Unit = {}) {
        if (!optedIn(ctx)) return
        if (!init(ctx)) { onResult("FCM 設定不完整"); return }
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
            .addOnSuccessListener { onResult("FCM 主題訂閱維持中") }
            .addOnFailureListener { onResult("FCM 重新訂閱失敗：${it.message.orEmpty()}") }
    }

    /** This preference does not filter FCM topic deliveries; validated county impact
     * remains the publisher's responsibility. The app also applies local freshness policy.
     */
    fun setRegion(ctx: Context, region: String, onResult: (String) -> Unit = {}) {
        val safe = if (region in setOf(
                "TAIWAN", "TAIPEI", "NEW_TAIPEI", "TAOYUAN", "TAICHUNG", "TAINAN",
                "KAOHSIUNG", "PENGHU", "KINMEN", "LIENCHIANG", "HUALIEN", "TAITUNG"
            )) region else "TAIWAN"
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("region", safe).apply()
        onResult("已記錄地區偏好；主題推播涵蓋台灣及離島，並非分區個人化預警")
    }
}
