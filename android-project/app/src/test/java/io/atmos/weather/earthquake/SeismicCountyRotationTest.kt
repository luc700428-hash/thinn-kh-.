package io.atmos.weather.earthquake
import org.junit.Assert.*
import org.junit.Test
class SeismicCountyRotationTest{
    @Test fun allCountiesRotateAndHistoricalGradesAndMissingValuesRemainDistinct(){
        val entries=SeismicCountyRotation.entries(mapOf("台北市" to "3","南投縣" to "7","嘉義縣" to "6","臺南市" to "5"),"臺北市")
        assertEquals(22,entries.size);assertEquals("臺北市",entries.first().county);assertEquals("3 級",entries.first().grade)
        assertEquals("南投縣",entries[1].county);assertEquals("嘉義縣",entries[2].county)
        assertEquals("6 級",entries.single{it.county=="嘉義縣"}.grade);assertEquals("5 級",entries.single{it.county=="臺南市"}.grade)
        assertEquals("—",entries.single{it.county=="金門縣"}.grade)
        assertEquals(entries,(0..10).flatMap{SeismicCountyRotation.page(entries,it)})
        assertEquals(entries.take(2),SeismicCountyRotation.page(entries,11))
    }
}
