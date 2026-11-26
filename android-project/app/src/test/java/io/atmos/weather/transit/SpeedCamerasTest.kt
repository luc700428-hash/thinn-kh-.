package io.atmos.weather.transit

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SpeedCamerasTest {
    private val net = Freeways.parse(File("src/main/assets/roads/freeways.txt").readText())
    private val moi = SpeedCameras.parseMoi(File("src/main/assets/roads/speed_cameras.csv").readText())
    private val extra = SpeedCameras.parseExtra(File("src/main/assets/roads/enforcement_extra.txt").readText())
    private val index = SpeedCameras.Index(moi + extra, net)

    @Test fun directionsReadTheWayThePoliceWriteThem() {
        fun d(s: String) = SpeedCameras.direction(s)?.toList()
        assertEquals(listOf(180), d("北向南")); assertEquals(listOf(0), d("南往北")); assertEquals(listOf(180), d("往南"))
        assertEquals(listOf(180), d("南向")); assertEquals(listOf(0), d("北向")); assertEquals(listOf(45), d("西南向東北"))
        assertEquals(listOf(270), d("東往西(區間測速)")); assertEquals(listOf(180), d("南下車道")); assertEquals(listOf(0), d("北上方向"))
        assertEquals(listOf(90), d(" 往東"))
        listOf("雙向", "南北雙向", "東西雙向(區間測速)", "南北向", "東西向", "往大溪方向", "南向60北向70", "單向", "往南北").forEach { assertNull(it, d(it)) }
    }

    @Test fun theOfficialListIsCompleteAndOnTheMap() {
        assertTrue(moi.size > 1800)
        assertTrue(moi.all { it.lat in 21.5..26.5 && it.lon in 118.0..122.5 })
        val fwy = moi.first { it.label == "國道一號南向306.1公里" }
        assertEquals(listOf(180), fwy.dirs!!.toList()); assertEquals(110, fwy.limit)
        assertTrue("freeway cameras sit on their carriageway", index.onChain.size > 120)
        assertTrue(extra.count { it.kind == SpeedCameras.Kind.ZONE } >= 10)
        assertFalse(moi.any { it.label.contains("中國") })
    }

    @Test fun onTheFreewayTheCameraAheadIsMeasuredAlongTheRoadAndOnlyForItsDirection() {
        val (cam, at) = index.onChain.entries.first { it.key.label == "國道一號南向306.1公里" }.let { it.key to it.value }
        val spot = Freeways.Spot(at.chain, (at.at - 800).coerceAtLeast(0.0), 0.0)
        val p = Freeways.pointAlong(net, spot, 0.0)!!; val q = Freeways.pointAlong(net, spot, 200.0)!!
        val heading = DrivingMode.bearing(p.first, p.second, q.first, q.second)
        val car = Freeways.match(net, p.first, p.second, heading)!!
        val a = SpeedCameras.ahead(index, net, car, p.first, p.second, heading, 1500.0)!!
        assertEquals(cam, a.camera)
        assertTrue("${a.metres}", a.metres in 760.0..840.0)
        // Northbound at the same place: this southbound camera is not yours.
        val back = Freeways.match(net, p.first, p.second, (heading + 180) % 360)
        val b = SpeedCameras.ahead(index, net, back, p.first, p.second, (heading + 180) % 360, 1500.0)
        assertNotEquals(cam, b?.camera)
        // Already past it: nothing from it.
        val past = Freeways.Spot(at.chain, at.at + 50, 0.0)
        assertNotEquals(cam, SpeedCameras.ahead(index, net, past, cam.lat, cam.lon, heading, 1500.0)?.camera)
    }

    @Test fun inTownTheCameraMustWatchYourWayAndBeOnYourLine() {
        val cam = moi.first { it.dirs != null && it.label.contains("臺北市").not() && it.lat in 24.9..25.2 && !index.onChain.containsKey(it) }
        val dir = cam.dirs!![0].toDouble()
        val (lat, lon) = DrivingMode.pointAhead(cam.lat, cam.lon, (dir + 180) % 360, .3)
        val a = SpeedCameras.ahead(index, null, null, lat, lon, dir, 600.0)!!
        assertEquals(cam, a.camera); assertTrue(a.metres in 280.0..320.0)
        // Driving the other way past it: it does not watch you.
        val (lat2, lon2) = DrivingMode.pointAhead(cam.lat, cam.lon, dir, .3)
        assertNotEquals(cam, SpeedCameras.ahead(index, null, null, lat2, lon2, (dir + 180) % 360, 600.0)?.camera)
        // A parallel street 120 m to the side: not on your line.
        val (lat3, lon3) = DrivingMode.pointAhead(lat, lon, (dir + 90) % 360, .12)
        assertNotEquals(cam, SpeedCameras.ahead(index, null, null, lat3, lon3, dir, 600.0)?.camera)
    }

    @Test fun aZoneAveragesFromItsStartToItsEnd() {
        val z = extra.first { it.zone != null }.zone!!
        val d = Freeways.metres(z.fromLat, z.fromLon, z.toLat, z.toLon)
        var run = SpeedCameras.followZone(null, index, z.fromLat, z.fromLon, 0L)
        assertNotNull(run)
        // Halfway after the time 60 km/h would take.
        val mid = (z.fromLat + z.toLat) / 2 to (z.fromLon + z.toLon) / 2
        run = SpeedCameras.followZone(run, index, mid.first, mid.second, (d / 2 / (60 / 3.6) * 1000).toLong())
        assertEquals(60.0, run!!.averageKmh((d / 2 / (60 / 3.6) * 1000).toLong())!!.toDouble(), 2.0)
        assertNull(SpeedCameras.followZone(run, index, z.toLat, z.toLon, 600_000L))
    }

    @Test fun theVoiceSaysWhatAndHowFar() {
        val c = SpeedCameras.Camera(SpeedCameras.Kind.SPEED, 25.0, 121.5, intArrayOf(0), 60, "x")
        assertEquals("前方 400 公尺，固定測速，速限 60", SpeedCameras.speech(SpeedCameras.Alert(c, 380.0)))
        assertEquals(300.0, SpeedCameras.lookahead(30), .1); assertEquals(1500.0, SpeedCameras.lookahead(200), .1)
        assertEquals(916.7, SpeedCameras.lookahead(110), .1)
    }
}
