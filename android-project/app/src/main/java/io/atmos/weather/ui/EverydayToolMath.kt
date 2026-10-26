package io.atmos.weather.ui

internal object EverydayToolMath {
    data class UnitValue(val symbol:String,val factor:Double,val offset:Double=0.0)
    data class Category(val title:String,val units:List<UnitValue>)
    val categories=listOf(
        Category("溫度",listOf(UnitValue("°C",1.0),UnitValue("°F",5.0/9.0,-32.0*5.0/9.0),UnitValue("K",1.0,-273.15))),
        Category("風速",listOf(UnitValue("m/s",1.0),UnitValue("km/h",1.0/3.6),UnitValue("kt",1852.0/3600.0))),
        Category("雨量",listOf(UnitValue("mm",1.0),UnitValue("cm",10.0),UnitValue("in",25.4))),
        Category("距離",listOf(UnitValue("km",1.0),UnitValue("mi",1.609344),UnitValue("m",.001))),
        Category("氣壓",listOf(UnitValue("hPa",1.0),UnitValue("kPa",10.0),UnitValue("mmHg",1.33322387415)))
    )
    fun convert(value:Double,from:UnitValue,to:UnitValue):Double=(value*from.factor+from.offset-to.offset)/to.factor
    fun elapsed(accumulated:Long,started:Long?,now:Long):Long=accumulated+(if(started!=null && now>=started)now-started else 0L)
}
