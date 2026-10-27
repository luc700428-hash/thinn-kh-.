package io.atmos.weather.integration
import android.app.Application
import io.atmos.weather.domain.model.*
import io.atmos.weather.location.*
import io.atmos.weather.next.*
import io.atmos.weather.radar.*
import io.atmos.weather.ui.adjustedTextWeight
import io.atmos.weather.widgets.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class CitiesRadarBuddyTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun rainfallDescriptionsPreserveAllWmoIntensities() {
        assertEquals(listOf("毛毛雨","中度毛毛雨","較強毛毛雨","小雨","中雨","大雨","短暫小雨","短暫中雨","短暫大雨"),
            listOf(51,53,55,61,63,65,80,81,82).map{WeatherDescriptions.fromCode(it)})
        assertEquals("降雨",WeatherDescriptions.label(WeatherCondition.RAIN,0))
        assertEquals("濃霧",WeatherDescriptions.label(WeatherCondition.FOG,0,100.0))
        assertEquals("霾",WeatherDescriptions.label(WeatherCondition.HAZE,0))
        assertEquals("晴朗夜晚",WeatherDescriptions.fromCode(0,false))
        assertNull(WeatherDescriptions.fromCode(10000))
    }
    @Test fun offlineSearchFindsAllTownshipsAndCompoundCountyNames() {
        val raw=org.json.JSONObject(context.assets.open("locations/taiwan_townships.json").bufferedReader().readText()).getJSONArray("places")
        assertEquals(368,raw.length())
        assertEquals(368,(0 until raw.length()).map{raw.getJSONObject(it).getString("id")}.toSet().size)
        assertTrue(OfflineCitySearch.search(context,"台北大安").any{it.name.endsWith("臺北市 · 大安區")||it.name.endsWith("台北市 · 大安區")})
        assertTrue(OfflineCitySearch.search(context,"臺南新營").any{it.name.endsWith("新營區")})
        assertTrue(OfflineCitySearch.search(context,"Daan").any{it.name.endsWith("大安區")})
        assertTrue(OfflineCitySearch.search(context,"台東").isNotEmpty())
    }
    @Test fun diaoyutaiDisplayIsCoordinateScopedAndDoesNotRenameBeijing() {
        for(q in listOf("釣魚台","釣魚臺","钓鱼岛","Senkaku")) {
            assertEquals("台灣 · 釣魚台",OfflineCitySearch.search(context,q).first().name)
        }
        assertEquals("台灣 · 釣魚台",LocationLabels.name("JP","Japan","Okinawa","Uotsuri",GeoPoint(25.746,123.474)))
        assertFalse(LocationLabels.isDiaoyutai(GeoPoint(39.92,116.33)))
        assertEquals("中國 · 北京 · 釣魚台國賓館",LocationLabels.name("CN","中國","北京","釣魚台國賓館",GeoPoint(39.92,116.33)))
    }
    @Test fun coordinateSearchAndRecentPlacesStayBounded() {
        assertNotNull(CitySearchRules.coordinate("25.03，121.56"));assertNull(CitySearchRules.coordinate("91,181"))
        assertEquals("台灣 · 釣魚台",CitySearchRules.coordinate("25.746, 123.474")?.name)
        for(i in 0..12)RecentCityStore.record(context,WeatherCity("r$i","地點$i",GeoPoint(20.0+i/10.0,120.0)))
        assertEquals(8,RecentCityStore.read(context).size);assertEquals("r12",RecentCityStore.read(context).first().id)
        RecentCityStore.record(context,RecentCityStore.read(context).last());assertEquals(8,RecentCityStore.read(context).size)
        assertEquals("台灣",CitySearchRules.normalize(" 臺灣 "))
    }
    @Test fun nonRainAudioAlwaysHasRealPlayablePcm() {
        for(condition in listOf(WeatherCondition.CLEAR,WeatherCondition.PARTLY_CLOUDY,WeatherCondition.CLOUDY))for(night in listOf(true,false)) {
            val scene=WeatherScenes.classify(condition,if(condition==WeatherCondition.CLEAR)0 else 3,null)
            val path=AmbientSoundPolicy.path(condition,scene,night)
            assertNotNull(path);val wav=LoopWav.read(context,path!!);assertNotNull(path,wav)
            assertEquals(44100,wav!!.sampleRate);assertTrue(wav.frames>=44100*10)
            assertTrue(wav.pcm.any{it.toInt()!=0})
        }
        assertNull(AmbientSoundPolicy.path(WeatherCondition.UNKNOWN,null,false))
        assertTrue(AmbientSoundPolicy.path(WeatherCondition.CLOUDY,null,true)!!.contains("cloudy_night"))
    }
    @Test fun radarProjectionPreservesPinchAnchorAndWrapsLongitude() {
        val c=RadarCamera(25.0,179.8,5.0)
        val moved=c.pan(-150f,0f,1f);assertTrue(moved.longitude in -180.0..180.0)
        val after=c.zoomAt(1.5f,100f,80f,400f,300f,1f)
        fun anchor(camera:RadarCamera)=RadarProjection.x(camera.longitude)+(100-200)/camera.world(1f)
        assertEquals(anchor(c),anchor(after),1e-9)
        val center=c.zoomAt(1.5f,200f,150f,400f,300f,1f)
        assertEquals(c.latitude,center.latitude,1e-9);assertEquals(c.longitude,center.longitude,1e-9)
        for(v in listOf(-84.0,-20.0,0.0,25.0,84.0))assertEquals(v,RadarProjection.latitude(RadarProjection.y(v)),1e-8)
        assertEquals(c,c.zoomAt(Float.NaN,0f,0f,100f,100f,1f))
    }
    @Test fun radarTilesAreBoundedAndNeverRequestUnsupportedZoom() {
        for(lon in listOf(-179.9,0.0,121.0,179.9))for(zoom in listOf(2.0,4.5,7.0)) {
            val tiles=RadarProjection.tiles(RadarCamera(25.0,lon,zoom),900f,1500f,1f)
            assertTrue(tiles.size<=36);assertTrue(tiles.all{it.z in 2..7&&it.x in 0 until (1 shl it.z)&&it.y in 0 until (1 shl it.z)})
        }
        assertTrue(RadarProjection.tiles(RadarCamera(),Float.NaN,300f,1f).isEmpty())
        val c=RadarCamera(Double.NaN,Double.POSITIVE_INFINITY,Double.NaN).normalized()
        assertTrue(c.latitude.isFinite()&&c.longitude.isFinite()&&c.zoom in 2.0..7.0)
    }
    @Test fun radarMetadataAcceptsRealHashPathsAndRejectsUntrustedHosts() {
        val now=1_800_000_000_000L
        val text="""{"host":"https://tilecache.rainviewer.com","radar":{"past":[{"time":1799999400,"path":"/v2/radar/eba6beb6bf79"},{"time":1799998800,"path":"/v2/radar/old_hash"}]}}"""
        val result=RadarRepository.parse(text,now);assertEquals(2,result.frames.size)
        assertTrue(result.frames[0].time<result.frames[1].time)
        for(bad in listOf(text.replace("tilecache.rainviewer.com","evil.example"),text.replace("/v2/radar/eba6beb6bf79","/../../secret").replace("/v2/radar/old_hash","https://evil"))) {
            assertTrue(runCatching{RadarRepository.parse(bad,now)}.isFailure)
        }
    }
    @Test fun radarDecoderChecksDimensionsBeforeAllocatingImages() {
        fun png(w:Int):ByteArray {
            val bitmap=android.graphics.Bitmap.createBitmap(w,32,android.graphics.Bitmap.Config.ARGB_8888)
            val stream=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream)
            bitmap.recycle();return stream.toByteArray()
        }
        assertNull(RadarRepository.decodeTile(byteArrayOf(1,2,3)))
        assertNull(RadarRepository.decodeTile(png(513)))
        assertEquals(256,RadarRepository.decodeTile(png(256))!!.width)
    }
    @Test fun typographyWeightChangesBothBodyAndHeadingsWithoutOverflow() {
        assertEquals(800,adjustedTextWeight(400,800));assertEquals(900,adjustedTextWeight(700,800))
        assertEquals(300,adjustedTextWeight(500,200));assertEquals(100,adjustedTextWeight(100,200))
    }
    @Test fun buddyMotionHasClosedJoinsAndDifferentSeededActions() {
        for(seed in 0..100) {
            val a=BuddyMotion.pose(0f,seed);val b=BuddyMotion.pose(1f,seed)
            assertEquals(a,b)
            assertTrue((0..200).map{BuddyMotion.pose(it/200f,seed).openness}.min()<.1f)
            assertTrue(abs(BuddyMotion.pose(.9999f,seed).lookX-a.lookX)<.01f)
        }
        assertNotEquals(BuddyMotion.pose(.3f,1),BuddyMotion.pose(.3f,2))
        assertEquals(WeatherBuddyArt.Kind.MOON,WeatherBuddyArt.kind(WeatherCondition.CLEAR,false))
        assertEquals(WeatherBuddyArt.Kind.UNKNOWN,WeatherBuddyArt.kind(WeatherCondition.UNKNOWN,true))
    }
}
