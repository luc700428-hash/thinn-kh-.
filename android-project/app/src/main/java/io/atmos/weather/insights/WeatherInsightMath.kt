package io.atmos.weather.insights

import io.atmos.weather.domain.model.*
import java.time.Instant
import kotlin.math.ln

internal enum class OutdoorMode(val title:String){WALK("散步"),COMMUTE("通勤"),LAUNDRY("晾衣")}
internal data class OutdoorWindow(val hours:List<HourlyWeather>,val rain:Int,val temperature:Double,
    val wind:Double,val uv:Double?,val humidity:Int?,val caution:Boolean,val score:Double,val usesFeelsLike:Boolean)

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
}
