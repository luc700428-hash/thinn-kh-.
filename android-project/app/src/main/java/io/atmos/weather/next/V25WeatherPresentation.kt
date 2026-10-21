package io.atmos.weather.next

/** Shared status vocabulary for homepage, widgets, alarms and weather alerts.
 * This is presentation only. Unknown/missing input NEVER becomes an official warning.
 */
object V25WeatherPresentation {
    data class Scene(
        val name: String,
        val headline: String,
        val advice: String,
        val glowArgb: Long,
        val emblem: String,
        val precipitation: Boolean,
        val needsAttention: Boolean
    )
    fun from(condition: String?, night: Boolean, rainProbability: Int? = null): Scene {
        val risk = rainProbability?.takeIf { it in 0..100 }
        fun scene(name: String, headline: String, advice: String, color: Long, emblem: String,
                  precipitation: Boolean=false, attention: Boolean=false) =
            Scene(name,headline,advice,color,emblem,precipitation,attention)
        return when(condition?.uppercase()) {
            "CLEAR", "SUNNY" -> if(night)
                scene("晴朗夜空","今晚，天空很安靜","晚間出門留意氣溫變化",0xFF94BAF4L,"☾")
                else scene("晴朗","陽光正好","出門前別忘了補充水分",0xFFFFCF87L,"☀")
            "PARTLY_CLOUDY", "MOSTLY_CLEAR" -> scene("晴時多雲","雲朵與陽光交錯","看看接下來幾小時的變化",0xFFB2D7F5L,"☀")
            "CLOUDY", "OVERCAST" -> scene("多雲","天空有些雲","出門前查看最新降雨預報",0xFFB7CDE3L,"☁")
            "FOG" -> scene("有霧","能見度可能降低","開車請放慢速度",0xFFD4DAE1L,"◌",attention=true)
            "HAZE" -> scene("霾","空氣有些朦朧","留意最新空氣品質資訊",0xFFCCBED2L,"◌")
            "DUST", "DUST_STORM" -> scene("揚塵","外出留意空氣品質","敏感族群可減少戶外活動",0xFFE3BE94L,"◌",attention=true)
            "DRIZZLE" -> scene("毛毛雨","有些細雨","可以帶一把輕便的傘",0xFF83BEE1L,"☂",precipitation=true)
            "RAIN", "HEAVY_RAIN" -> scene("降雨","記得帶雨具","雨勢可能變化，請留意道路狀況",0xFF70ACDFL,"☂",precipitation=true)
            "THUNDERSTORM" -> scene("雷雨","留意雷電與強降雨","避免在空曠處或樹下停留",0xFFA08ED8L,"⚡",precipitation=true,attention=true)
            "FREEZING_RAIN" -> scene("凍雨","路面可能濕滑","外出請注意道路安全",0xFFBBDDEFL,"❄",precipitation=true,attention=true)
            "SLEET" -> scene("雨夾雪","留意低溫與降水","注意保暖及路面狀況",0xFFC7DCF3L,"❄",precipitation=true)
            "SNOW" -> scene("降雪","天空正飄著雪","出門注意保暖",0xFFD6ECFAL,"❄",precipitation=true)
            "HAIL" -> scene("冰雹","小心冰雹與強對流","請盡量待在安全室內",0xFFB5D7EAL,"◆",precipitation=true,attention=true)
            else -> scene("最新天氣","正在整理天氣資訊","可下拉重新整理觀測資料",0xFFA7CDE3L,"✦")
        }.let { picked ->
            if (risk != null && risk >= 75 && !picked.precipitation && !picked.needsAttention)
                picked.copy(advice="未來降雨機率可能偏高，記得帶傘") else picked
        }
    }
}
