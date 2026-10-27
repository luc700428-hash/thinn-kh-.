package io.atmos.weather.location
import android.content.Context
import io.atmos.weather.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

object CitySearchRules {
    fun normalize(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFKC).lowercase()
        .replace('臺','台').replace("台湾","台灣").replace(Regex("[\\s·,，/]+"),"")
    fun score(query:String,name:String,aliases:List<String> = emptyList()):Int {
        val q=normalize(query).removePrefix("台灣");if(q.isEmpty())return 0
        val n=normalize(name).removePrefix("台灣")
        val tail=normalize(name.substringAfterLast('·'))
        val short=n.replace(Regex("[縣市區鄉鎮]"),"")
        val shortQuery=q.replace(Regex("[縣市區鄉鎮]"),"")
        return when {tail==q->100;n==q->95;n.startsWith(q)->85;tail.startsWith(q)->80;n.contains(q)->60;shortQuery.length>=2&&short==shortQuery->92;shortQuery.length>=2&&short.startsWith(shortQuery)->75
            aliases.any{normalize(it)==q}->90;aliases.any{normalize(it).startsWith(q)}->55;else->0}
    }
    fun coordinate(query:String):WeatherCity? {
        val m=Regex("^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,，]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$").matchEntire(query)?:return null
        val lat=m.groupValues[1].toDoubleOrNull()?:return null;val lon=m.groupValues[2].toDoubleOrNull()?:return null
        if(lat !in -90.0..90.0||lon !in -180.0..180.0)return null
        return LocationLabels.city(WeatherCity("point-$lat-$lon","座標 · $lat, $lon",GeoPoint(lat,lon)))
    }
    fun distinct(cities:List<WeatherCity>):List<WeatherCity> {
        val result=mutableListOf<WeatherCity>()
        cities.forEach {raw->val c=LocationLabels.city(raw)
            if(result.none{it.id==c.id||(normalize(it.name)==normalize(c.name)&&abs(it.coordinate.latitude-c.coordinate.latitude)<.03&&abs(it.coordinate.longitude-c.coordinate.longitude)<.03)})result+=c}
        return result.take(60)
    }
}
object OfflineCitySearch {
    private data class Entry(val city:WeatherCity,val aliases:List<String>)
    @Volatile private var loaded:List<Entry>?=null
    private fun entries(context:Context):List<Entry> = loaded?:synchronized(this) {
        loaded?:run {
            val j=JSONObject(context.assets.open("locations/taiwan_townships.json").bufferedReader().use{it.readText()}).getJSONArray("places")
            (0 until j.length()).map{i->val p=j.getJSONObject(i);val a=p.optJSONArray("aliases")?:JSONArray()
                Entry(WeatherCity(p.getString("id"),p.getString("name"),GeoPoint(p.getDouble("lat"),p.getDouble("lon"))),
                    (0 until a.length()).map{a.getString(it)})}
        }.also{loaded=it}
    }
    fun search(context:Context,query:String):List<WeatherCity> {
        CitySearchRules.coordinate(query)?.let{return listOf(it)}
        val q=CitySearchRules.normalize(query)
        val island=if(listOf("釣魚台","釣魚臺","釣魚島","钓鱼岛","diaoyu","diaoyutai","senkaku","尖閣").any{q.contains(CitySearchRules.normalize(it))})
            listOf(WeatherCity("tw-diaoyutai","台灣 · 釣魚台",GeoPoint(25.746,123.474))) else emptyList()
        val domestic=entries(context).map{it to CitySearchRules.score(query,it.city.name,it.aliases)}.filter{it.second>0}.sortedByDescending{it.second}.take(40).map{it.first.city}
        val defaults=SampleCities.all.filter{CitySearchRules.score(query,it.name)>0}
        return CitySearchRules.distinct(island+domestic+defaults)
    }
}
object RecentCityStore {
    private const val key="recent_weather_places_v26"
    fun read(context:Context):List<WeatherCity> = runCatching {
        val j=JSONArray(context.getSharedPreferences("thinn_settings",0).getString(key,"[]"))
        (0 until j.length()).mapNotNull {i->val p=j.getJSONObject(i);val lat=p.optDouble("lat",Double.NaN);val lon=p.optDouble("lon",Double.NaN)
            if(lat !in -90.0..90.0||lon !in -180.0..180.0)null else LocationLabels.city(WeatherCity(p.getString("id"),p.getString("name"),GeoPoint(lat,lon)))}.take(8)
    }.getOrDefault(emptyList())
    fun record(context:Context,city:WeatherCity) {
        if(city.id=="gps")return
        val list=CitySearchRules.distinct(listOf(city)+read(context)).take(8);val j=JSONArray()
        list.forEach{j.put(JSONObject().put("id",it.id).put("name",it.name).put("lat",it.coordinate.latitude).put("lon",it.coordinate.longitude))}
        context.getSharedPreferences("thinn_settings",0).edit().putString(key,j.toString()).apply()
    }
}
