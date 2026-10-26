package io.atmos.weather.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class AppUpdateDeltaTest {
    @Test fun fullReleaseWithoutInternalMarkerIsNotDiscarded()=runTest {
        val notes="# 25.0.300\n\n修正說明\n- 地圖\n- 鬧鐘\n\nhttps://example.com/release"
        val release=ReleaseInfo("25.0.300",notes,"https://example.com/app.apk","a".repeat(64))
        val shown=AppUpdateDelta.describe("25.0.299",release)
        assertTrue(shown.contains(notes));assertTrue(shown.contains("25.0.299 → 25.0.300"))
    }
    @Test fun targetIncrementalBlockIsCompleteAndDoesNotIncludeOldHistory() {
        val entries=(1..30).joinToString("\n") {"- 更新 $it"}
        val notes="舊版累積紀錄\n"+AppUpdateDelta.MARKER+"\n"+entries+"\n<!-- END_THINN_INCREMENTAL_CHANGES -->\n其他舊版"
        assertEquals(entries,AppUpdateDelta.displayNotes(notes))
        assertEquals(30,AppUpdateDelta.changes(notes)!!.size)
    }
    @Test fun emptyNotesDoNotInventChanges()=runTest {
        val shown=AppUpdateDelta.describe("25.0.299",ReleaseInfo("25.0.300","","https://example.com/app.apk","a".repeat(64)))
        assertTrue(shown.contains("尚未提供更新說明"));assertFalse(shown.contains("iOS"))
    }
}
