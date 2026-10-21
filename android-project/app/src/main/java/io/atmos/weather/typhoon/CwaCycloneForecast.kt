package io.atmos.weather.typhoon

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Only CWA-issued fixes from W-C0034-005; supports REST camelCase and file/XML-style casing.
 * An unrecognised schema returns null. A recognised empty cyclone list returns emptyList().
 * Never synthesize a track, intensity, warning, or forecast cycle.
 */
object CwaCycloneForecast {
    private fun obj(parent: JSONObject?, vararg names: String): JSONObject? =
        names.firstNotNullOfOrNull { parent?.optJSONObject(it) }

    private fun text(parent: JSONObject?, vararg names: String): String =
        names.firstNotNullOfOrNull { key ->
            parent?.optString(key)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        }.orEmpty()

    private fun number(parent: JSONObject?, vararg names: String): Double? =
        names.firstNotNullOfOrNull { key ->
            parent?.opt(key)?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
        }

    private fun children(parent: JSONObject?, vararg names: String): List<JSONObject> {
        val value = names.firstNotNullOfOrNull { parent?.opt(it) }
        return when(value) {
            is JSONArray -> (0 until minOf(value.length(), 300)).mapNotNull { value.optJSONObject(it) }
            is JSONObject -> listOf(value)
            else -> emptyList()
        }
    }

    private fun cyclones(root: JSONObject): List<JSONObject>? {
        val data = root.optJSONObject("records") ?: obj(root,"cwaopendata")?.let {
            obj(it,"dataset","Dataset")
        } ?: obj(root,"dataset","Dataset") ?: return null
        val group=obj(data,"tropicalCyclones","TropicalCyclones","tropicalcyclones") ?: return null
        return children(group,"tropicalCyclone","TropicalCyclone","tropicalcyclone")
    }

    private fun coordinate(fix: JSONObject): Pair<Double, Double>? {
        var lat=number(fix,"CoordinateLatitude","coordinateLatitude","coordinate_latitude","latitude","lat")
        var lon=number(fix,"CoordinateLongitude","coordinateLongitude","coordinate_longitude","longitude","lon")
        val nested=obj(fix,"coordinate","Coordinate")
        if(nested!=null) {
            lat=lat ?: number(nested,"latitude","Latitude","lat")
            lon=lon ?: number(nested,"longitude","Longitude","lon")
        }
        if(lat==null || lon==null) {
            // CWA XML-derived format uses "longitude,latitude" in one coordinate field.
            val csv=text(fix,"coordinate","Coordinate").split(',')
            if(csv.size == 2) {
                lon=lon ?: csv[0].trim().toDoubleOrNull()
                lat=lat ?: csv[1].trim().toDoubleOrNull()
            }
        }
        if(lat==null || lon==null || !lat.isFinite() || !lon.isFinite() ||
            lat !in 0.0..55.0 || lon !in 100.0..180.0) return null
        return lat to lon
    }

    private fun stormId(tc: JSONObject): String? {
        val year=text(tc,"Year","year")
        val identifier=text(tc,"CwaTdNo","cwaTdNo","cwa_td_no")
            .ifBlank { text(tc,"CwaTyNo","cwaTyNo","cwa_ty_no") }
            .ifBlank { text(tc,"TyphoonName","typhoonName","typhoon_name") }
        if(year.length > 8 || identifier.isBlank() || identifier.length > 48) return null
        return "$year:$identifier"
    }

    private fun forecastEntries(tc: JSONObject): List<Pair<Instant, Pair<Instant, JSONObject>>> {
        val group=obj(tc,"ForecastData","forecastData","forecast_data") ?: return emptyList()
        val defaultTime=text(group,"InitialTime","initialTime","initial_time")
        return children(group,"Fix","fix").mapNotNull { fix ->
            val initial=VerifiedTrackPlanner.parseTime(text(fix,"InitialTime","initialTime","initial_time")
                .ifBlank { defaultTime }) ?: return@mapNotNull null
            val hour=number(fix,"ForecastHour","forecastHour","forecast_hour")
                ?.takeIf { it in 0.0..168.0 } ?: return@mapNotNull null
            if(coordinate(fix)==null) return@mapNotNull null
            initial to (initial.plusSeconds((hour*3600.0).toLong()) to fix)
        }
    }

