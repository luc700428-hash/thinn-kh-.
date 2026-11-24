package io.atmos.weather.visual

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.*

/** Which landmarks stand behind the weather for a place: by its name first, then by distance. */
object LandmarkPlaces {
    data class Place(val name: String, val aliases: List<String>, val lat: Double, val lon: Double, val radiusKm: Double,
                     val landmarks: List<LandmarkSpec>, val taiwan: Boolean)
    private fun tw(name: String, lat: Double, lon: Double, vararg l: LandmarkSpec) =
        Place(name, listOf(name, name.replace('臺', '台')), lat, lon, 60.0, l.toList(), true)
    private fun abroad(name: String, aliases: List<String>, lat: Double, lon: Double, vararg l: LandmarkSpec) =
        Place(name, aliases, lat, lon, 70.0, l.toList(), false)
    private val A = LandmarkArt

    val places: List<Place> = listOf(
        tw("臺北市", 25.06, 121.55, A.taipei101, A.grandHotel),
        tw("新北市", 24.95, 121.50, A.tamkangBridge, A.loversBridge),
        tw("基隆市", 25.13, 121.74, A.keelungSign),
        tw("桃園市", 24.93, 121.22, A.daxiBridge, A.airportTower),
        tw("新竹市", 24.80, 120.97, A.hsinchuGate),
        tw("新竹縣", 24.70, 121.15, A.neiwanBridge),
        tw("苗栗縣", 24.50, 120.90, A.longtengBridge),
        tw("臺中市", 24.20, 120.80, A.taichungOpera, A.taichungStation),
        tw("彰化縣", 24.00, 120.48, A.baguaBuddha),
        tw("南投縣", 23.85, 120.95, A.sunMoonLake),
        tw("雲林縣", 23.70, 120.40, A.beigangTemple, A.xiluoBridge),
        tw("嘉義市", 23.48, 120.45, A.chiayiFountain),
        tw("嘉義縣", 23.45, 120.60, A.alishan),
        tw("臺南市", 23.10, 120.25, A.chihkan, A.anpingFort),
        tw("高雄市", 22.85, 120.50, A.kaohsiung85, A.lotusPagodas),
        tw("屏東縣", 22.40, 120.60, A.eluanbiLighthouse),
        tw("宜蘭縣", 24.65, 121.70, A.turtleIsland, A.lanyangMuseum),
        tw("花蓮縣", 23.80, 121.45, A.qingshuiCliff),
        tw("臺東縣", 22.85, 121.05, A.sanxiantai),
        tw("澎湖縣", 23.57, 119.60, A.penghuBridge, A.twinHearts),
        tw("金門縣", 24.44, 118.37, A.juguangTower, A.windLion),
        tw("連江縣", 26.16, 119.95, A.qinbi, A.dongyinLighthouse),
        abroad("東京", listOf("東京", "Tokyo"), 35.68, 139.76, A.skytree, A.tokyoTower),
        abroad("大阪", listOf("大阪", "Osaka"), 34.69, 135.50, A.osakaCastle),
        abroad("京都", listOf("京都", "Kyoto"), 35.00, 135.77, A.kyotoPagoda),
        abroad("首爾", listOf("首爾", "Seoul"), 37.56, 126.98, A.namsanTower),
        abroad("香港", listOf("香港", "Hong Kong"), 22.30, 114.17, A.hongKong),
        abroad("上海", listOf("上海", "Shanghai"), 31.23, 121.47, A.pearlTower),
        abroad("新加坡", listOf("新加坡", "Singapore"), 1.29, 103.85, A.marinaBaySands),
        abroad("曼谷", listOf("曼谷", "Bangkok"), 13.75, 100.50, A.watArun),
        abroad("巴黎", listOf("巴黎", "Paris"), 48.86, 2.35, A.eiffel),
        abroad("倫敦", listOf("倫敦", "London"), 51.51, -0.13, A.bigBen, A.towerBridge),
        abroad("紐約", listOf("紐約", "New York"), 40.71, -74.00, A.empireState, A.liberty),
        abroad("舊金山", listOf("舊金山", "San Francisco"), 37.77, -122.42, A.goldenGate),
        abroad("雪梨", listOf("雪梨", "Sydney"), -33.87, 151.21, A.operaHouse),
        abroad("杜拜", listOf("杜拜", "Dubai"), 25.20, 55.27, A.burjKhalifa),
        abroad("羅馬", listOf("羅馬", "Rome"), 41.90, 12.50, A.colosseum),
    )

