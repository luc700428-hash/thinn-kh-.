package io.atmos.weather.notifications

/**
 * What an island says, decided before any vendor payload is built.
 *
 * A compact island has room for a glyph, one short label beside it and one value on the far
 * side. Both are composed here from what the producer actually sent, so a whole word or a
 * real number is shown. Nothing is cut to a fixed number of characters mid-word, and the
 * same label is never repeated in two places.
 */
internal data class IslandCompact(val label:String,val value:String,val accent:String,val urgent:Boolean)

internal object IslandContent {
    const val LABEL_MAX=6
    const val VALUE_MAX=6
    // One hue per kind of activity, carrying meaning rather than decoration.
    const val RED="#FF453A"
    const val ORANGE="#FF9F0A"
    const val BLUE="#0A84FF"
    const val GREEN="#32D74B"
    const val SKY="#64D2FF"

    private val hazards=listOf("颱風","豪雨","大雨","雷雨","低溫","高溫","濃霧","強風","長浪","降雪","大雪","寒流","熱浪","海嘯","淹水","土石流")
    private val estimated=Regex("預估\\s*([0-7])\\s*([強弱+\\-]?)\\s*級")
    private val shaking=Regex("震度\\s*([0-7])\\s*([強弱+\\-]?)")
    private val magnitudeValue=Regex("(?:M|規模)\\s*([0-9](?:\\.[0-9])?)")
    private val notices=Regex("([0-9]{1,3})\\s*則")
    private val clockValue=Regex("(?<![0-9])([0-2]?[0-9]:[0-5][0-9])(?![0-9])")
    private val minuteValue=Regex("([0-9]{1,2})\\s*分")

    /** The whole text when it fits; otherwise cut on a code point and closed with an ellipsis. */
    fun fit(text:String,max:Int):String {
        val clean=text.trim()
        if(max<=0)return ""
        val count=clean.codePointCount(0,clean.length)
        if(count<=max)return clean
        return clean.substring(0,clean.offsetByCodePoints(0,max-1)).trimEnd()+"…"
    }

    internal fun intensity(text:String):String? {
        val m=estimated.find(text)?:shaking.find(text)?:return null
        return "${m.groupValues[1]}${m.groupValues[2]} 級"
    }
    internal fun magnitude(text:String):String?=magnitudeValue.find(text)?.let{"M${it.groupValues[1]}"}

    /** An official warning keeps its own name when that fits; a longer one keeps its hazard and its level. */
    internal fun warningName(title:String):String {
        val name=title.substringAfter("中央氣象署：").substringAfter("中央氣象署:").trim()
        if(name.isEmpty())return "天氣特報"
        if(name.codePointCount(0,name.length)<=LABEL_MAX)return name
        val hazard=hazards.firstOrNull{it in name}?:return fit(name,LABEL_MAX)
        return hazard+if("警報" in name)"警報" else "特報"
    }

    /** First part of "縣市 · 鄉鎮": the county is what a warning is issued for. */
    internal fun place(detail:String):String=detail.split("·","・","、",",","，").firstOrNull()?.trim().orEmpty()

    fun compose(kind:String,title:String,detail:String,primary:String?=null,weatherLabel:String?=null,temperatureC:Int?=null):IslandCompact {
        val text="$title $detail"
        return when(kind) {
            "SEISMIC_EEW"->IslandCompact("地震速報",intensity(text)?:magnitude(text)?:"注意",RED,true)
            "STATION_DETECTION"->IslandCompact("測站檢知",intensity(text)?:"已確認",ORANGE,false)
            "EARTHQUAKE_REPORT"->IslandCompact("地震報告",magnitude(text)?:intensity(text)?:"已發布",ORANGE,false)
            "WEATHER_WARNING"->IslandCompact(warningName(title),fit(place(detail),VALUE_MAX).ifEmpty{"留意"},ORANGE,true)
            "WARNING_SUMMARY"->IslandCompact("天氣特報",notices.find(title)?.let{"${it.groupValues[1]} 則"}?:"多則",ORANGE,true)
            "ALARM_RINGING"->IslandCompact("鬧鐘",clockValue.find(primary.orEmpty())?.value?:clockValue.find(text)?.value?:"響鈴中",ORANGE,true)
            "TIMER_RUNNING"->IslandCompact("計時器","倒數中",ORANGE,false)
            "VERIFIED_MINUTE_RAIN"->IslandCompact("即將下雨",minuteValue.find(title)?.let{"${it.groupValues[1]} 分內"}?:"留意",BLUE,false)
            "TASK_REMINDER"->IslandCompact("待辦事項",fit(title,VALUE_MAX).ifEmpty{"提醒"},BLUE,false)
            "APP_UPDATE"->IslandCompact("軟體更新",if("下載" in text)"下載中" else "新版本",BLUE,false)
            "AMBIENT_WEATHER","WEATHER_BRIEFING"->IslandCompact(
                fit(weatherLabel?.takeIf{it.isNotBlank()&&it!="天氣資料"}?:if(kind=="WEATHER_BRIEFING")"天氣簡報" else "目前天氣",LABEL_MAX),
                temperatureC?.takeIf{it in -50..60}?.let{"$it°"}?:primary?.takeIf{it.isNotBlank()}?.let{fit(it,VALUE_MAX)}?:"已更新",SKY,false)
            "USER_TEST"->IslandCompact("測試通知","正常",GREEN,false)
            else->IslandCompact("天氣通知","查看",SKY,false)
        }
    }
}
