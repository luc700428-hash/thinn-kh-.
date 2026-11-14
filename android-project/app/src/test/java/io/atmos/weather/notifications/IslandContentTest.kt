package io.atmos.weather.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island shows a whole label and one real value, taken from what producers actually send. */
class IslandContentTest {
    private val c=IslandContent

    @Test fun realProducerTextBecomesOneLabelAndOneValue() {
        assertEquals(IslandCompact("地震速報","4 級",c.RED,true),
            c.compose("SEISMIC_EEW","收到地震速報 · 慎防強烈搖晃","花蓮縣近海 · M 5.8 · 所在地預估 4 級"))
        assertEquals("5弱 級",c.compose("SEISMIC_EEW","收到地震速報 · 慎防強烈搖晃","花蓮縣近海 · M 5.8 · 所在地預估 5弱 級").value)
        assertEquals("M5.8",c.compose("SEISMIC_EEW","收到地震速報 · 慎防強烈搖晃","花蓮縣近海 · M 5.8 · 所在地震度待來源提供").value)
        assertEquals("注意",c.compose("SEISMIC_EEW","收到地震速報","來源未提供").value)
        assertEquals(IslandCompact("地震報告","M6.1",c.ORANGE,false),
            c.compose("EARTHQUAKE_REPORT","中央氣象署地震報告 · M 6.1","花蓮縣壽豐鄉 · 官方震後報告（非即時預警）"))
        assertEquals("3 級",c.compose("STATION_DETECTION","地震監測 · 第三方測站檢知","ExpTech 第三方測站 123：震度 3，已連續確認；非官方地震預警").value)
        assertEquals(IslandCompact("大雨特報","臺北市",c.ORANGE,true),c.compose("WEATHER_WARNING","大雨特報","臺北市"))
        assertEquals(IslandCompact("颱風警報","新北市",c.ORANGE,true),c.compose("WEATHER_WARNING","海上陸上颱風警報","新北市 · 板橋區"))
        assertEquals("低溫特報",c.compose("WEATHER_WARNING","中央氣象署：低溫特報","嘉義縣").label)
        assertEquals("留意",c.compose("WEATHER_WARNING","陸上強風特報","").value)
        assertEquals(IslandCompact("天氣特報","3 則",c.ORANGE,true),c.compose("WARNING_SUMMARY","3 則中央氣象署特報","臺北市"))
        assertEquals(IslandCompact("鬧鐘","07:30",c.ORANGE,true),c.compose("ALARM_RINGING","鬧鐘","07:30 鬧鐘響鈴中","07:30"))
        assertEquals(IslandCompact("即將下雨","5 分內",c.BLUE,false),c.compose("VERIFIED_MINUTE_RAIN","預計 5 分鐘內下雨","板橋區"))
        assertEquals(IslandCompact("待辦事項","記得領包裹",c.BLUE,false),c.compose("TASK_REMINDER","記得領包裹","個人事項提醒"))
        assertEquals("新版本",c.compose("APP_UPDATE","thinn-khì 25.0.343 已發布","發現新版本，開啟更新中心查看特色並安全安裝").value)
        assertEquals("下載中",c.compose("APP_UPDATE","軟體更新","正在下載更新 42%").value)
    }

    @Test fun weatherUsesOnlyRealConditionAndTemperature() {
        assertEquals(IslandCompact("晴時多雲","24°",c.SKY,false),c.compose("WEATHER_BRIEFING","臺北市 · 午後有雨","記得帶傘",null,"晴時多雲",24))
        assertEquals(IslandCompact("天氣簡報","已更新",c.SKY,false),c.compose("WEATHER_BRIEFING","臺北市 · 午後有雨","記得帶傘"))
        // A placeholder condition or an impossible reading is never shown as weather.
        assertEquals(IslandCompact("目前天氣","24°",c.SKY,false),c.compose("AMBIENT_WEATHER","即時天氣","板橋區 24°C｜多雲","24°","天氣資料",99))
        assertEquals("-3°",c.compose("AMBIENT_WEATHER","即時天氣","x",null,"雷雨",-3).value)
    }

    @Test fun longTextEndsInAnEllipsisOnACodePointInsteadOfABareFragment() {
        assertEquals("大雨特報",c.fit("大雨特報",6))
        assertEquals("與客戶的季…",c.fit("與客戶的季度檢討會議",6))
        assertEquals("🌧臺…",c.fit("🌧臺北市信義區松仁路",3))
        assertEquals("臺北市",c.fit("  臺北市 ",6))
        assertEquals("",c.fit("abc",0))
        assertEquals("熱帶性低氣…",c.compose("WEATHER_WARNING","熱帶性低氣壓特報說明資訊","高雄市").label)
    }

    @Test fun everyKindFitsTheCompactIslandAndIsNeverBlank() {
        for(kind in XiaomiFocusBridge.Kind.entries) {
            val v=c.compose(kind.name,"標題很長很長很長很長很長","內容很長很長很長很長很長很長")
            assertFalse(kind.name,v.label.isBlank()||v.value.isBlank())
            assertTrue(kind.name,v.label.codePointCount(0,v.label.length)<=c.LABEL_MAX)
            assertTrue(kind.name,v.value.codePointCount(0,v.value.length)<=c.VALUE_MAX)
            assertTrue(kind.name,v.accent.matches(Regex("#[0-9A-F]{6}")))
        }
    }
}
