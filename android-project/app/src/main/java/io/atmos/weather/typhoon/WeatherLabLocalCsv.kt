package io.atmos.weather.typhoon

/** Local-only Weather Lab CSV viewer. No scraping, fake model paths or unlicensed redistribution. */
object WeatherLabLocalCsv {
    data class Position(val longitude:Double,val latitude:Double,val time:String)
    private fun split(line:String):List<String> {
        val out=mutableListOf<String>()
        val cell=StringBuilder()
        var quoted=false
        var i=0
        while(i<line.length) {
            when(val c=line[i]) {
                '"' -> if(quoted && i+1<line.length && line[i+1]=='"') {cell.append('"');i++}
                       else quoted=!quoted
                ',' -> if(quoted) cell.append(c) else {out.add(cell.toString().trim());cell.clear()}
                else -> cell.append(c)
            }
            i++
        }
        out.add(cell.toString().trim())
        return out
    }
    fun parse(csv:String):List<Position> {
        val rows=csv.lineSequence().map {it.trimStart('\uFEFF').trim()} .filter {it.isNotBlank()}.take(5000).toList()
        require(rows.size>2) { "檔案沒有可用路徑" }
        val header=split(rows.first()).map {it.lowercase().replace("_","").replace(" ","")}
        fun index(vararg options:String)=header.indexOfFirst {it in options}
        val lat=index("latitude","lat","forecastlatitude")
        val lon=index("longitude","lon","lng","long","forecastlongitude")
        val time=index("validtime","forecasttime","time","timestamp","datetime","validdatetime")
        val member=index("member","memberid","ensemblemember","trackid","ensembleid")
        val storm=index("stormid","storm","cycloneid","cyclone","stormname")
        val model=index("model","modelname","source")
        val issued=index("issuetime","initialtime","init","rundatetime","forecastinit")
        require(lat>=0 && lon>=0 && time>=0) { "缺少緯度、經度或預報時間欄位" }
        data class Entry(val point:Position,val series:String)
        val data=rows.drop(1).mapNotNull {line ->
            val x=split(line)
            if(maxOf(lat,lon,time,member,storm,model,issued)>=x.size)return@mapNotNull null
            val north=x[lat].toDoubleOrNull() ?: return@mapNotNull null
            val east=x[lon].toDoubleOrNull() ?: return@mapNotNull null
            if(north !in 0.0..55.0 || east !in 100.0..180.0) return@mapNotNull null
            val ts=x[time].trim().takeIf {it.isNotBlank()} ?: return@mapNotNull null
            val series="${if(model>=0)x[model] else ""}|${if(storm>=0)x[storm] else ""}|"+
                "${if(member>=0)x[member] else ""}|${if(issued>=0)x[issued] else ""}"
            Entry(Position(east,north,ts),series)
        }
        val selected=data.groupBy {it.series}.maxByOrNull {it.value.size}?.value
            ?.map {it.point}?.distinctBy {it.time}?.sortedBy {it.time}.orEmpty().take(240)
        require(selected.size>=2) {"找不到同一條有效的預測路徑"}
        return selected
    }
}
