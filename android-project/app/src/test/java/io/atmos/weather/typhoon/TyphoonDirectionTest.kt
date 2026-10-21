package io.atmos.weather.typhoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TyphoonDirectionTest {
    @Test fun allSixteenCompassCodesAreTranslated() {
        val expected = linkedMapOf(
            "N" to "北", "NNE" to "北北東", "NE" to "東北", "ENE" to "東北東",
            "E" to "東", "ESE" to "東南東", "SE" to "東南", "SSE" to "南南東",
            "S" to "南", "SSW" to "南南西", "SW" to "西南", "WSW" to "西南西",
            "W" to "西", "WNW" to "西北西", "NW" to "西北", "NNW" to "北北西"
        )
        expected.forEach { (source, chinese) ->
            assertEquals("Failed compass code: $source", chinese, TyphoonDirection.zh(source))
        }
    }

    @Test fun abbreviatedCodesNormalizeCaseAndWhitespace() {
        assertEquals("西北西", TyphoonDirection.zh(" wnw "))
        assertEquals("北北東", TyphoonDirection.zh("N-N-E"))
        assertEquals("南南西", TyphoonDirection.zh("s_s_w"))
    }

    @Test fun fullEnglishDirectionsConvertToTraditionalChinese() {
        assertEquals("西北西", TyphoonDirection.zh("west-northwest"))
        assertEquals("北北東", TyphoonDirection.zh("north_northeast"))
    }

    @Test fun originalChineseValuesRemainAndUnknownCodesAreNotShown() {
        assertEquals("西北", TyphoonDirection.zh("西北"))
        assertNull(TyphoonDirection.zh(null))
        assertNull(TyphoonDirection.zh(""))
        assertNull(TyphoonDirection.zh("not-a-direction"))
    }
}
