package io.atmos.weather.next.data

import android.content.Context
import io.atmos.weather.next.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId

/** HTTPS network source. A failed request is never silently replaced with fabricated weather. */
class OpenMeteoWeather(private val context: Context) {
    private val store = context.getSharedPreferences("next_weather_cache_v1", Context.MODE_PRIVATE)

    suspend fun load(city: WeatherCity): WeatherLoad = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&daily=temperature_2m_min,temperature_2m_max,weather_code,precipitation_probability_max" +
                "&timezone=Asia%2FTaipei&forecast_days=10")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                require(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                // Bound upstream response memory in a constrained mobile process.
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val received = input.read(buffer)
                        if (received < 0) break
                        total += received
                        require(total <= 200_000) { "回應過大" }
                        output.write(buffer, 0, received)
                    }
                    output.toByteArray()
                }
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                val current = json.getJSONObject("current")
                val local = LocalDateTime.parse(current.getString("time"))
                val observed = local.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                val temperature = current.getDouble("temperature_2m")
                require(temperature.isFinite() && temperature in -80.0..65.0) { "溫度資料異常" }
                val code = current.getInt("weather_code")
                val forecastHourly = parseHourly(json.optJSONObject("hourly"), observed)
                val forecastDaily = parseDaily(json.optJSONObject("daily"))
                val value = WeatherReading(
                    cityId=city.id,
                    temperatureC=temperature,
                    feelsLikeC=current.optFiniteDouble("apparent_temperature"),
                    humidityPct=current.optInt("relative_humidity_2m", -1).takeIf { it in 0..100 },
                    windKmh=current.optFiniteDouble("wind_speed_10m"),
                    precipitationMm=current.optFiniteDouble("precipitation"),
                    wmoCode=code, observedEpochMs=observed, receivedEpochMs=now,
                    hourly=forecastHourly,daily=forecastDaily
                )
                // Persist only validated real source values, not unknown response structures.
                store.edit().putString("city_${city.id}", encode(value).toString()).apply()
                WeatherLoad.Live(value)
            } finally { connection.disconnect() }
        } catch (failure: Exception) {
            val cached = decode(store.getString("city_${city.id}", null), city.id)
            val reason = "連線失敗，顯示先前資料（非即時）"
            if (cached != null) WeatherLoad.Cached(cached, reason)
            else WeatherLoad.Unavailable("暫時無法取得天氣；請確認網路再重試")
        }
    }

    // Real Open-Meteo forecast entries only. Never fill empty days/hours with synthetic samples.
    private fun parseHourly(o:JSONObject?, observed:Long):List<HourlyForecast> {
        if(o==null) return emptyList()
        val times=o.optJSONArray("time")?:return emptyList()
        val temps=o.optJSONArray("temperature_2m")?:return emptyList()
        val codes=o.optJSONArray("weather_code")
        val chances=o.optJSONArray("precipitation_probability")
        val out=mutableListOf<HourlyForecast>()
        for(i in 0 until minOf(times.length(),temps.length())) {
            val t=runCatching { LocalDateTime.parse(times.getString(i)) }.getOrNull()?:continue
            val stamp=t.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()
            if(stamp < observed - 3_600_000L || stamp > observed + 30*3_600_000L) continue
            val value=temps.optDouble(i,Double.NaN).takeIf {it.isFinite() && it in -80.0..65.0}
            val code=codes?.optInt(i,-1)?.takeIf {it>=0}
            val rain=chances?.optInt(i,-1)?.takeIf {it in 0..100}
            out+=HourlyForecast("%02d:00".format(t.hour),value,code,rain)
            if(out.size==24)break
        }
        return out
    }
    private fun parseDaily(o:JSONObject?):List<DailyForecast> {
        if(o==null)return emptyList()
        val times=o.optJSONArray("time")?:return emptyList()
        val lows=o.optJSONArray("temperature_2m_min")?:return emptyList()
        val highs=o.optJSONArray("temperature_2m_max")?:return emptyList()
        val codes=o.optJSONArray("weather_code")
        val chances=o.optJSONArray("precipitation_probability_max")
        val out=mutableListOf<DailyForecast>()
        for(i in 0 until minOf(10,times.length(),lows.length(),highs.length())) {
            val date=runCatching {java.time.LocalDate.parse(times.getString(i))}.getOrNull()?:continue
            val low=lows.optDouble(i,Double.NaN)
            val high=highs.optDouble(i,Double.NaN)
            if(!low.isFinite() || !high.isFinite() || low !in -80.0..65.0 || high !in -80.0..65.0 || low>high)continue
            val label=if(i==0) "今天" else if(i==1) "明天" else "週"+listOf("一","二","三","四","五","六","日")[date.dayOfWeek.value-1]
            out+=DailyForecast(label,low,high,codes?.optInt(i,-1)?.takeIf {it>=0},
                chances?.optInt(i,-1)?.takeIf {it in 0..100})
        }
        return out
    }

    private fun JSONObject.optFiniteDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() }

    private fun encode(w: WeatherReading) = JSONObject().apply {
        put("schema", 1);put("city", w.cityId);put("temp",w.temperatureC)
        put("feels",w.feelsLikeC);put("humidity",w.humidityPct);put("wind",w.windKmh)
        put("rain",w.precipitationMm);put("code",w.wmoCode)
        put("observed",w.observedEpochMs);put("received",w.receivedEpochMs)
        put("hourly",JSONArray().apply {w.hourly.forEach { h->put(JSONObject().apply {
            put("hour",h.hourLabel);put("temp",h.temperatureC);put("code",h.wmoCode);put("rain",h.rainChance)
        }) } })
        put("daily",JSONArray().apply {w.daily.forEach { d->put(JSONObject().apply {
            put("day",d.dayLabel);put("low",d.lowC);put("high",d.highC);put("code",d.wmoCode);put("rain",d.rainChance)
        }) } })
    }
    private fun decode(raw: String?, cityId: String): WeatherReading? = runCatching {
        if (raw.isNullOrBlank()) return@runCatching null
        val o=JSONObject(raw)
        if(o.getInt("schema") != 1 || o.getString("city") != cityId) return@runCatching null
        val t=o.getDouble("temp").takeIf { it.isFinite() && it in -80.0..65.0 } ?: return@runCatching null
        val observed=o.getLong("observed").takeIf { it>0L } ?: return@runCatching null
        WeatherReading(cityId,t,finiteOrNull(o,"feels"),
            o.optInt("humidity",-1).takeIf { it in 0..100 },finiteOrNull(o,"wind"),
            finiteOrNull(o,"rain"),o.getInt("code"),observed,o.getLong("received"),
            hourly=(o.optJSONArray("hourly")?:JSONArray()).let { a -> (0 until minOf(a.length(),24)).mapNotNull { i-> runCatching {
                val h=a.getJSONObject(i)
                HourlyForecast(h.getString("hour"),finiteOrNull(h,"temp"),
                    h.optInt("code",-1).takeIf{it>=0},h.optInt("rain",-1).takeIf{it in 0..100})
            }.getOrNull() } },
            daily=(o.optJSONArray("daily")?:JSONArray()).let { a -> (0 until minOf(a.length(),10)).mapNotNull { i-> runCatching {
                val d=a.getJSONObject(i)
                DailyForecast(d.getString("day"),d.getDouble("low"),d.getDouble("high"),
                    d.optInt("code",-1).takeIf{it>=0},d.optInt("rain",-1).takeIf{it in 0..100})
            }.getOrNull() } })
    }.getOrNull()
    private fun finiteOrNull(o:JSONObject,key:String):Double? =
        if(o.isNull(key)) null else o.optDouble(key,Double.NaN).takeIf {it.isFinite()}
}
