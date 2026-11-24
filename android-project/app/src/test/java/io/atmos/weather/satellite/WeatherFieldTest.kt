package io.atmos.weather.satellite

import android.app.Application
import io.atmos.weather.ui.GlassQuality
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class WeatherFieldTest {
    private val rows=WeatherField.lats.size
    private val cols=WeatherField.lons.size

    /** A low centred on (22N,124E) in a 1012 hPa background, warmer to the south. */
    private fun sampleJson():String {
        val sb=StringBuilder("[")
        for(r in 0 until rows)for(c in 0 until cols){
            val lat=WeatherField.lats[r];val lon=WeatherField.lons[c]
            val d2=(lat-22)*(lat-22)+(lon-124)*(lon-124)
            val p=1012-18*Math.exp(-d2/30.0)
            val t=30-(lat-12)*0.8
            if(sb.length>1)sb.append(',')
            sb.append("""{"latitude":$lat,"longitude":$lon,"current":{"time":"2026-11-24T00:00","pressure_msl":$p,"temperature_2m":$t,"wind_speed_10m":${12+(c%5)*7},"wind_direction_10m":${(r*37)%360}}}""")
        }
        return sb.append(']').toString()
    }

    @Test fun parsesEveryGridPointInOrder() {
        val g=WeatherField.parse(sampleJson())
        assertEquals(rows,g.rows);assertEquals(cols,g.cols)
        assertEquals(Instant.parse("2026-11-24T00:00:00Z"),g.time)
        val r=WeatherField.lats.indexOfFirst{it==22.0};val c=WeatherField.lons.indexOfFirst{it==124.0}
        assertEquals(994f,g.pressure[r][c],.01f)
        assertTrue(WeatherField.url().contains("latitude=12,12,"))
        assertEquals(rows*cols,WeatherField.url().substringAfter("latitude=").substringBefore('&').split(',').size)
        assertTrue(runCatching{WeatherField.parse("[]")}.isFailure)
    }

    @Test fun findsTheLowAndDrawsClosedIsobarsAroundIt() {
        val g=WeatherField.parse(sampleJson())
        val centers=WeatherField.centers(g)
        val low=centers.single{!it.high}
        assertEquals(22.0,low.lat,0.01);assertEquals(124.0,low.lon,0.01)
        assertTrue(centers.none{it.high})
        val levels=WeatherField.levels(g.pressure,4f,1000f)
        assertTrue(levels.contains(996f)&&levels.contains(1008f))
        assertTrue(levels.all{(it-1000f)%4f==0f})
        val fine=WeatherField.refine(g.pressure,4)
        fun axis(a:DoubleArray)=DoubleArray((a.size-1)*4+1){a[0]+(a[1]-a[0])*it/4}
        val sets=WeatherField.contours(fine,axis(g.lats),axis(g.lons),levels)
        val ring=sets.single{it.level==1000f}.segments
        // Every point on the 1000 hPa line lies at the distance where the field equals 1000.
        val expected=Math.sqrt(-30*Math.log(12.0/18.0))
        for(i in ring.indices step 2){
            val d=Math.hypot(ring[i]-124.0,ring[i+1]-22.0)
            assertEquals(expected,d,0.35)
        }
        // Refinement never invents a deeper low than the model points.
        assertTrue(fine.all{row->row.all{it>=993.99f}})
    }

    @Test fun elevenDayForecastParsesEveryStepAndSamplesSmoothly() {
        val steps=6
        val sb=StringBuilder("[")
        for(r in 0 until rows)for(c in 0 until cols){
            if(sb.length>1)sb.append(',')
            val times=(0 until steps).joinToString(","){"\"2026-11-25T${"%02d".format(it*6%24)}:00\""}
            fun series(f:(Int)->Double)=(0 until steps).joinToString(","){f(it).toString()}
            sb.append("""{"hourly":{"time":[$times],"pressure_msl":[${series{1010.0+it}}],"temperature_2m":[${series{20.0}}],"wind_speed_10m":[${series{10.0}}],"wind_direction_10m":[${series{90.0}}],"cloud_cover":[${series{(r*5).toDouble()}}],"precipitation":[${series{if(c>3)2.0 else 0.0}}]}}""")
        }
        sb.append(']')
        val f=WeatherField.parseForecast(sb.toString())
        assertEquals(steps,f.size)
        assertEquals(1013f,f[3].second.grid.pressure[2][2],.01f)
        assertEquals(20f,f[0].second.cloud[4][0],.01f)
        assertTrue(WeatherField.forecastUrl().contains("forecast_days=11"))
        // Bilinear sampling between grid points, NaN outside the grid.
        val g=f[0].second.cloud
        assertEquals(12.5f,WeatherField.sample(g,WeatherField.SOUTH+5.0,WeatherField.WEST+1.0),.01f)
        assertTrue(WeatherField.sample(g,0.0,0.0).isNaN())
    }

    @Test fun windBarbsRoundToFiveKnots() {
        assertTrue(WeatherField.barb(1f).calm)
        assertEquals(WeatherField.Barb(0,0,1,false),WeatherField.barb(5f))
        assertEquals(WeatherField.Barb(0,1,1,false),WeatherField.barb(14f))
        assertEquals(WeatherField.Barb(1,1,0,false),WeatherField.barb(61f))
        assertEquals(WeatherField.Barb(2,0,1,false),WeatherField.barb(103f))
    }

    @Test fun satelliteTimesTilesAndAddresses() {
        val now=Instant.parse("2026-11-23T22:29:00Z")
        val xml="<Domains><DimensionDomain><Domain>2026-11-23T19:00:00Z/2026-11-23T21:50:00Z/PT10M,2026-11-23T22:00:00Z</Domain></DimensionDomain></Domains>"
        val times=SatelliteSource.parseDomains(xml,now)
        assertEquals(12,times.size)
        assertEquals(Instant.parse("2026-11-23T22:00:00Z"),times.last())
        assertEquals(Instant.parse("2026-11-23T20:10:00Z"),times.first())
        assertTrue(SatelliteSource.parseDomains("<Domain></Domain>",now).isEmpty())
        val fallback=SatelliteSource.fallbackTimes(now)
        assertEquals(Instant.parse("2026-11-23T21:40:00Z"),fallback.last());assertEquals(12,fallback.size)
        val url=SatelliteSource.tileUrl(SatelliteSource.Channel.INFRARED,times.last(),SatelliteSource.Tile(5,26,13))
        assertEquals("https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/Himawari_AHI_Band13_Clean_Infrared/default/2026-11-23T22:00:00Z/GoogleMapsCompatible_Level6/5/13/26.png",url)
        assertTrue(SatelliteSource.domainsUrl(SatelliteSource.Channel.VISIBLE,now).endsWith("/GoogleMapsCompatible_Level7/all/2026-11-23T19:29:00Z--2026-11-23T22:29:00Z.xml"))
        // East Asia at about 36 px per degree: a handful of z5 tiles, never beyond the channel's detail.
        val tiles=SatelliteSource.tilesFor(107.0,12.0,135.0,36.0,36.0,6)
        assertTrue(tiles.isNotEmpty()&&tiles.size<=30)
        assertTrue(tiles.all{it.z==SatelliteSource.tilesFor(107.0,12.0,135.0,36.0,36.0,6).first().z})
        assertTrue(SatelliteSource.tilesFor(120.0,22.0,122.0,25.0,2000.0,6).all{it.z==6})
        assertTrue(SatelliteSource.tilesFor(60.0,-40.0,179.0,60.0,9.0,6).size<=30)
    }

    @Test fun glassQualityFollowsTheHardwareAndStepsDownOnJank() {
        assertEquals(GlassQuality.Level.LIGHT,GlassQuality.fromHardware(32,12_000_000_000L,false,0))
        assertEquals(GlassQuality.Level.LIGHT,GlassQuality.fromHardware(35,12_000_000_000L,true,0))
        assertEquals(GlassQuality.Level.FULL,GlassQuality.fromHardware(35,12_000_000_000L,false,0))
        assertEquals(GlassQuality.Level.FULL,GlassQuality.fromHardware(35,8_000_000_000L,false,34))
        assertEquals(GlassQuality.Level.BALANCED,GlassQuality.fromHardware(35,8_000_000_000L,false,0))
        assertEquals(GlassQuality.Level.LIGHT,GlassQuality.fromHardware(35,4_000_000_000L,false,0))
        assertEquals(GlassQuality.Level.BALANCED,GlassQuality.fromHardware(35,0L,false,0))
        assertEquals(GlassQuality.Level.BALANCED,GlassQuality.capped(GlassQuality.Level.FULL,1))
        assertEquals(GlassQuality.Level.LIGHT,GlassQuality.capped(GlassQuality.Level.BALANCED,2))
        assertEquals(GlassQuality.Level.FULL,GlassQuality.resolve(1,GlassQuality.Level.LIGHT))
        assertEquals(GlassQuality.Level.LIGHT,GlassQuality.resolve(2,GlassQuality.Level.FULL))
        val judge=GlassQuality.JankJudge(window=10)
        val interval=16_666_667L
        // Smooth windows never step down; two janky windows in a row do, once.
        repeat(30){assertFalse(judge.frame(10_000_000L,interval))}
        var steps=0
        repeat(20){if(judge.frame(40_000_000L,interval))steps++}
        assertEquals(1,steps)
    }
}
