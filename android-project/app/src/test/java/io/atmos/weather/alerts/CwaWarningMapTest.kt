package io.atmos.weather.alerts

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CwaWarningMapTest {
    private val now=Instant.parse("2026-10-28T18:30:00Z")
    private fun text(name:String)=javaClass.getResourceAsStream("/continuous-radar/$name")!!.bufferedReader().use{it.readText()}
    private fun atlas()=runBlocking{WarningCountyAtlas.load(RuntimeEnvironment.getApplication())}
    private fun feed()=CwaNationalWarningFeed.parse(text("cwa-counties.json"),now)
    @Test fun nationwideFeedIncludesEveryActiveCountyRatherThanOnlyTheUsersLocation() {
        val all=feed();assertEquals(setOf("屏東縣","臺東縣","澎湖縣"),all.map{CwaWarningGeography.county(it.county)}.toSet())
        val local=CwaCountyHazardFeed.parse(JSONObject(text("cwa-counties.json")),"台灣 · 屏東縣 · 恆春鎮",null,now)
        assertEquals(1,local.size);assertEquals("屏東縣",local.single().county)
        assertTrue(all.all{it.expiresAt.isAfter(now)&&it.title=="陸上強風特報"})
    }
    @Test fun incompleteDuplicateAndInvalidHazardRecordsCannotBecomeAnAllClearMap() {
        for(change in listOf<(JSONObject)->Unit>(
            {j->j.getJSONObject("cwaopendata").getJSONObject("dataset").getJSONArray("location").remove(0)},
            {j->val a=j.getJSONObject("cwaopendata").getJSONObject("dataset").getJSONArray("location");a.getJSONObject(0).put("locationName",a.getJSONObject(1).getString("locationName"))},
            {j->j.getJSONObject("cwaopendata").getJSONObject("dataset").getJSONArray("location").getJSONObject(0).remove("hazardConditions")},
            {j->j.getJSONObject("cwaopendata").getJSONObject("dataset").getJSONArray("location").getJSONObject(15).getJSONObject("hazardConditions").getJSONObject("hazards").getJSONObject("validTime").remove("endTime")}
        )) {
            val j=JSONObject(text("cwa-counties.json"));change(j)
            assertThrows(Exception::class.java){CwaNationalWarningFeed.parse(j.toString(),now)}
        }
    }
    @Test fun privateTestStaleAndFutureDocumentsAreRejected() {
        for((key,value) in listOf("status" to "Test","scope" to "Private","sent" to "2026-10-01T00:00:00+08:00","sent" to "2026-11-01T00:00:00+08:00")) {
            val j=JSONObject(text("cwa-counties.json"));j.getJSONObject("cwaopendata").put(key,value)
            assertThrows(Exception::class.java){CwaNationalWarningFeed.parse(j.toString(),now)}
        }
    }
    @Test fun cancellationAndExpiryNeverRemainCurrentMapHighlights() {
        val cancelled=JSONObject(text("cwa-counties.json"));cancelled.getJSONObject("cwaopendata").put("msgType","Cancel")
        assertTrue(CwaNationalWarningFeed.parse(cancelled.toString(),now).isEmpty())
        assertTrue(CwaNationalWarningFeed.parse(text("cwa-counties.json"),Instant.parse("2026-10-28T21:01:00Z")).isEmpty())
        val bulletins=CwaBulletins.parse(text("cwa-bulletins.json"),now)
        val late=Instant.parse("2026-10-28T21:01:00Z")
        val state=CwaWarningMapState.build(CwaNationalWarnings(feed(),now),bulletins,emptyList(),bulletins.single().title,late)
        assertTrue(state.expiredSelection);assertTrue(state.titles.isEmpty())
    }
    @Test fun nationwideMapCanDisplayOfficialTyphoonScopeWithoutChangingLocalAlertIssuance() {
        val j=JSONObject(text("cwa-counties.json"))
        val h=j.getJSONObject("cwaopendata").getJSONObject("dataset").getJSONArray("location").getJSONObject(15)
            .getJSONObject("hazardConditions").getJSONObject("hazards").getJSONObject("info")
        h.put("phenomena","颱風").put("significance","警報")
        assertTrue(CwaNationalWarningFeed.parse(j.toString(),now).any{it.title=="颱風警報"})
        assertTrue(CwaCountyHazardFeed.parse(j,"屏東縣",null,now).isEmpty())
    }
    @Test fun sourceFailureDoesNotPretendThereAreNoWarningsAndFallbackKeepsRegionalNames() {
        val bulletins=CwaBulletins.parse(text("cwa-bulletins.json"),now)
        val failed=CwaNationalWarnings(emptyList(),now,failed=true)
        val state=CwaWarningMapState.build(failed,bulletins,emptyList(),null,now)
        assertFalse(state.complete);assertEquals(setOf("屏東縣","臺東縣","澎湖縣"),state.titles.keys)
        assertTrue(CwaWarningGeography.resolve("全國海面").isEmpty())
        assertEquals(setOf("臺東縣"),CwaWarningGeography.resolve("蘭嶼綠島"))
        val latest=CwaWarningMapState.build(CwaNationalWarnings(emptyList(),now),bulletins,emptyList(),null,now)
        assertTrue(latest.complete);assertTrue("An older bulletin must not override a newer complete national result",latest.titles.isEmpty())
    }
    @Test fun namedOfficialAtlasKeepsAll22CountiesAndOffshoreContours() {
        val atlas=atlas();assertEquals(22,atlas.counties.size)
        assertTrue(atlas.counties.all{it.contains(it.label.first,it.label.second)})
        assertTrue(atlas.counties.single{it.name=="澎湖縣"}.rings.size>50)
        val context=RuntimeEnvironment.getApplication()
        val bytes=context.assets.open("maps/warning_counties.tkatlasz").use{it.readBytes()}
        val meta=JSONObject(context.assets.open("maps/warning_counties.metadata.json").bufferedReader().use{it.readText()})
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        assertEquals(meta.getString("atlas_sha256"),hash)
    }
    @Test fun anOlderNationalSnapshotCannotHideANewerOfficialBulletinOrCancellation() {
        val bulletin=CwaBulletins.parse(text("cwa-bulletins.json"),now).single().copy(updatedAt=now)
        val national=CwaNationalWarnings(emptyList(),now,sourceAt=now.minusSeconds(3600))
        val newer=CwaWarningMapState.build(national,listOf(bulletin),emptyList(),null,now)
        assertFalse(newer.complete);assertTrue(newer.sourceMismatch)
        assertEquals(setOf("屏東縣","臺東縣","澎湖縣"),newer.titles.keys)
        val active=national.copy(hazards=feed())
        val cancelled=CwaWarningMapState.build(active,listOf(bulletin.copy(cancelled=true)),emptyList(),null,now)
        assertTrue(cancelled.titles.isEmpty());assertTrue(cancelled.sourceMismatch)
    }
    @Test fun screenHitTestingAndProjectionRemainCorrectAfterZoomAndPan() {
        val atlas=atlas();val views=WarningMapLayout.viewports(720f,780f,2f)
        val main=views.first()
        for(name in listOf("屏東縣","臺東縣","臺北市","臺中市","花蓮縣")) {
            val c=atlas.counties.single{it.name==name};val p=main.project(c.label.first,c.label.second)
            assertEquals(name,WarningMapLayout.hit(atlas,views,p))
        }
        views.drop(1).forEach{assertEquals(it.county,WarningMapLayout.hit(atlas,views,it.rectangle.center))}
        val zoomed=WarningMapLayout.viewports(720f,780f,2f,3.2f,Offset(20f,-15f)).first()
        val point=zoomed.project(120.75,23.5);val restored=zoomed.unproject(point)
        assertEquals(120.75,restored.first,1e-5);assertEquals(23.5,restored.second,1e-5)
    }
    @Test fun nativeWarningMapHasReadableThemesAndClearsExpiredHatching() {
        val atlas=atlas();val state=CwaWarningMapState.build(CwaNationalWarnings(feed(),now),emptyList(),emptyList(),null,now)
        val output=File("build/warning-map-renders").apply{mkdirs()}
        for(dark in listOf(false,true)) {
            val colours=WarningMapColours.forTheme(dark,0xff007aff.toInt())
            val bitmap=Bitmap.createBitmap(720,780,Bitmap.Config.ARGB_8888)
            WarningMapPainter().draw(android.graphics.Canvas(bitmap),720f,780f,2f,1f,atlas,state,"臺東縣",colours)
            val pixels=IntArray(720*780);bitmap.getPixels(pixels,0,720,0,0,720,780)
            assertTrue(pixels.count{it==colours.warningLand}>200);assertTrue(pixels.count{it==colours.warning}>100)
            assertTrue(pixels.count{it==colours.land}>5000);assertTrue(pixels.count{it==colours.selected}>20)
            val text=Color(colours.text).luminance();val background=Color(colours.background).luminance()
            assertTrue((maxOf(text,background)+.05f)/(minOf(text,background)+.05f)>7f)
            File(output,"warning-map-$dark.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            val clear=state.copy(titles=emptyMap())
            WarningMapPainter().draw(android.graphics.Canvas(bitmap),720f,780f,2f,1.5f,atlas,clear,null,colours)
            bitmap.getPixels(pixels,0,720,0,0,720,780)
            assertEquals(0,pixels.count{it==colours.warning||it==colours.warningLand})
        }
    }
    @Test fun scheduledOfficialAreasUseAmberRatherThanCurrentWarningRedInBothThemes() {
        val at=Instant.parse("2026-10-29T21:01:00Z")
        fun captured(name:String)=javaClass.getResourceAsStream("/cwa-warning-20261030/$name")!!.bufferedReader().use{it.readText()}
        val bulletin=CwaBulletins.parse(captured("bulletins.json"),at).single()
        val feed=CwaNationalWarnings(CwaNationalWarningFeed.parse(captured("counties.json"),at),at,sourceAt=bulletin.updatedAt)
        val state=CwaWarningMapState.build(feed,listOf(bulletin),emptyList(),bulletin.title,at)
        val output=File("build/warning-map-renders").apply{mkdirs()}
        for(dark in listOf(false,true)) {
            val colours=WarningMapColours.forTheme(dark,0xff007aff.toInt())
            val bitmap=Bitmap.createBitmap(720,780,Bitmap.Config.ARGB_8888)
            WarningMapPainter().draw(android.graphics.Canvas(bitmap),720f,780f,2f,1f,atlas(),state,null,colours)
            val pixels=IntArray(720*780);bitmap.getPixels(pixels,0,720,0,0,720,780)
            assertTrue(pixels.count{it==colours.upcomingLand}>200)
            assertTrue(pixels.count{it==colours.upcoming}>100)
            assertEquals(0,pixels.count{it==colours.warning||it==colours.warningLand})
            File(output,"upcoming-warning-$dark.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
}
