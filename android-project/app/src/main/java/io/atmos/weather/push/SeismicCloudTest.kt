package io.atmos.weather.push

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import io.atmos.weather.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 「從雲端送一則測試警報給這支手機」: asks the cloud server to push one test alert to this phone's
 * FCM token. The phone only shows it if it carries the one-time number this request created, so a
 * test can never be triggered from outside. Five minutes between requests (also enforced by the server).
 */
object SeismicCloudTest {
    private const val PREF = "thinn_cloud_test_v1"
    const val COOLDOWN_MS = 5 * 60_000L
    /** How long a request waits for its push before it is considered lost. */
    const val WINDOW_MS = 10 * 60_000L

    sealed interface Result {
        data object Sent : Result
        data class Wait(val seconds: Long) : Result
        data class Failed(val message: String) : Result
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Seconds until another request is allowed (0 = now). */
    fun secondsLeft(ctx: Context, now: Long = System.currentTimeMillis()): Long {
        val last = prefs(ctx).getLong("requested", 0L)
        if (last <= 0L || last > now) return if (last > now) COOLDOWN_MS / 1000 else 0
        return ((last + COOLDOWN_MS - now).coerceAtLeast(0L) + 999) / 1000
    }

    /** What the settings row shows under the button. */
    fun status(ctx: Context, now: Long = System.currentTimeMillis()): String {
        val p = prefs(ctx)
        val received = p.getLong("received", 0L)
        val requested = p.getLong("requested", 0L)
        return when {
            received > 0 && received >= requested -> "已收到雲端測試警報 · ${clock(received)}"
            requested > 0 && now - requested < WINDOW_MS -> "已送出請求，大約 3 分鐘內會收到警報"
            requested > 0 -> "上次請求未收到警報：請確認通知、省電與網路設定"
            else -> "送出請求後，大約 3 分鐘內會收到警報。兩次請求之間要隔 5 分鐘。"
        }
    }

    private fun clock(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        .toLocalTime().withNano(0).toString().take(5)

    suspend fun request(ctx: Context): Result {
        val app = ctx.applicationContext
        val wait = secondsLeft(app)
        if (wait > 0) return Result.Wait(wait)
        if (!SeismicPushRegistration.init(app)) return Result.Failed("此版本尚未設定雲端推播服務")
        return try {
            val token = suspendCancellableCoroutine<String> { c ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { if (c.isActive) c.resume(it) }
                    .addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
            }
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) suspendCancellableCoroutine<Unit> { c ->
                auth.signInAnonymously().addOnSuccessListener { if (c.isActive) c.resume(Unit) }
                    .addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
            }
            val nonce = UUID.randomUUID().toString()
            // Remember the number before asking, so a very fast push is still recognised.
            prefs(app).edit().putString("nonce", nonce).putLong("requested", System.currentTimeMillis()).commit()
            val region = BuildConfig.THINN_PUSH_FUNCTIONS_REGION.ifBlank { "asia-east1" }
            suspendCancellableCoroutine<Any?> { c ->
                FirebaseFunctions.getInstance(region).getHttpsCallable("requestTestAlert")
                    .call(mapOf("token" to token, "nonce" to nonce))
                    .addOnSuccessListener { if (c.isActive) c.resume(it.data) }
                    .addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
            }
            Result.Sent
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    val s = ((e.details as? Map<*, *>)?.get("retryAfterSeconds") as? Number)?.toLong() ?: 300L
                    Result.Wait(s)
                }
                FirebaseFunctionsException.Code.NOT_FOUND -> { forget(app); Result.Failed("雲端測試服務尚未部署") }
                FirebaseFunctionsException.Code.UNAVAILABLE -> { forget(app); Result.Failed("雲端推播服務暫時無法送出，請稍後再試") }
                else -> { forget(app); Result.Failed("雲端主機沒有接受請求（${e.code.name}）") }
            }
        } catch (e: Exception) {
            forget(app); Result.Failed("無法連線到雲端主機，請確認網路")
        }
    }

    /** A request that never reached the server must not block the next five minutes. */
    private fun forget(ctx: Context) { prefs(ctx).edit().remove("nonce").remove("requested").apply() }

    /**
     * Called for every FCM message with kind EEW_TEST. True only for the push this phone asked
     * for, inside its window and before it expires; the number is used up so it cannot repeat.
     */
    fun accept(ctx: Context, data: Map<String, String>, now: Long = System.currentTimeMillis()): Boolean {
        if (data["kind"] != "EEW_TEST") return false
        val p = prefs(ctx)
        val nonce = p.getString("nonce", null) ?: return false
        val requested = p.getLong("requested", 0L)
        val expires = data["expiresAtMillis"]?.toLongOrNull() ?: return false
        if (data["nonce"] != nonce || now - requested !in 0..WINDOW_MS || expires < now - 60_000L) return false
        return p.edit().remove("nonce").putLong("received", now).commit()
    }
}