    private fun km(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 12742 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun find(name: String, lat: Double, lon: Double): Place? {
        // A county or city named in the place name wins; the longest name first (新竹縣 before 新竹).
        places.flatMap { p -> p.aliases.map { it to p } }.sortedByDescending { it.first.length }
            .firstOrNull { name.contains(it.first, ignoreCase = true) }?.let { return it.second }
        if (!lat.isFinite() || !lon.isFinite()) return null
        // Otherwise the nearest place within its reach; Taipei City is small, so it needs to be close.
        return places.map { it to km(lat, lon, it.lat, it.lon) }
            .filter { (p, d) -> d <= if (p.name == "臺北市") 9.0 else p.radiusKm }
            .minByOrNull { it.second }?.first
    }
}

/**
 * The landmarks behind the weather: the main one on the right, a second one smaller on the left,
 * standing on a soft horizon in the colours of the current sky. Rain and fog thin them out; at
 * night their windows light up. The ground dissolves into the page so cards sit naturally above.
 */
@Composable
fun LandmarkScene(cityName: String, latitude: Double, longitude: Double, skyMiddle: Color, skyBottom: Color,
                  night: Boolean, haze: Float, modifier: Modifier = Modifier) {
    val place = remember(cityName, latitude, longitude) { LandmarkPlaces.find(cityName, latitude, longitude) } ?: return
    val picks = remember(place) {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        val list = place.landmarks
        // The best-known landmark always leads; the others take turns beside it day by day.
        if (list.size <= 1) list else listOf(list[0], list[1 + day % (list.size - 1)])
    }
    Canvas(modifier.semantics { contentDescription = "${place.name}地標：" + picks.joinToString("、") { it.name } }) {
        val thin = (1f - haze.coerceIn(0f, 1f) * .55f)
        val base = lerp(skyBottom, if (night) Color(0xFF070B16) else Color(0xFF223041), if (night) .55f else .38f)
        val ink = LandmarkInk(base.copy(alpha = (if (night) .86f else .62f) * thin),
            (if (night) Color(0xFF9FB4D8) else Color.White).copy(alpha = .34f * thin),
            Color(0xFFFFD58A).copy(alpha = .92f * thin), night)
        val far = LandmarkInk(lerp(base, skyMiddle, .45f).copy(alpha = ink.body.alpha * .55f), ink.detail.copy(alpha = 0f), ink.light.copy(alpha = .5f * thin), night)
        val ground = size.height * .9f
        // Distant ridge across the whole width.
        val ridge = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, ground)
            val n = 7
            for (i in 0..n) {
                val x = size.width * i / n
                val y = ground - size.height * (.05f + .035f * sin(i * 1.7f + 1f) + .02f * cos(i * 3.1f))
                if (i == 0) lineTo(x, y) else quadraticTo(x - size.width / n / 2, y - size.height * .02f, x, y)
            }
            lineTo(size.width, ground); close()
        }
        drawPath(ridge, far.body)
        fun place(spec: LandmarkSpec, cx: Float, height: Float, inkUse: LandmarkInk) {
            val unit = min(height / 100f, size.width * .62f / spec.width)
            LandmarkPen(this, cx, ground, unit, inkUse).apply(spec.draw)
        }
        if (picks.size > 1) place(picks[1], size.width * .2f, size.height * .3f, LandmarkInk(lerp(ink.body, far.body, .35f), ink.detail, ink.light, night))
        place(picks[0], size.width * .7f, size.height * .5f, ink)
        // The ground fades into the page instead of ending in a hard line.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, skyMiddle.copy(alpha = .0f), skyMiddle),
            startY = ground - size.height * .08f, endY = size.height), Offset(0f, ground - size.height * .08f),
            androidx.compose.ui.geometry.Size(size.width, size.height - ground + size.height * .08f))
    }
}
