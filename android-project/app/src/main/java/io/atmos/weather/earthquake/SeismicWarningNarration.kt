package io.atmos.weather.earthquake

import java.util.Locale

/** Pure validated county-level EEW speech plan. Never infers intensity from magnitude.
 * Only values accepted by SeismicWarningCounties are announced.
 */
object SeismicWarningNarration {
    fun build(epicenter:String,magnitude:Double?,raw:Map<String,String>,localName:String?,depthKm:Double?=null):String {
        val counties=SeismicWarningCounties.fromProvider(raw)
        val localCounty=localName?.replace('台','臺')?.removeSuffix("附近")?.trim()
        val matched=localCounty?.let { name -> counties.firstOrNull { name==it.county || name.contains(it.county) } }
        fun gradeLabel(level:String):String = if(level.endsWith("弱") || level.endsWith("強")) level else "${level}級"
        val location=when {
            localCounty==null -> "尚未取得您的所在地，目前無法確認當地預估震度。"
            matched!=null -> "您所在的${matched.county}，預估震度${gradeLabel(matched.intensity ?: "未提供")}。"
            else -> "${localCounty}目前沒有來源提供的預估震度。"
        }
        val center=epicenter.trim().takeIf { it.isNotBlank() }?.let { "震央位於${it}。" } ?: "震央位置待確認。"
        val magDescription=magnitude?.takeIf { it.isFinite() && it>0.0 }?.let { "規模${String.format(Locale.TAIWAN,"%.1f",it)}。" } ?: "規模尚未提供。"
        val depthDescription=depthKm?.takeIf { it.isFinite() && it in 0.0..700.0 }
            ?.let { "深度${String.format(Locale.TAIWAN,"%.1f",it)}公里。" } ?: "深度尚未提供。"
        val levelOrder=listOf("0","1","2","3","4","5弱","5強","6弱","6強","7")
        val others=counties.filterNot { it.county==matched?.county }
            .sortedWith(compareByDescending<CountyIntensity> { levelOrder.indexOf(it.intensity) }
                .thenBy { SeismicWarningCounties.counties.indexOf(it.county) })
        val overview=if(others.isEmpty()) "其他縣市目前沒有可靠的預估震度資料。"
            else "來源提供的其他縣市預估震度為："+
                others.joinToString("；") { "${it.county}${gradeLabel(it.intensity ?: "未提供")}" }+"。"
        return "收到地震速報！慎防強烈搖晃！趴下、掩護、穩住！${location}${center}${magDescription}${depthDescription}${overview}"
    }
}
