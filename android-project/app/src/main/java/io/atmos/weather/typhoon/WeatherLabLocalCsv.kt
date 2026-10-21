package io.atmos.weather.typhoon

/** Client-side Weather Lab forecast CSV reader. Explicitly imported data, never a live feed. */
object WeatherLabLocalCsv {
    data class Point(val longitude: Double, val latitude: Double, val time: String)
    fun parse(input: String): List<Point> {
        require(input.length <= 1_000_000) { "CSV 大小不可超過 1 MB" }
        val records=input.lineSequence().map(String::trim).filter {it.isNotEmpty() && !it.startsWith('#')}.take(3002).toList()
        require(records.size >= 2) { "CSV 缺少欄位與資料" }
        fun row(s:String):List<String> {
            val out=mutableListOf<String>(); val acc=StringBuilder(); var quoted=false;var i=0
            while(i<s.length) {
                when {
                    s[i]=='"' && quoted && i+1<s.length && s[i+1]=='"' -> {acc.append('"');i++}
                    s[i]=='"' -> quoted=!quoted
                    s[i]==',' && !quoted -> {out+=acc.toString().trim();acc.clear()}
                    else -> acc.append(s[i])
                };i++
            }
            require(!quoted) {"CSV 引號格式錯誤"}
            out+=acc.toString().trim();return out
        }
        val names=row(records[0]).map { it.trim().removePrefix("\uFEFF").lowercase()
            .replace(" ","_").replace("-","_") }
        fun idx(vararg choices:String)= names.indexOfFirst {it in choices}
        val lat=idx("latitude","lat","forecast_latitude","lat_deg","latitude_deg")
        val lon=idx("longitude","lon","lng","forecast_longitude","lon_deg","longitude_deg")
        val time=idx("time","valid_time","forecast_time","datetime","date_time","timestamp","valid_datetime")
        require(lat>=0 && lon>=0 && time>=0) {"CSV 應有 latitude、longitude、time 欄位"}
        return records.drop(1).mapNotNull {line ->
            val f=runCatching {row(line)}.getOrNull() ?:return@mapNotNull null
            if(f.size <= maxOf(lat,lon,time)) return@mapNotNull null
            val la=f[lat].toDoubleOrNull();val lo=f[lon].toDoubleOrNull()
            val tm=f[time]
            if(la==null || lo==null || la !in 0.0..55.0 || lo !in 100.0..180.0 ||
                VerifiedTrackPlanner.parseTime(tm)==null) return@mapNotNull null
            Point(lo,la,tm)
        }.distinctBy {listOf(it.longitude,it.latitude,it.time)}.take(3000).also {
            require(it.isNotEmpty()){ "未找到可解析、且位於西北太平洋區域的時間座標" }
        }
    }
}
