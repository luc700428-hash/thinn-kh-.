package io.atmos.weather.earthquake

/** Non-Android script formatter: ONLY values validated by provider adapters.
 * Safety instruction and local impact come before the potentially long county rollcall.
 * No predicted arrival time is fabricated.
 */
object SeismicVoiceFormatter {
    fun text(epicenter:String,magnitude:Double?,counties:List<CountyIntensity>,location:String?,drill:Boolean=false):String {
        val city=location.orEmpty().replace('台','臺').removeSuffix("附近").trim()
        val local=counties.firstOrNull {county-> city.isNotBlank() && (city.contains(county.county)||county.county.contains(city)) }
        val localLine=when {
            city.isBlank()->"目前無法確認您的所在縣市，請就近避難。"
            local?.intensity!=null->"您所在的${local.county}，預估震度${local.intensity}級。"
            else->"您所在的${city}，目前尚無來源確認的震度預估。"
        }
        val valid=counties.filter{it.intensity!=null && it.intensity!="0" && it.county!=local?.county}
        val maximum=counties.mapNotNull{it.intensity}.maxByOrNull {rank(it)}
        val highest=maximum?.takeIf {rank(it)>0}?.let {"來源提供的最高預估震度為${it}級。"}.orEmpty()
        val countyLine=if(valid.isEmpty()) "其他縣市震度資料尚未提供。" else {
            val ordered=valid.sortedWith(compareByDescending<CountyIntensity> { rank(it.intensity) }.thenBy { it.county })
            "其他有預估震度的縣市，包括"+ordered.joinToString("，") { "${it.county}${it.intensity}級" }+"。"
        }
        val mag=magnitude?.takeIf {it.isFinite() && it in 0.0..10.0}?.let {"，規模${"%.1f".format(java.util.Locale.TAIWAN,it)}"}.orEmpty()
        val opener=if(drill) "這是地震模擬演練，不是真實地震速報。" else "地震速報，第三方來源。"
        return "$opener 立即趴下、掩護、穩住，保護頭部。$localLine $highest 震央${epicenter}$mag。$countyLine 請持續留意正式地震資訊。"
    }
    private fun rank(grade:String?):Int=when(grade){
        "7"->9;"6強"->8;"6弱"->7;"5強"->6;"5弱"->5;"4"->4;"3"->3;"2"->2;"1"->1;else->0
    }
}
