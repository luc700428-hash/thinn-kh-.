package io.atmos.weather.ui

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CwaRadarObservationTest {
    private fun fixture(name:String)=javaClass.getResourceAsStream("/weather-info/$name")!!.use{it.readBytes()}
    @Test fun realOfficialPngServedAsOctetStreamIsDecodedWithoutMimeGate() {
        // Captured HTTP 200, Content-Type binary/octet-stream, 3600 x 3600 PNG, 2026-10-28.
        val bitmap=CwaRadarObservation.decode(fixture("cwa-radar-octet-stream.png"))!!
        assertEquals(1800,bitmap.width);assertEquals(1800,bitmap.height)
        assertTrue(bitmap.allocationByteCount<=13_000_000)
        assertNull(CwaRadarObservation.decode("<html>service unavailable</html>".toByteArray()))
        assertNull(CwaRadarObservation.decode(byteArrayOf(-119,80,78,71,13,10,26,10)+ByteArray(128)))
        bitmap.recycle()
    }
    @Test fun observedTimeComesFromOfficialMetadataAndRejectsStaleOrOtherProducts() {
        val text=String(fixture("cwa-radar-metadata.json"),Charsets.UTF_8)
        val now=Instant.parse("2026-10-28T11:33:30Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-28T11:20:00Z").toEpochMilli(),CwaRadarObservation.observationTime(text,now))
        assertNull(CwaRadarObservation.observationTime(text,now+3_600_000))
        assertNull(CwaRadarObservation.observationTime(text.replace("O-A0058-003","O-A0058-004"),now))
        assertNull(CwaRadarObservation.observationTime(text.replace("Actual","Test"),now))
    }
}
