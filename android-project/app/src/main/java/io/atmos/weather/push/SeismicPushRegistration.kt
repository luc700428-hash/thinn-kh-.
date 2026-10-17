
package io.atmos.weather.push

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import io.atmos.weather.BuildConfig
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/**
 * thinn-khì Cloudflare Worker + Firebase FCM integration.
 * No server credentials or private keys are stored in the APK.
 * Registration remains closed while the Worker safety gates are off.
 */
object SeismicPushRegistration {
    private const val PREF = "thinn_push_preferences_v22"
    private const val REGISTERED = "遠端地震通知已登記"

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val regions = setOf(
        "TAIWAN", "TAIPEI", "NEW_TAIPEI", "TAOYUAN",
        "TAICHUNG", "TAINAN", "KAOHSIUNG", "KEELUNG",
        "HSINCHU", "CHIAYI", "MIAOLI", "CHANGHUA",
        "NANTOU", "YUNLIN", "PINGTUNG", "YILAN",
        "HUALIEN", "TAITUNG", "PENGHU", "KINMEN",
        "LIENCHIANG"
    )

    private data class Reply(
        val status: Int,
        val error: String = ""
    )

    fun optedIn(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean("enabled", false)

    fun configured(): Boolean =
        listOf(
            BuildConfig.THINN_FIREBASE_APP_ID,
            BuildConfig.THINN_FIREBASE_API_KEY,
            BuildConfig.THINN_FIREBASE_SENDER_ID,
            BuildConfig.THINN_FIREBASE_PROJECT_ID
        ).all { it.isNotBlank() } &&
        BuildConfig.THINN_PUSH_WORKER_URL.trim()
            .startsWith("https://")

    fun init(ctx: Context): Boolean {
        if (!configured()) return false

        if (FirebaseApp.getApps(ctx).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.THINN_FIREBASE_APP_ID)
                .setApiKey(BuildConfig.THINN_FIREBASE_API_KEY)
                .setGcmSenderId(BuildConfig.THINN_FIREBASE_SENDER_ID)
                .setProjectId(BuildConfig.THINN_FIREBASE_PROJECT_ID)
                .build()

            FirebaseApp.initializeApp(ctx, options)
        }
        return true
    }

    private fun emit(
        callback: (String) -> Unit,
        message: String
    ) {
        main.post { callback(message) }
    }

    private fun withIdentity(
        ctx: Context,
        onToken: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!init(ctx)) {
            onError("Firebase 或 Worker 設定不完整")
            return
        }

        val auth = FirebaseAuth.getInstance()

