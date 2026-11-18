package io.atmos.weather.push

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Only the push this phone asked for is shown, once, inside its window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SeismicCloudTestTest {
    private val ctx get() = RuntimeEnvironment.getApplication()
    private val nonce = "0123abcd-4567-89ab-cdef-0123456789ab"
    private fun pending(at: Long) = ctx.getSharedPreferences("thinn_cloud_test_v1", 0).edit()
        .putString("nonce", nonce).putLong("requested", at).commit()
    private fun push(n: String = nonce, expires: Long) = mapOf("kind" to "EEW_TEST", "nonce" to n, "expiresAtMillis" to expires.toString())

    @Test fun theRequestedPushIsAcceptedOnce() {
        val now = 1_800_000_000_000L
        pending(now - 60_000)
        assertFalse("wrong number", SeismicCloudTest.accept(ctx, push("ffffffff-4567-89ab-cdef-0123456789ab", now + 60_000), now))
        assertFalse("not a test", SeismicCloudTest.accept(ctx, mapOf("kind" to "EEW", "nonce" to nonce, "expiresAtMillis" to "${now + 60_000}"), now))
        assertTrue(SeismicCloudTest.accept(ctx, push(expires = now + 60_000), now))
        assertFalse("used up", SeismicCloudTest.accept(ctx, push(expires = now + 60_000), now))
        assertTrue(SeismicCloudTest.status(ctx, now).startsWith("已收到雲端測試警報"))
    }

    @Test fun lateOrUnrequestedPushesAreIgnoredAndRequestsWaitFiveMinutes() {
        val now = 1_800_000_000_000L
        assertFalse("never asked", SeismicCloudTest.accept(ctx, push(expires = now + 60_000), now))
        pending(now - SeismicCloudTest.WINDOW_MS - 1)
        assertFalse("too late", SeismicCloudTest.accept(ctx, push(expires = now + 60_000), now))
        pending(now - 60_000)
        assertEquals(240L, SeismicCloudTest.secondsLeft(ctx, now))
        assertEquals(0L, SeismicCloudTest.secondsLeft(ctx, now + 240_000))
        assertTrue(SeismicCloudTest.status(ctx, now).contains("3 分鐘內"))
    }
}
