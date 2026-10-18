package io.atmos.weather.earthquake

import java.util.Locale

/** Pure validated county-level EEW speech plan. Never infers intensity from magnitude.
 * Only values accepted by SeismicWarningCounties are announced.
 */
object SeismicWarningNarration {
    fun build(epicenter:String,magnitude:Double?,raw:Map<String,String>,localName:String?):String {
        val counties=SeismicWarningCounties.fromProvider(raw)
        val localCounty=localName?.replace('台','臺')?.removeSuffix("附近")?.trim()
        val matched=localCounty?.let { name -> counties.firstOrNull { name==it.county || name.contains(it.county) } }
        val location=when {
            localCounty==null -> "尚未取得您的所在地，無法提供當地預估震度。"
            matched!=null -> "您所在的${matched.county}，預估震度${matched.intensity}級，請立即保護自己。"
            else -> "${localCounty}目前沒有來源提供的預估震度，請先採取防護。"
        }
        val magDescription=magnitude?.let { "規模${String.format(Locale.TAIWAN,"%.1f",it)}。" }.orEmpty()
        val levelOrder=listOf("0","1","2","3","4","5弱","5強","6弱","6強","7")
        val others=counties.filterNot { it.county==matched?.county }
            .sortedWith(compareByDescending<CountyIntensity> { levelOrder.indexOf(it.intensity) }
                .thenBy { SeismicWarningCounties.counties.indexOf(it.county) })
        val overview=if(others.isEmpty()) "其他縣市目前沒有可靠的預估震度資料。"
            else "來源提供的其他縣市預估震度為："+
                others.joinToString("；") { "${it.county}${it.intensity}級" }+"。"
        return "地震速報，第三方資料。${location}震央${epicenter}。${magDescription}${overview}趴下、掩護、穩住。"
    }
}
