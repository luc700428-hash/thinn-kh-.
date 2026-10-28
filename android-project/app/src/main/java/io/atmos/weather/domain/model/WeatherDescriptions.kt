package io.atmos.weather.domain.model

/** WMO interpretation, never a rain-intensity guess from precipitation probability. */
object WeatherDescriptions {
    fun fromCode(code:Int?,day:Boolean=true):String?=when(code) {
        0->if(day)"晴天" else "晴朗夜晚"
        1->if(day)"晴時多雲" else "夜間晴時多雲"
        2->"局部多雲";3->"陰天";45->"起霧";48->"霧凇"
        51->"毛毛雨";53->"中度毛毛雨";55->"較強毛毛雨"
        56->"輕度凍毛毛雨";57->"強凍毛毛雨"
        61->"小雨";63->"中雨";65->"大雨";66->"輕度凍雨";67->"強凍雨"
        80->"短暫小雨";81->"短暫中雨";82->"短暫大雨"
        71->"小雪";73->"中雪";75->"大雪";77->"雪粒";85->"短暫小雪";86->"短暫大雪"
        95->"雷雨";96->"雷雨伴隨冰雹";99->"強雷雨伴隨冰雹"
        else->null
    }
    fun label(condition:WeatherCondition?,code:Int?,visibilityM:Double?=null,day:Boolean=true):String {
        when(condition) {
            WeatherCondition.DUST_STORM->return "沙塵暴"
            WeatherCondition.DUST->return "揚塵"
            WeatherCondition.HAZE->return "霾"
            WeatherCondition.FOG->return if(visibilityM!=null&&visibilityM<200)"濃霧" else if(code==48)"霧凇" else "起霧"
            else->Unit
        }
        val precipitation=condition in setOf(WeatherCondition.RAIN,WeatherCondition.FREEZING_RAIN,WeatherCondition.SLEET,WeatherCondition.SNOW,WeatherCondition.HAIL,WeatherCondition.THUNDERSTORM)
        if(!(precipitation&&code in 0..3))fromCode(code,day)?.let{return it}
        return when(condition) {
            WeatherCondition.CLEAR->if(day)"晴天" else "晴朗夜晚"
            WeatherCondition.PARTLY_CLOUDY->if(day)"晴時多雲" else "夜間晴時多雲"
            WeatherCondition.CLOUDY->"多雲";WeatherCondition.RAIN->"降雨"
            WeatherCondition.SNOW->"降雪";WeatherCondition.SLEET->"雨夾雪"
            WeatherCondition.FREEZING_RAIN->"凍雨";WeatherCondition.HAIL->"冰雹"
            WeatherCondition.THUNDERSTORM->"雷雨";else->"天氣資料更新中"
        }
    }
    fun current(w:WeatherSnapshot)=if(w.condition in setOf(WeatherCondition.DUST_STORM,WeatherCondition.DUST,WeatherCondition.HAZE))label(w.condition,w.weatherCode,w.visibilityM,w.isDay) else w.observationDescription ?: label(w.condition,w.weatherCode,w.visibilityM,w.isDay)
}
