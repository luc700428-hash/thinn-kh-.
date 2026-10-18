package io.atmos.weather.typhoon

import io.atmos.weather.alerts.CwaWarning
import java.time.Instant

/** A notice is authoritative only when the actual CWA RSS title explicitly identifies
 * an issued sea / land typhoon warning. Model paths and GDACS events can NEVER trigger it.
 * This is a best-effort RSS fallback; a structured CWA/CAP feed is required for a final
 * geocoded warning map and near-real-time server-initiated push.
 */
enum class TyphoonWarningLevel { SEA, LAND }

data class VerifiedTyphoonWarning(
    val level: TyphoonWarningLevel,
    val issuedAt: Instant,
    val title: String,
    val officialUrl: String,
    val landCounties: List<String>,
    val seaAreas: List<String>,
    val name: String,
) {
    fun localLandWarning(county: String?): Boolean = level == TyphoonWarningLevel.LAND &&
        county != null && landCounties.any { it.replace('臺','台') == county.replace('臺','台') }
}

object TyphoonOfficialWarning {
    private val counties = listOf("基隆市", "台北市", "新北市", "桃園市", "新竹縣", "新竹市", "苗栗縣", "台中市", "彰化縣", "南投縣", "雲林縣", "嘉義縣", "嘉義市", "台南市", "高雄市", "屏東縣", "宜蘭縣", "花蓮縣", "台東縣", "澎湖縣", "金門縣", "連江縣")
    private val seaNames = listOf("台灣北部海面", "台灣東北部海面", "台灣東南部海面", "台灣海峽北部", "台灣海峽南部", "台灣西南部海面", "巴士海峽", "東沙島海面", "台灣南部海面")
    private fun canonical(s:String) = s.replace('臺','台').replace("臺灣", "台灣")
    private fun scope(text:String, type:String): String {
        // Only extract explicit official warning sections. Mentions of a city in a
        // storm position / impact forecast must not become an issued warning county.
        val keys=if(type=="LAND") listOf("陸上警戒區域", "陸上警戒地區", "陸上警戒")
                 else listOf("海上警戒區域", "海上警戒海域", "海上警戒")
        val start=keys.map { text.indexOf(it) }.filter { it>=0 }.minOrNull() ?: return ""
        val colon=text.indexOf('：',start).takeIf { it in (start+2)..(start+16) }
            ?: text.indexOf(':',start).takeIf { it in (start+2)..(start+16) }
            ?: return ""
        val rest=text.substring(colon+1).take(280)
        val cut=listOf("陸上警戒", "海上警戒", "注意事項", "颱風動態", "中心位置", "最新消息", "發布時間", "。", "\n")
            .map { rest.indexOf(it) }.filter{it>=0}.minOrNull() ?: rest.length
        return rest.substring(0,cut)
    }
    fun classify(warning:CwaWarning,now:Instant=Instant.now()): VerifiedTyphoonWarning? {
        if (!warning.link.startsWith("https://www.cwa.gov.tw/")) return null
        val title=canonical(warning.title).replace(Regex("\\s+"), "")
        if (listOf("解除", "取消", "測試", "演練", "消息", "預報").any { title.contains(it) }) return null
        val land=title.contains("海上陸上颱風警報") || title.contains("海上及陸上颱風警報") || title.contains("陸上颱風警報")
        val sea=title.contains("海上颱風警報") || land
        if (!sea) return null
        val age=now.epochSecond-warning.issuedAt.epochSecond
        if(age < -600 || age>6*3600) return null
        val body=canonical(warning.summary)
        val landSection=scope(body,"LAND")
        val seaSection=scope(body,"SEA")
        val landCounties=if(land) counties.filter { landSection.contains(it) } else emptyList()
        val seas=seaNames.filter { seaSection.contains(it) }
        val stormName=Regex("(?:強烈|中度|輕度)颱風([\\p{IsHan}]{2,5})").find(body)?.groupValues?.getOrNull(1)
            ?.takeUnless { it.contains("警報") || it.contains("消息") } ?: "本次颱風"
        return VerifiedTyphoonWarning(if(land)TyphoonWarningLevel.LAND else TyphoonWarningLevel.SEA,
            warning.issuedAt,warning.title,warning.link,landCounties,seas,stormName)
    }
    fun isOfficialCancellation(warning:CwaWarning,now:Instant=Instant.now()):Boolean {
        val title=canonical(warning.title)
        val age=now.epochSecond-warning.issuedAt.epochSecond
        return warning.link.startsWith("https://www.cwa.gov.tw/") &&
            title.contains("颱風") && title.contains("解除") && age in -600L..(6*3600L)
    }
    fun newest(warnings:List<CwaWarning>,now:Instant=Instant.now()): VerifiedTyphoonWarning? =
        warnings.mapNotNull { classify(it,now) }
            .maxWithOrNull(compareBy<VerifiedTyphoonWarning> {it.issuedAt}.thenBy {it.level.ordinal})
}
