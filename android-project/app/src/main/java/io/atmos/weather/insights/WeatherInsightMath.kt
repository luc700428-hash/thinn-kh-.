package io.atmos.weather.insights

import io.atmos.weather.domain.model.*
import java.time.Instant
import kotlin.math.ln

internal enum class OutdoorMode(val title:String){WALK("散步"),COMMUTE("通勤"),LAUNDRY("晾衣")}
internal data class OutdoorWindow(val hours:List<HourlyWeather>,val rain:Int,val temperature:Double,
    val wind:Double,val uv:Double?,val humidity:Int?,val caution:Boolean,val score:Double,val usesFeelsLike:Boolean)

internal enum class HourLevel(val word:String){GOOD("適合"),FAIR("留意"),POOR("避開")}
internal data class HourRating(val at:Instant,val level:HourLevel,val reason:String)

/** Transparent relative comparison of available forecast hours, not a probability of safety. */
internal object WeatherInsightMath {
    fun dewPoint(temperature:Double,humidity:Int?):Double? {
        if(!temperature.isFinite()||temperature !in -40.0..50.0||humidity==null||humidity !in 1..100)return null
        val g=ln(humidity/100.0)+17.625*temperature/(243.04+temperature)
        return (243.04*g/(17.625-g)).takeIf{it.isFinite() && it<=temperature+.01}
    }
    fun beaufort(kmh:Double?):Int? {
        if(kmh==null||!kmh.isFinite()||kmh<0)return null
        val ms=kmh/3.6
        return listOf(.3,1.6,3.4,5.5,8.0,10.8,13.9,17.2,20.8,24.5,28.5,32.7,37.0,41.5,46.2,51.0,56.1).indexOfFirst{ms<it}.let{if(it<0)17 else it}
    }
    fun windows(weather:WeatherSnapshot,mode:OutdoorMode,now:Instant):List<OutdoorWindow> {
        val upcoming=weather.hourly.filter{!it.at.isBefore(now)&&it.at.isBefore(now.plusSeconds(24*3600))}
            .distinctBy{it.at}.sortedBy{it.at}
        return upcoming.windowed(3).mapNotNull{hours->
            if(hours.zipWithNext().any{(a,b)->b.at.epochSecond-a.at.epochSecond!=3600L})return@mapNotNull null
            val rains=hours.mapNotNull{it.precipitationProbabilityPct?.takeIf{n->n in 0..100}}
            val feels=hours.mapNotNull{it.feelsLikeC?.takeIf{n->n.isFinite()&&n in -90.0..65.0}}
            val usesFeelsLike=feels.size==3
            val temperatures=if(usesFeelsLike)feels else hours.mapNotNull{it.temperatureC?.takeIf{n->n.isFinite()&&n in -90.0..65.0}}
            val winds=hours.mapNotNull{it.windKmh?.takeIf{n->n.isFinite()&&n>=0}}
            // Missing data is never treated as zero rain, calm wind or comfortable temperature.
            if(rains.size!=3||temperatures.size!=3||winds.size!=3)return@mapNotNull null
            val rain=rains.max();val temp=temperatures.average();val wind=winds.max()
            val uv=hours.mapNotNull{it.uvIndex?.takeIf{n->n.isFinite()&&n>=0}}.takeIf{it.size==3}?.max()
            val humidity=hours.mapNotNull{it.humidityPct?.takeIf{n->n in 0..100}}.takeIf{it.size==3}?.max()
            if(mode==OutdoorMode.LAUNDRY && humidity==null)return@mapNotNull null
            val severe=hours.any{it.weatherCode in setOf(56,57,66,67,71,73,75,77,85,86,95,96,99)}
            val caution=severe||wind>=39||temperatures.any{it<4||it>=38}
            val score=rain*(if(mode==OutdoorMode.LAUNDRY)2.0 else 1.1)+wind*.9+
                kotlin.math.abs(temp-22)*1.8+(uv?:0.0)*(if(mode==OutdoorMode.WALK)3 else 1)+
                (if(mode==OutdoorMode.LAUNDRY)(humidity?:0)*.8 else 0.0)+(if(caution)1000 else 0)
            OutdoorWindow(hours,rain,temp,wind,uv,humidity,caution,score,usesFeelsLike)
        }.sortedWith(compareBy({it.score},{it.hours.first().at}))
    }

    /** One plain rating per forecast hour for the 24-hour strip; missing data is never "good". */
    fun hourRatings(weather:WeatherSnapshot,mode:OutdoorMode,now:Instant):List<HourRating> =
        weather.hourly.filter{!it.at.isBefore(now.minusSeconds(3599))&&it.at.isBefore(now.plusSeconds(24*3600))}
            .distinctBy{it.at}.sortedBy{it.at}.map{h->
                val rain=h.precipitationProbabilityPct?.takeIf{it in 0..100}
                val temp=(h.feelsLikeC?:h.temperatureC)?.takeIf{it.isFinite()}
                val wind=h.windKmh?.takeIf{it.isFinite()&&it>=0}
                val humidity=h.humidityPct?.takeIf{it in 0..100}
                val severe=h.weatherCode in setOf(56,57,66,67,71,73,75,77,85,86,95,96,99)
                val poor=when{
                    severe->"可能有雷雨或冰雪"
                    rain!=null&&rain>=60->"降雨機率 $rain%"
                    wind!=null&&wind>=39->"風很大"
                    temp!=null&&(temp<6||temp>=36)->if(temp<6)"非常冷" else "非常熱"
                    mode==OutdoorMode.LAUNDRY&&humidity!=null&&humidity>=88->"濕度 $humidity%，衣服不易乾"
                    else->null
                }
                val fair=rain==null||temp==null||(rain>=30)||(wind!=null&&wind>=25)||temp<12||temp>=32||
                    (mode==OutdoorMode.WALK&&(h.uvIndex?:0.0)>=8)||(mode==OutdoorMode.LAUNDRY&&(humidity==null||humidity>=75))
                HourRating(h.at,if(poor!=null)HourLevel.POOR else if(fair)HourLevel.FAIR else HourLevel.GOOD,poor?:"")
            }
}
