package io.atmos.weather.earthquake

import kotlin.math.*

/** Conservative client-side scope filter, not an authoritative shaking prediction.
 * Taiwan and outlying islands may be affected by offshore events, including abroad.
 * If verified upstream supplies Taiwanese county intensity >= 1, that takes priority.
 * Otherwise require a located event sufficiently close to Taiwan / its outlying islands.
 * Unknown coordinates with vague place text are NOT emergency-alertable.
 */
object TaiwanEewScope {
    private val anchors = listOf(
        25.03 to 121.54, 24.15 to 120.67, 23.48 to 120.45, 22.64 to 120.31,
        22.76 to 121.15, 23.97 to 121.60, 24.76 to 121.75,
        23.56 to 119.58, // Penghu
        24.44 to 118.33, // Kinmen
        26.16 to 119.95, // Matsu
        22.04 to 121.54, // Orchid Island (Lanyu)
        22.67 to 121.49, // Green Island
        22.34 to 120.37, // Xiaoliuqiu
        25.63 to 122.08  // Pengjiayu
    )
    private val taiwanNames = listOf("臺灣", "台灣", "澎湖", "金門", "馬祖", "連江", "蘭嶼", "綠島", "小琉球", "彭佳嶼",
        "臺北", "台北", "新北", "基隆", "宜蘭", "花蓮", "臺東", "台東", "嘉義", "雲林", "苗栗", "南投", "彰化", "新竹", "屏東", "臺南", "台南", "高雄", "臺中", "台中", "桃園")
    fun relevant(e:EewIncident):Boolean {
        // This field must be issued by a verified upstream; never interpret event max intensity as local.
        val countyReadings = SeismicWarningCounties.fromProvider(e.localIntensityByCounty)
        if (countyReadings.any { it.intensity != "0" }) return true
        if (countyReadings.isNotEmpty()) return false // county forecast explicitly zero
        val lat=e.latitude
        val lon=e.longitude
        if(lat!=null && lon!=null && lat.isFinite() && lon.isFinite()) {
            if(lat !in 18.0..29.0 || lon !in 115.0..127.0) return false
            return anchors.any { (y,x) -> haversineKm(lat,lon,y,x) <= 130.0 }
        }
        // Named Taiwan event from a known authenticated EEW provider, without GPS.
        // Generic offshore / Japan text is not sufficient proof of Taiwan relevance.
        return taiwanNames.any { name -> e.epicenter.contains(name) } &&
            !listOf("日本", "沖繩", "琉球", "Japan", "Okinawa").any { e.epicenter.contains(it,true) }
    }
    fun haversineKm(lat1:Double, lon1:Double, lat2:Double, lon2:Double):Double {
        val rad=Math.PI/180.0
        val dLat=(lat2-lat1)*rad
        val dLon=(lon2-lon1)*rad
        val h=sin(dLat/2).pow(2.0)+cos(lat1*rad)*cos(lat2*rad)*sin(dLon/2).pow(2.0)
        return 6371.0 * 2 * asin(sqrt(h.coerceIn(0.0,1.0)))
    }
}
