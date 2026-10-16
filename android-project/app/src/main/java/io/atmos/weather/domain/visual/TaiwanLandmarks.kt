package io.atmos.weather.domain.visual

import io.atmos.weather.domain.model.GeoPoint

/** County-level visual routing. Approximate fallback is deliberately NOT a legal boundary map. */
object TaiwanLandmarks {
    val counties = listOf(
        "台北市", "新北市", "桃園市", "台中市", "台南市", "高雄市",
        "基隆市", "新竹市", "嘉義市", "新竹縣", "苗栗縣", "彰化縣",
        "南投縣", "雲林縣", "嘉義縣", "屏東縣", "宜蘭縣", "花蓮縣",
        "台東縣", "澎湖縣", "金門縣", "連江縣"
    )
    val motifs = mapOf(
        "台北市" to "台北101", "新北市" to "淡江大橋", "桃園市" to "航空城",
        "台中市" to "國家歌劇院", "台南市" to "赤崁樓", "高雄市" to "港都85大樓",
        "基隆市" to "港口燈塔", "新竹市" to "東門城", "嘉義市" to "噴水圓環",
        "新竹縣" to "客家聚落", "苗栗縣" to "龍騰斷橋", "彰化縣" to "八卦山大佛",
        "南投縣" to "日月潭", "雲林縣" to "西螺大橋", "嘉義縣" to "阿里山森林鐵道",
        "屏東縣" to "鵝鑾鼻燈塔", "宜蘭縣" to "蘭陽平原", "花蓮縣" to "太魯閣山海",
        "台東縣" to "海岸熱氣球", "澎湖縣" to "玄武岩海岸", "金門縣" to "閩南古厝",
        "連江縣" to "馬祖石屋聚落"
    )
    private val aliases = mapOf("台北" to "台北市", "新北" to "新北市", "桃園" to "桃園市", "台中" to "台中市",
        "台南" to "台南市", "高雄" to "高雄市", "嘉義" to "嘉義市", "新竹" to "新竹市")
    private val centers = mapOf(
        "台北市" to (25.033 to 121.565), "新北市" to (25.011 to 121.466),
        "桃園市" to (24.994 to 121.301), "台中市" to (24.148 to 120.674),
        "台南市" to (22.999 to 120.227), "高雄市" to (22.627 to 120.301),
        "基隆市" to (25.128 to 121.742), "新竹市" to (24.814 to 120.968),
        "嘉義市" to (23.480 to 120.449), "新竹縣" to (24.839 to 121.018),
        "苗栗縣" to (24.560 to 120.821), "彰化縣" to (24.075 to 120.541),
        "南投縣" to (23.961 to 120.972), "雲林縣" to (23.709 to 120.432),
        "嘉義縣" to (23.452 to 120.256), "屏東縣" to (22.552 to 120.549),
        "宜蘭縣" to (24.702 to 121.738), "花蓮縣" to (23.991 to 121.611),
        "台東縣" to (22.758 to 121.144), "澎湖縣" to (23.571 to 119.579),
        "金門縣" to (24.449 to 118.377), "連江縣" to (26.160 to 119.950)
    )
    fun countyFor(label: String, point: GeoPoint): String? {
        val name = label.replace('臺','台')
        counties.firstOrNull { name.contains(it) }?.let { return it }
        aliases.entries.firstOrNull { (key,_) -> name.contains(key) }?.let { return it.value }
        if (point.latitude !in 21.7..26.5 || point.longitude !in 118.0..122.2) return null
        // Rural fallback is approximate. Prefer Android ReverseGeocoder's administrative county.
        return centers.minByOrNull { (_,p) ->
            val dy = (p.first - point.latitude) * 111.0
            val dx = (p.second - point.longitude) * 100.0
            dx * dx + dy * dy
        }?.key
    }
    fun title(county: String) = motifs[county] ?: county
}
