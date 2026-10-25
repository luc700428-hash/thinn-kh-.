package io.atmos.weather.update
import org.junit.Assert.*
import org.junit.Test
class OtaTransferProtocolTest {
    @Test fun acceptsFullAndResumedRanges() {
        assertEquals(0L to 1000L,OtaTransferProtocol.contentRange("bytes 0-999/1000"))
        assertEquals(500L to 1000L,OtaTransferProtocol.contentRange("bytes 500-999/1000"))
    }
    @Test fun rejectsMalformedOrUnboundedRanges() {
        listOf(null,"","bytes 0-100/100","bytes 80-20/100","bytes -1-99/100",
            "bytes 0-99/*","bytes 0-99/99999999999","bytes 0-99/100 trailing",
            "bytes 99999999999999999999-99999999999999999999/99999999999999999999")
            .forEach {assertNull(it,OtaTransferProtocol.contentRange(it))}
    }
}
