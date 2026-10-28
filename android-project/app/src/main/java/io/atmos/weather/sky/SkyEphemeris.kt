package io.atmos.weather.sky

import io.atmos.weather.domain.model.WeatherCity
import org.shredzone.commons.suncalc.*
import java.time.*

data class SkyPosition(val at:ZonedDateTime,val fraction:Double,val phase:Double,val moonAzimuth:Double,
    val moonAltitude:Double,val moonDistanceKm:Double,val sunAzimuth:Double,val sunAltitude:Double)
data class SkyDay(val date:LocalDate,val sunrise:ZonedDateTime?,val sunset:ZonedDateTime?,
    val moonrise:ZonedDateTime?,val moonset:ZonedDateTime?)
object SkyEphemeris {
    fun position(city:WeatherCity,at:ZonedDateTime):SkyPosition {
        val lat=city.coordinate.latitude;val lon=city.coordinate.longitude
        require(lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0)
        val moon=MoonPosition.compute().on(at).at(lat,lon).execute()
        val sun=SunPosition.compute().on(at).at(lat,lon).execute()
        val light=MoonIllumination.compute().on(at).at(lat,lon).execute()
        return SkyPosition(at,light.fraction,light.phase,moon.azimuth,moon.altitude,moon.distance,sun.azimuth,sun.altitude)
    }
    fun day(city:WeatherCity,date:LocalDate,zone:ZoneId):SkyDay {
        val at=date.atStartOfDay(zone);val lat=city.coordinate.latitude;val lon=city.coordinate.longitude
        val window=Duration.between(at,date.plusDays(1).atStartOfDay(zone))
        val sun=SunTimes.compute().on(at).at(lat,lon).limit(window).execute()
        val moon=MoonTimes.compute().on(at).at(lat,lon).limit(window).execute()
        fun local(value:ZonedDateTime?)=value?.withZoneSameInstant(zone)?.takeIf{it.toLocalDate()==date}
        return SkyDay(date,local(sun.rise),local(sun.set),local(moon.rise),local(moon.set))
    }
    fun phaseName(phase:Double)=when {
        phase< -157.5||phase>=157.5->"新月"
        phase< -112.5->"娥眉月"
        phase< -67.5->"上弦月"
        phase< -22.5->"盈凸月"
        phase< 22.5->"滿月"
        phase< 67.5->"虧凸月"
        phase< 112.5->"下弦月"
        else->"殘月"
    }
}
