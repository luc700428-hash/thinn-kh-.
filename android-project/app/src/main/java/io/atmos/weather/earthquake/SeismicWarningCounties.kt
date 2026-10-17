package io.atmos.weather.earthquake

/** Visible county predictions must come from a verified upstream county-intensity map.
 * This adapter does not derive intensities from magnitude, epicentral distance or weather.
 */
data class CountyIntensity(val county:String,val intensity:String?)
object SeismicWarningCounties {
    val counties=listOf("基隆市","臺北市","新北市","桃園市","新竹市","新竹縣","苗栗縣",
        "臺中市","彰化縣","南投縣","雲林縣","嘉義市","嘉義縣","臺南市","高雄市","屏東縣",
        "宜蘭縣","花蓮縣","臺東縣","澎湖縣","金門縣","連江縣")
    fun allFromProvider(raw:Map<String,String>):List<CountyIntensity> {
        val present=fromProvider(raw).associateBy { it.county }
        return counties.map { present[it] ?: CountyIntensity(it,null) }
    }
    fun fromProvider(raw:Map<String,String>):List<CountyIntensity> {
        val values=raw.mapKeys { it.key.replace('台','臺').trim() }
        return counties.mapNotNull { county ->
            val valid=SeismicWavePhysics.intensityLabel(values[county]) ?: return@mapNotNull null
            CountyIntensity(county,valid)
        }
    }
}