    /** Keep only the newest issuance per storm; never mix the old and new run. */
    private fun newestForecast(tc: JSONObject): List<Pair<Instant, JSONObject>> {
        val entries=forecastEntries(tc)
        val latest=entries.maxOfOrNull { it.first } ?: return emptyList()
        return entries.filter { it.first == latest }.map { it.second }
            .distinctBy { it.first }.sortedBy { it.first }
    }

    fun parse(root: JSONObject, max: Int = 240): List<TyphoonFix>? {
        val cyclones=cyclones(root) ?: return null
        val result=mutableListOf<TyphoonFix>()
        for(tc in cyclones) {
            val id=stormId(tc) ?: continue
            val historical=obj(tc,"AnalysisData","analysisData","analysis_data")
            for(fix in children(historical,"Fix","fix")) {
                val time=text(fix,"DateTime","dateTime","fix_time","fixTime")
                val position=coordinate(fix) ?: continue
                if(VerifiedTrackPlanner.parseTime(time)==null) continue
                result+=TyphoonFix(position.first,position.second,TyphoonFix.Kind.OBSERVED,time,id)
                if(result.size>=max) return result.take(max)
            }
            for((time,fix) in newestForecast(tc)) {
                val position=coordinate(fix) ?: continue
                result+=TyphoonFix(position.first,position.second,TyphoonFix.Kind.FORECAST,time.toString(),id)
                if(result.size>=max) return result.take(max)
            }
        }
        return result.distinctBy { listOf(it.stormId,it.kind,it.timestamp,it.latitude,it.longitude) }.take(max)
    }

    /** Typed timeline: each published fix owns its own pressure, wind and direction fields.
     * Never recursively borrow attributes from a different cyclone or forecast fix.
     */
    fun timelines(root: JSONObject): List<StormTimeline>? {
        val all=cyclones(root) ?: return null
        return all.mapNotNull storm@ { tc ->
            val id=stormId(tc) ?: return@storm null
            val candidates=newestForecast(tc)
            val positions=candidates.mapNotNull position@ { (time,fix) ->
                val (lat,lon)=coordinate(fix) ?: return@position null
                DatedTyphoonPosition(id,lat,lon,true,time.toString())
            }
            val segment=VerifiedTrackPlanner.verifiedForecastSegments(positions)
                .maxByOrNull { it.size } ?: return@storm null
            val perTime=candidates.associateBy { it.first }
            val fixes=segment.mapNotNull forecast@ { item ->
                val time=VerifiedTrackPlanner.parseTime(item.issuedTime) ?: return@forecast null
                val source=perTime[time]?.second ?: return@forecast null
                fun bounded(min:Double,max:Double,vararg fields:String)=number(source,*fields)?.takeIf { it in min..max }
                val circle=obj(source,"Circle15ms","circle15ms","circle_of_15ms")
                val radius=number(circle,"Radius","radius","radius_km")
                    ?: bounded(0.0,900.0,"circle_of_15ms","radius7Km","radius7")
                StormForecastFix(item.latitude,item.longitude,time,
                    bounded(800.0,1100.0,"Pressure","pressure","centralPressure"),
                    bounded(0.0,120.0,"MaxWindSpeed","maxWindSpeed","max_wind_speed"),
                    bounded(0.0,150.0,"MaxGustSpeed","maxGustSpeed","max_gust_speed"),
                    radius?.takeIf { it in 0.0..900.0 },
                    text(source,"MovingDirection","movingDirection","moving_direction")
                        .take(30).ifBlank { null })
            }
            if(fixes.size<2) return@storm null
            val name=text(tc,"CwaTyphoonName","cwaTyphoonName","cwa_typhoon_name")
                .ifBlank { text(tc,"TyphoonName","typhoonName","typhoon_name") }
                .ifBlank { id }
            val number=text(tc,"CwaTyNo","cwaTyNo","cwa_ty_no")
            StormTimeline(id,name.take(60),number.take(24),fixes)
        }
    }
}
