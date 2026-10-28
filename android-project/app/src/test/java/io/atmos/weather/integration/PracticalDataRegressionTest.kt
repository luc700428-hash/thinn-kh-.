package io.atmos.weather.integration

import android.app.Application
import io.atmos.weather.data.remote.CwaSurfaceObservations
import io.atmos.weather.data.remote.SurfaceObservation
import io.atmos.weather.data.remote.OpenMeteoProvider
import io.atmos.weather.data.repository.WeatherDiskCache
import io.atmos.weather.domain.model.*
import io.atmos.weather.earthquake.*
import io.atmos.weather.lifestyle.*
import io.atmos.weather.ocean.*
import io.atmos.weather.sky.SkyEphemeris
import io.atmos.weather.typhoon.CwaWindCityTable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PracticalDataRegressionTest {
    private val now=Instant.parse("2026-10-27T04:00:00Z")
    private val city=WeatherCity("test-taipei","台灣 · 臺北市 · 信義區",GeoPoint(25.033,121.565))
    private fun weather(t:Double=20.0,feels:Double=t)=WeatherSnapshot(city,now,now,"open-meteo",t,feels,70,12.0,1012.0,
        WeatherCondition.CLEAR,true,null,null,emptyList())
    private fun station(at:Instant=now)=SurfaceObservation("A","臺北",121.565,25.033,10.0,at,10.0,70,6.0,1012.0,90,"陰")

    @Test fun diskRoundTripDoesNotMoveHumidityWindAndWeatherIntoSnowFields() {
        val cache=WeatherDiskCache(RuntimeEnvironment.getApplication())
        val hour=HourlyWeather(now,12.3,45,1.2,snowfallProbabilityPct=0,snowfallCm=.2,freezingRainProbabilityPct=3,
            humidityPct=87,windKmh=21.6,weatherCode=61,feelsLikeC=9.8)
        val original=weather().copy(hourly=listOf(hour),observationStation="臺北",observationDistanceKm=1.5,
            observationDescription="毛毛雨",feelsLikeMethod="CWA",forecastReferenceAt=now.plusSeconds(3600))
        cache.save(original)
        val restored=cache.load(city)!!
        assertEquals(hour,restored.hourly.single())
        assertEquals(original.observationStation,restored.observationStation)
        assertEquals(original.forecastReferenceAt,restored.forecastReferenceAt)
        assertEquals(original.feelsLikeMethod,restored.feelsLikeMethod)
        assertEquals(original.observationDescription,restored.observationDescription)
        assertEquals(1.5,restored.observationDistanceKm!!,0.0)
    }
    @Test fun forecastIsAnchoredToModelTimeWhenObservationIsOlder() {
        val w=weather().copy(observedAt=now.minusSeconds(1200),forecastReferenceAt=now,
            hourly=listOf(HourlyWeather(now.minusSeconds(900),5.0,90,null),HourlyWeather(now,20.0,10,null)))
        assertEquals(10,w.nextHourRainProbabilityPct);assertEquals(1,w.nextHours.size)
    }
    @Test fun futureAndStaleCacheCannotBecomeFreshWhenOpened() {
        val ttl=Duration.ofMinutes(10)
        assertTrue(WeatherDataQuality.cacheFresh(weather(),now.plusSeconds(599),ttl))
        assertFalse(WeatherDataQuality.cacheFresh(weather(),now.plusSeconds(600),ttl))
        assertFalse(WeatherDataQuality.cacheFresh(weather().copy(fetchedAt=now.plusSeconds(10)),now,ttl))
        assertFalse(WeatherDataQuality.current(weather().copy(observedAt=now.minusSeconds(7201)),now))
        assertFalse(WeatherDataQuality.current(weather().copy(temperatureC=Double.NaN),now))
    }
    @Test fun observationsUseThirtyMinuteSourceLimit() {
        val w=weather().copy(observationStation="臺北",sourceId="cwa-observation+open-meteo")
        assertTrue(WeatherDataQuality.current(w.copy(observedAt=now.minusSeconds(1800)),now))
        assertFalse(WeatherDataQuality.current(w.copy(observedAt=now.minusSeconds(1801)),now))
    }
    @Test fun nearbyObservationRequiresFreshnessDistanceAndComparableElevation() {
        val s=station()
        assertNotNull(CwaSurfaceObservations.choose(listOf(s),25.033,121.565,10.0,now))
        assertNull(CwaSurfaceObservations.choose(listOf(s),24.0,121.565,10.0,now))
        assertNull(CwaSurfaceObservations.choose(listOf(s),25.033,121.565,211.0,now))
        assertNull(CwaSurfaceObservations.choose(listOf(station(now.minusSeconds(1801))),25.033,121.565,10.0,now))
        assertNull(CwaSurfaceObservations.choose(listOf(station(now.plusSeconds(121))),25.033,121.565,10.0,now))
    }
    @Test fun cwaSelectsWgs84AndRejectsMissingValueSentinels() {
        fun fixture(temp:String)="""{"records":{"Station":[{"StationId":"A","StationName":"臺北","ObsTime":{"DateTime":"2026-10-27T12:00:00+08:00"},"GeoInfo":{"StationAltitude":"10","Coordinates":[{"CoordinateName":"TWD67","StationLatitude":"23","StationLongitude":"119"},{"CoordinateName":"WGS84","StationLatitude":"25.033","StationLongitude":"121.565"}]},"WeatherElement":{"AirTemperature":"$temp","RelativeHumidity":"70","WindSpeed":"6","AirPressure":"1012","Weather":"小雨"}}]}}"""
        val s=CwaSurfaceObservations.parse(fixture("10")).single()
        assertEquals(25.033,s.lat,0.0);assertEquals(121.565,s.lon,0.0);assertEquals(now,s.at)
        assertTrue(CwaSurfaceObservations.parse(fixture("-99")).isEmpty())
    }
    @Test fun apparentTemperatureUsesMetresPerSecondWithoutPreferenceAdjustment() {
        assertEquals(5.516,CwaSurfaceObservations.apparent(10.0,70,6.0)!!,.01)
        assertNull(CwaSurfaceObservations.apparent(10.0,101,6.0))
        assertNull(CwaSurfaceObservations.apparent(10.0,70,Double.NaN))
    }
    private fun index(stamp:String="202610270400",at:Long=now.toEpochMilli())=
        CwaWindCityTable.parseIndex("var DataPath='typhoon/TY_NEWS/';var TY_DataTime='$stamp';",at)
    @Test fun officialZeroIsDifferentFromAbsentOrInvalidProbability() {
        val i=index()!!
        val s=CwaWindCityTable.parse("""{"DURING_THE_HOURS":120,"AREA":{"TaipeiCity":0,"Nantou":37,"Tainan":null,"Kaohsiung":101,"Yilan":"0"}}""",i)!!
        assertEquals("0%",s.value(121.565,25.033,"臺北市"))
        assertEquals("37%",s.value(120.69,23.91,"南投縣"))
        assertNull(s.value(120.21,22.99,"臺南市"))
        assertFalse(s.percentages.containsKey("Kaohsiung"));assertFalse(s.percentages.containsKey("Yilan"))
        assertNull(s.value(139.7,35.7,"東京"))
    }
    @Test fun officialCountyReferenceAndIslandSpecificReferenceAreNotMixed() {
        val s=CwaWindCityTable.parse("""{"DURING_THE_HOURS":120,"AREA":{"ChiayiCounty":20,"ChiayiCity":0,"Taitung":30,"Lanyu":80}}""",index()!!)!!
        assertEquals("20%",s.value(120.70,23.51,"台灣 · 嘉義縣 · 阿里山鄉"))
        assertEquals("80%",s.value(121.55,22.04,"臺東縣 · 蘭嶼鄉"))
    }
    @Test fun probabilityDiscoveryRejectsUnsafePathsStaleAndInvalidDates() {
        assertNull(CwaWindCityTable.parseIndex("var DataPath='https://evil.invalid/';var TY_DataTime='202610270400';",now.toEpochMilli()))
        assertNull(index("202602300400"));assertNull(index(at=now.plusSeconds(36*3600+1).toEpochMilli()))
        assertNull(index(at=now.minusSeconds(601).toEpochMilli()))
        assertNull(CwaWindCityTable.parse("""{"DURING_THE_HOURS":24,"AREA":{"TaipeiCity":0}}""",index()!!))
    }
    @Test fun stationExpiresOnlyAfterTwentySecondsAndExplicitMaintenanceIsDistinct() {
        val at=now.toEpochMilli();val s=SeismicStation("A","站",25.0,121.0,1.0,2.0,observedAtMs=at)
        assertEquals(StationViewState.LIVE,StationFreshness.state(s,at+20_000))
        assertEquals(StationViewState.EXPIRED,StationFreshness.state(s,at+20_001))
        assertEquals(StationViewState.WAITING,StationFreshness.state(s.copy(intensity=null,accelerationGal=null),at))
        assertEquals(StationViewState.MAINTENANCE,StationFreshness.state(s.copy(availability=StationAvailability.MAINTENANCE),at))
        assertEquals(StationViewState.OFFLINE,StationFreshness.state(s.copy(availability=StationAvailability.OFFLINE),at))
        assertEquals(SeismicFeedState.LIVE,SeismicFeedHealth.rts(true,"連線中斷，等待重連",at,at+500))
    }
    @Test fun partialFramesPreserveOldStationTimeInsteadOfRenewingIt() {
        val at=now.toEpochMilli();val a=SeismicStation("A","A",25.0,121.0,1.0,2.0,observedAtMs=at)
        val b=a.copy(id="B",observedAtMs=at+10000)
        val next=StationFreshness.merge(listOf(a,b),listOf(b.copy(observedAtMs=at+20000)))
        assertEquals(2,next.size)
        assertEquals(StationViewState.EXPIRED,StationFreshness.state(next.first{it.id=="A"},at+20001))
        assertEquals(StationViewState.LIVE,StationFreshness.state(next.first{it.id=="B"},at+20001))
        val metadata=StationFreshness.merge(next,listOf(a.copy(intensity=null,accelerationGal=null,observedAtMs=null)))
        assertEquals(at,metadata.first{it.id=="A"}.observedAtMs)
    }
    @Test fun realRtsSourceTsWinsOverReceiptAndMetadataDoesNotRenewReadings() {
        val at=now.toEpochMilli();val metadata=mapOf("117825C" to SeismicStation("117825C","站",25.0,121.0,null,null))
        val s=SeismicStationData.parse("""{"source":"core-tnn1","ts":$at,"stations":{"117825C":{"i":1.1,"pga":2.6}},"eq":[]}""",metadata,at+4000).single()
        assertEquals(at,s.observedAtMs)
        assertEquals(StationViewState.EXPIRED,StationFreshness.state(s,at+20001))
        val meta=SeismicStationData.parse("""{"ts":${at+10000},"stations":{"117825C":{"status":"maintenance"}}}""",metadata,at+10000).single()
        assertNull(meta.observedAtMs);assertEquals(StationAvailability.MAINTENANCE,meta.availability)
    }
    private fun marine(currentUnit:String="km/h",temperatureUnit:String="°C",temperature:Double=24.0)=
        """{"latitude":25.2,"longitude":121.8,"current_units":{"wave_height":"m","swell_wave_height":"m","ocean_current_velocity":"$currentUnit","sea_surface_temperature":"$temperatureUnit"},"current":{"time":${now.epochSecond},"wave_height":0,"wave_direction":360,"wave_period":8,"swell_wave_height":null,"ocean_current_velocity":3.6,"ocean_current_direction":90,"sea_surface_temperature":$temperature}}"""
    @Test fun marineConvertsVelocityAndTemperatureAndPreservesPublishedZeroAndMissing() {
        val s=MarineRepository.parse(marine(),MarineRepository.places.first(),now)
        assertEquals(1.0,s.current.currentMs!!,1e-9);assertEquals(0.0,s.current.waveHeight!!,0.0)
        assertEquals(90.0,s.current.currentDirection!!,0.0);assertEquals(360.0,s.current.waveDirection!!,0.0)
        assertNull(s.current.swellHeight);assertEquals(24.0,s.current.seaTemperature!!,0.0)
        assertEquals(20.0,MarineRepository.parse(marine(temperatureUnit="°F",temperature=68.0),s.place,now).current.seaTemperature!!,1e-9)
        assertNull(MarineRepository.parse(marine(currentUnit="unknown",temperatureUnit="K"),s.place,now).current.currentMs)
        assertNull(MarineRepository.parse(marine(temperatureUnit="K"),s.place,now).current.seaTemperature)
    }
    @Test fun marineRejectsMissingWaveDataAndExpiredSources() {
        val s=MarineRepository.parse(marine(),MarineRepository.places.first(),now)
        assertFalse(s.copy(current=s.current.copy(at=now.minusSeconds(5401))).fresh(now))
        assertTrue(runCatching{MarineRepository.parse(marine().replace("\"wave_height\":0","\"wave_height\":null"),s.place,now)}.isFailure)
        assertEquals("北",CompassDirections.name(360.0));assertEquals("東",CompassDirections.name(90.0))
    }
    @Test fun outingHonorsActualColdEvenWhenFeelsOrPreferenceIsWarmer() {
        fun plan(t:Double,feels:Double=t)=LifestyleAdvisor.recommend(weather(t,feels),OutingMode.DAILY,now=now,
            preferences=LifestylePreferences(comfort=TemperatureComfort.FEELS_HOT))
        assertTrue(plan(20.9,25.0).layer.contains("外套"))
        assertTrue(plan(9.9,15.0).headline.contains("10°C"))
        assertTrue(plan(3.9,12.0).headline.contains("4°C"))
        assertTrue(plan(3.9,12.0).layer.contains("帽子"))
    }
    @Test fun outingConsidersUpcomingColdAndRainWithoutMutatingTemperature() {
        val w=weather(25.0).copy(hourly=listOf(HourlyWeather(now.plusSeconds(3*3600),9.0,70,null)))
        val plan=LifestyleAdvisor.recommend(w,OutingMode.DAILY,now=now)
        assertTrue(plan.layer.contains("保暖"));assertTrue(plan.extraReminders.any{it.contains("降溫")} )
        assertTrue(plan.umbrella.contains("傘"));assertEquals(25.0,w.temperatureC,0.0)
    }
    @Test fun skyPositionsAndRiseTimesFollowLocationAndCivilDayIncludingDst() {
        val zone=ZoneId.of("Asia/Taipei");val at=now.atZone(zone)
        val position=SkyEphemeris.position(city,at)
        assertTrue(position.fraction in 0.0..1.0);assertTrue(position.phase in -180.0..180.0)
        assertTrue(position.moonAzimuth in 0.0..360.0);assertTrue(position.sunAltitude>30)
        assertTrue(position.moonDistanceKm in 350000.0..410000.0)
        val day=SkyEphemeris.day(city,at.toLocalDate(),zone)
        assertTrue(day.sunrise!!.hour in 5..6);assertTrue(day.sunset!!.hour in 16..18)
        for(date in listOf(LocalDate.of(2026,3,29),LocalDate.of(2026,10,25))) {
            val paris=SampleCities.all.first{it.id=="paris"};val d=SkyEphemeris.day(paris,date,ZoneId.of("Europe/Paris"))
            listOfNotNull(d.sunrise,d.sunset,d.moonrise,d.moonset).forEach{assertEquals(date,it.toLocalDate())}
        }
        assertEquals("新月",SkyEphemeris.phaseName(-179.0));assertEquals("上弦月",SkyEphemeris.phaseName(-90.0))
        assertEquals("滿月",SkyEphemeris.phaseName(0.0));assertEquals("下弦月",SkyEphemeris.phaseName(90.0))
    }
}
