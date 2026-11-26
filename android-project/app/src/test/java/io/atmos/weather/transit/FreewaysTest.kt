package io.atmos.weather.transit

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FreewaysTest {
    private val net = Freeways.parse(File("src/main/assets/roads/freeways.txt").readText())

    /** A spot halfway between two places on the chain that carries both, heading along it. */
    private fun between(a: String, b: String): Pair<Freeways.Chain, Double> {
        val c = net.chains.first { ch -> ch.places.any { it.name == a } && ch.places.any { it.name == b } && ch.places.first { it.name == a }.at < ch.places.first { it.name == b }.at }
        return c to (c.places.first { it.name == a }.at + c.places.first { it.name == b }.at) / 2
    }

    @Test fun theAssetCoversTheMainFreewaysAndTheirServiceAreas() {
        val roads = net.chains.map { it.road }.toSet()
        listOf("國道1號", "國道3號", "國道5號", "國道6號", "台61線").forEach { assertTrue(it, it in roads) }
        val names = net.chains.flatMap { c -> c.places.map { it.name } }.toSet()
        listOf("湖口服務區", "清水服務區", "西螺服務區", "關西服務區", "南投服務區", "木柵休息站", "林口交流道", "台中交流道").forEach { assertTrue(it, it in names) }
        assertFalse(names.any { it.contains("中國") || it.contains("中華台北") })
    }

    @Test fun southboundOnFreewayOneTheNextExitIsTheOneAhead() {
        val (chain, at) = between("大雅交流道", "台中交流道")
        val p = Freeways.pointAlong(net, Freeways.Spot(chain, 0.0, 0.0), at)!!
        val q = Freeways.pointAlong(net, Freeways.Spot(chain, 0.0, 0.0), at + 300)!!
        val heading = DrivingMode.bearing(p.first, p.second, q.first, q.second)
        val spot = Freeways.match(net, p.first, p.second, heading)!!
        assertEquals(chain.id, spot.chain.id)
        val s = Freeways.status(net, spot)
        assertEquals("國道1號", s.road)
        assertEquals("南下", s.direction)
        assertEquals("台中交流道", s.exit!!.place.name)
        assertEquals("178", s.exit!!.place.exit)
        assertTrue(s.exit!!.metres in 1500.0..3500.0)
        assertEquals("南屯交流道", s.then!!.place.name)
        assertEquals("西螺服務區", s.service!!.place.name)
        assertTrue(s.service!!.metres > s.exit!!.metres)
    }

    @Test fun theOtherCarriagewayAndACrossingHeadingDoNotMatchTheWrongWay() {
        val (chain, at) = between("大雅交流道", "台中交流道")
        val p = Freeways.pointAlong(net, Freeways.Spot(chain, 0.0, 0.0), at)!!
        val q = Freeways.pointAlong(net, Freeways.Spot(chain, 0.0, 0.0), at + 300)!!
        val heading = DrivingMode.bearing(p.first, p.second, q.first, q.second)
        // Driving the opposite way at the same spot: the northbound carriageway, next exit 大雅.
        val back = Freeways.match(net, p.first, p.second, (heading + 180) % 360)!!
        assertNotEquals(chain.id, back.chain.id)
        val s = Freeways.status(net, back)
        assertEquals("北上", s.direction)
        assertEquals("大雅交流道", s.exit!!.place.name)
        // Crossing it at right angles on a road below: no match.
        assertNull(Freeways.match(net, p.first, p.second, (heading + 90) % 360))
        // A kilometre off the freeway: no match.
        assertNull(Freeways.match(net, p.first + .01, p.second, heading))
    }

    @Test fun distancesReadLikeRoadSigns() {
        assertEquals("800 公尺", Freeways.distance(790.0))
        assertEquals("2.4 公里", Freeways.distance(2420.0))
        assertEquals("36 公里", Freeways.distance(35_600.0))
        assertEquals("國1", Freeways.short("國道1號")); assertEquals("台61", Freeways.short("台61線")); assertEquals("國3甲", Freeways.short("國道3號甲線"))
    }
}
