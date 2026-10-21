package io.atmos.weather.typhoon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real JSON decoding tests against documented CWA snake_case and existing PascalCase wire shapes. */
class CwaCycloneForecastTest {
    @Test fun cwaCamelAndSnakeCaseForecastKeepsTypedAttributes() {
        val sample=JSONObject("""{
          "records":{"tropicalCyclones":{"tropicalCyclone":[{
            "year":"2026","cwaTdNo":"25","cwaTyNo":"16","cwaTyphoonName":"測試颱風",
            "analysis_data":{"fix":{"fix_time":"2026-10-22T00:00:00+08:00","coordinate":"125.0,20.0"}},
            "forecast_data":{"fix":[
              {"initial_time":"2026-10-22T00:00:00+08:00","forecast_hour":"0","coordinate":"125.0,20.0","pressure":"975","max_wind_speed":"36","max_gust_speed":"49","moving_direction":"NW","circle_of_15ms":{"radius":"150"}},
              {"initial_time":"2026-10-22T00:00:00+08:00","forecast_hour":"6","coordinate":"124.0,20.5","pressure":"970","max_wind_speed":"40","max_gust_speed":"52","moving_direction":"WNW","circle_of_15ms":{"radius":"160"}}
            ]}
          }]}}
        }""")
        val fixes=CwaCycloneForecast.parse(sample)!!
        assertEquals(1,fixes.count { it.kind==TyphoonFix.Kind.OBSERVED })
        assertEquals(2,fixes.count { it.kind==TyphoonFix.Kind.FORECAST })
        val storm=OfficialStormTimeline.parse(sample).single()
        assertEquals("測試颱風",storm.name)
        assertEquals("16",storm.typhoonNumber)
        assertEquals(2,storm.fixes.size)
        assertEquals(975.0,storm.fixes[0].pressureHpa!!,0.01)
        assertEquals(52.0,storm.fixes[1].gustMs!!,0.01)
        assertEquals(160.0,storm.fixes[1].radius7Km!!,0.01)
        assertEquals("西北西",TyphoonDirection.zh(storm.fixes[1].officialDirection))
    }

    @Test fun doesNotMixOldAndNewForecastIssuances() {
        val sample=JSONObject("""{
          "records":{"TropicalCyclones":{"TropicalCyclone":{
            "Year":"2026","CwaTdNo":"33","CwaTyphoonName":"新資料",
            "ForecastData":{"Fix":[
              {"InitialTime":"2026-10-21T12:00:00+08:00","ForecastHour":"0","CoordinateLatitude":"20","CoordinateLongitude":"125","Pressure":"995"},
              {"InitialTime":"2026-10-21T12:00:00+08:00","ForecastHour":"6","CoordinateLatitude":"21","CoordinateLongitude":"124","Pressure":"990"},
              {"InitialTime":"2026-10-22T00:00:00+08:00","ForecastHour":"0","CoordinateLatitude":"21.3","CoordinateLongitude":"123.8","Pressure":"980"},
              {"InitialTime":"2026-10-22T00:00:00+08:00","ForecastHour":"6","CoordinateLatitude":"21.9","CoordinateLongitude":"123.1","Pressure":"970"}
            ]}
          }}}
        }""")
        val timeline=CwaCycloneForecast.timelines(sample)!!.single()
        assertEquals(2,timeline.fixes.size)
        assertEquals(980.0,timeline.fixes.first().pressureHpa!!,0.01)
        assertEquals(970.0,timeline.fixes.last().pressureHpa!!,0.01)
        assertEquals(2,CwaCycloneForecast.parse(sample)!!.size)
    }

    @Test fun noActiveStormsMustNotFabricateTracks() {
        val sample=JSONObject("""{"records":{"tropicalCyclones":{"tropicalCyclone":[]}}}""")
        assertTrue(CwaCycloneForecast.parse(sample)!!.isEmpty())
        assertTrue(CwaCycloneForecast.timelines(sample)!!.isEmpty())
    }

    @Test fun malformedCoordinatesCannotBecomeOfficialFixes() {
        val sample=JSONObject("""{
          "records":{"tropicalCyclones":{"tropicalCyclone":{
            "year":"2026","cwaTdNo":"10",
            "forecast_data":{"fix":[
              {"initial_time":"2026-10-22T00:00:00+08:00","forecast_hour":"0","coordinate":"not-a-location"},
              {"initial_time":"2026-10-22T00:00:00+08:00","forecast_hour":"6","coordinate":"999,999"}
            ]}
          }}}
        }""")
        assertTrue(CwaCycloneForecast.parse(sample)!!.isEmpty())
        assertTrue(CwaCycloneForecast.timelines(sample)!!.isEmpty())
    }
}