        fun fetchToken() {
            val user = auth.currentUser

            if (user == null) {
                onError("Firebase 使用者不存在")
                return
            }

            user.getIdToken(false)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) {
                        onError("無法取得 Firebase 身分憑證")
                    } else {
                        onToken(token)
                    }
                }
                .addOnFailureListener {
                    onError("Firebase 身分憑證取得失敗")
                }
        }

        if (auth.currentUser != null) {
            fetchToken()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { fetchToken() }
                .addOnFailureListener {
                    onError("Firebase 匿名身分驗證失敗")
                }
        }
    }

    private fun request(
        path: String,
        idToken: String,
        data: JSONObject,
        callback: (Reply) -> Unit
    ) {
        val base = BuildConfig.THINN_PUSH_WORKER_URL
            .trim().trimEnd('/')

        io.execute {
            val result = try {
                val connection = URL(base + path)
                    .openConnection() as HttpsURLConnection

                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Authorization",
                        "Bearer $idToken"
                    )
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )
                    connection.setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    connection.outputStream.use { stream ->
                        stream.write(
                            data.toString().toByteArray(Charsets.UTF_8)
                        )
                    }

                    val status = connection.responseCode
                    val stream = if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                    val responseBody = stream
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText().take(4096) }
                        .orEmpty()

                    val error = runCatching {
                        JSONObject(responseBody)
                            .optString("error")
                    }.getOrDefault("")

                    Reply(status, error)
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                Reply(0, "network_unavailable")
            }

            main.post { callback(result) }
        }
    }

    private fun registerMessage(reply: Reply): String =
        when {
            reply.status == 200 -> REGISTERED

            reply.error == "enrollment_not_enabled" ->
                "推播後端尚未開放註冊（安全測試階段）"

            reply.error == "invalid_firebase_id_token" ->
                "Firebase 身分驗證失敗，請稍後重試"

            reply.error == "pilot_capacity_reached" ->
                "推播測試名額已滿"

            reply.status == 429 ->
                "操作太頻繁，請稍後重試"

            reply.status == 400 ->
                "推播裝置資料格式不正確"

            reply.status == 0 ->
                "無法連線推播伺服器，請檢查網路"

            else ->
                "推播註冊失敗（HTTP ${reply.status}）"
        }

    fun register(
        ctx: Context,
        token: String,
        onResult: (String) -> Unit = {}
    ) {
        if (!init(ctx)) {
            emit(onResult, "Firebase 或 Worker 設定不完整")
            return
        }

        val region = ctx.getSharedPreferences(
            PREF, Context.MODE_PRIVATE
        ).getString("region", "TAIWAN")
            ?.takeIf { it in regions } ?: "TAIWAN"

        withIdentity(
            ctx,
            onToken = { idToken ->
                val body = JSONObject()
                    .put("token", token)
                    .put("region", region)

                request(
                    "/v1/register",
                    idToken,
                    body
                ) { reply ->
                    onResult(registerMessage(reply))
                }
            },
            onError = { error ->
                emit(onResult, error)
            }
        )
    }

    fun setEnabled(
        ctx: Context,
        enabled: Boolean,
        onResult: (String) -> Unit
    ) {
        val prefs = ctx.getSharedPreferences(
            PREF, Context.MODE_PRIVATE
        )

        if (enabled) {
            if (!init(ctx)) {
                emit(onResult, "Firebase 或 Worker 設定不完整")
                return
            }

            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    register(ctx, token) { message ->
                        val success = message == REGISTERED

                        prefs.edit()
                            .putBoolean("enabled", success)
                            .apply()

                        onResult(message)
                    }
                }
                .addOnFailureListener {
                    prefs.edit()
                        .putBoolean("enabled", false)
                        .apply()
                    emit(onResult, "FCM 裝置憑證取得失敗")
                }

            return
        }

        prefs.edit()
            .putBoolean("enabled", false)
            .apply()

        if (!init(ctx)) {
            emit(
                onResult,
                "已停用本機推播；伺服器註冊狀態尚未確認"
            )
            return
        }

        withIdentity(
            ctx,
            onToken = { idToken ->
                request(
                    "/v1/unregister",
                    idToken,
                    JSONObject()
                ) { reply ->
                    FirebaseMessaging.getInstance()
                        .deleteToken()

                    if (reply.status == 200) {
                        onResult("已停用遠端地震推播")
                    } else {
                        onResult(
                            "本機已停用；伺服器取消註冊未確認"
                        )
                    }
                }
            },
            onError = {
                FirebaseMessaging.getInstance().deleteToken()
                emit(
                    onResult,
                    "本機已停用；伺服器取消註冊未確認"
                )
            }
        )
    }

    fun setRegion(
        ctx: Context,
        region: String,
        onResult: (String) -> Unit = {}
    ) {
        val safe = region.takeIf {
            it in regions
        } ?: "TAIWAN"

        ctx.getSharedPreferences(
            PREF, Context.MODE_PRIVATE
        ).edit()
            .putString("region", safe)
            .apply()

        if (optedIn(ctx) && init(ctx)) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener {
                    register(ctx, it, onResult)
                }
                .addOnFailureListener {
                    emit(onResult, "FCM 裝置憑證取得失敗")
                }
        }
    }
}
