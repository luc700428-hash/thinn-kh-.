package io.atmos.weather.voice

import org.junit.Assert.*
import org.junit.Test

class V25TaiwanVoiceTest {
    @Test fun timesAndDatesAreReadTheWayPeopleSayThem() {
        assertEquals("11月18日 5點 起生效", V25TaiwanVoice.humanize("11/18 05:00 起生效"))
        assertEquals("17點30分出門", V25TaiwanVoice.humanize("17:30出門"))
        assertEquals("規模5.6，深度10公里", V25TaiwanVoice.humanize("M5.6，深度10 km"))
        assertEquals("今天22到28度", V25TaiwanVoice.humanize("今天22–28°"))
        // Not a date or a time: left alone.
        assertEquals("比分3:15:20", V25TaiwanVoice.humanize("比分3:15:20"))
    }

    @Test fun speechIsSplitIntoSentencesWithBreaths() {
        val parts = V25TaiwanVoice.phrases("收到地震速報！慎防強烈搖晃！趴下、掩護、穩住。好。")
        assertEquals(listOf("收到地震速報！", "慎防強烈搖晃！", "趴下、掩護、穩住。好。"), parts)
        assertEquals(listOf("今天晴朗"), V25TaiwanVoice.phrases("今天晴朗"))
        assertTrue(V25TaiwanVoice.phrases("").isEmpty())
        assertTrue(V25TaiwanVoice.pause("小心！") > V25TaiwanVoice.pause("好的。"))
        assertTrue(V25TaiwanVoice.pause("首先；") < V25TaiwanVoice.pause("好的。"))
    }
}
