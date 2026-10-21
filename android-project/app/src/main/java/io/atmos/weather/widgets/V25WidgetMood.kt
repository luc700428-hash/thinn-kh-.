package io.atmos.weather.widgets

/** Original thinn-khì companion voice, not Duolingo art or copy.
 * RemoteViews/Glance are snapshots: the host refreshes them when data changes.
 */
object V25WidgetMood {
    data class Mood(val message: String, val eyebrow: String)

    fun forWeather(condition: String, isNight: Boolean, rainProbability: Int): Mood {
        val risk = rainProbability.coerceIn(-1, 100)
        return when (condition) {
            "THUNDERSTORM" -> Mood("雷雨靠近了，我陪你留意天空", "⚡ 留意雷雨")
            "HAIL" -> Mood("冰雹可能出現，記得遠離窗邊", "◈ 冰雹警示")
            "FREEZING_RAIN" -> Mood("地面可能濕滑，出門慢慢走", "❄ 凍雨")
            "SLEET" -> Mood("下著雨夾雪，注意保暖與路況", "❄ 雨夾雪")
            "SNOW" -> Mood("雪花正落下，今天記得多穿點", "❄ 雪天")
            "RAIN" -> Mood(if (risk >= 70) "雨勢機率偏高，雨具別忘了" else "帶把傘吧，我幫你留意雨勢", "☂ 雨天")
            "FOG" -> Mood("霧氣有點濃，行車請放慢", "◌ 霧天")
            "HAZE" -> Mood("空氣有點朦朧，留意空氣品質", "◌ 霾")
            "DUST" -> Mood("外頭有揚塵，留意空氣狀況", "◌ 揚塵")
            "DUST_STORM" -> Mood("揚塵明顯，外出做好防護", "◌ 沙塵")
            "CLEAR" -> if (isNight) Mood("晚安，今晚的天空交給我", "☾ 晴夜")
                       else Mood("陽光正好，今天適合出門", "☼ 晴朗")
            "PARTLY_CLOUDY" -> if (isNight) Mood("月光藏在雲後，記得休息", "☾ 多雲夜")
                                else Mood("陽光與雲朵正在散步", "☼ 晴時多雲")
            "CLOUDY" -> Mood("雲層慢慢移動，我陪你等天晴", "☁ 多雲")
            else -> Mood("我正在幫你留意最新的天氣", "✦ 天氣")
        }
    }
}
