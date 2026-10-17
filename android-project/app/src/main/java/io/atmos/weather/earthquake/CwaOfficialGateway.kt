
package io.atmos.weather.earthquake

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import io.atmos.weather.BuildConfig
import io.atmos.weather.push.SeismicPushRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Official CWA dataset gateway through Cloudflare Worker.
 *
 * The CWA API key stays exclusively in Cloudflare Secrets.
 * The Android app sends a Firebase ID token for authentication.
 *
 * Posted earthquake reports are not real-time EEW warnings.
 */
object CwaOfficialGateway {

    private val datasets = setOf(
        "E-A0015-001",
        "E-A0016-001",
        "W-C0034-001",
        "W-C0034-002",
        "W-C0034-003",
        "W-C0034-005"
    )

    suspend fun read(
        context: Context,
        dataset: String
    ): JSONObject {

        require(dataset in datasets) {
            "不支援的中央氣象署資料集"
        }

        check(SeismicPushRegistration.init(context)) {
            "Firebase 或 Cloudflare Worker 尚未設定"
        }

        val auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            suspendCancellableCoroutine<Unit> { cont ->
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) {
                            cont.resumeWithException(error)
                        }
                    }
            }
        }

        val user = auth.currentUser
            ?: error("Firebase 使用者登入失敗")

        val idToken =
            suspendCancellableCoroutine<String> { cont ->
                user.getIdToken(false)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) {
                            val token = result.token

                            if (token.isNullOrBlank()) {
                                cont.resumeWithException(
                                    IllegalStateException(
                                        "無法取得 Firebase 身分憑證"
                                    )
                                )
                            } else {
                                cont.resume(token)
                            }
                        }
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) {
                            cont.resumeWithException(error)
                        }
                    }
            }

        return withContext(Dispatchers.IO) {
            val base = BuildConfig.THINN_PUSH_WORKER_URL
                .trim()
                .trimEnd('/')

            check(base.startsWith("https://")) {
                "Cloudflare Worker 網址無效"
            }

            val url = URL("$base/v1/cwa")

            val connection =
                url.openConnection() as HttpsURLConnection

            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
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

                val requestBody = JSONObject()
                    .put("dataset", dataset)
                    .toString()
                    .toByteArray(Charsets.UTF_8)

                connection.outputStream.use { output ->
                    output.write(requestBody)
                }

                val status = connection.responseCode

                if (status !in 200..299) {
                    val message = when (status) {
                        400 -> "中央氣象署資料集請求錯誤"
                        401 -> "Firebase 身分驗證失敗"
                        429 -> "請求太頻繁，請稍後再試"
                        503 -> "中央氣象署資料代理暫時無法使用"
                        else -> "官方資料讀取失敗（HTTP $status）"
                    }

                    throw IllegalStateException(message)
                }

                val responseBody =
                    connection.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }

                val response = JSONObject(responseBody)

                check(
                    response.optString("dataset") == dataset &&
                    response.optString("source") ==
                        "CWA_OFFICIAL_POSTED_DATA"
                ) {
                    "中央氣象署資料來源驗證失敗"
                }

                val raw = response.optString("payload")

                check(raw.isNotBlank()) {
                    "中央氣象署回傳資料為空"
                }

                JSONObject(raw)

            } finally {
                connection.disconnect()
            }
        }
    }
}
