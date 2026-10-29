package io.atmos.weather.earthquake
import android.content.Context

/** Local forecast intensity; never replace it with M, epicentral or maximum intensity. */
object SeismicAlertThreshold {
    val grades=listOf("1","2","3","4","5弱","5強","6弱","6強","7")
    const val DEFAULT="3"
    fun chosen(ctx:Context)=ctx.getSharedPreferences("thinn_seismic_v15",0)
        .getString("eew_min_local_intensity",DEFAULT).takeIf{it in grades}?:DEFAULT
    fun choose(ctx:Context,grade:String) {require(grade in grades);ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putString("eew_min_local_intensity",grade).apply()}
    fun unknownAllowed(ctx:Context)=ctx.getSharedPreferences("thinn_seismic_v15",0).getBoolean("eew_notify_unknown_local",true)
    fun local(raw:Map<String,String>,place:String?):String? {
        val name=place?.replace('台','臺')?.removeSuffix("附近")?.trim().orEmpty()
        if(name.isBlank())return null
        return SeismicWarningCounties.fromProvider(raw).firstOrNull{name==it.county || name.contains(it.county)}?.intensity
    }
    fun accepts(raw:Map<String,String>,place:String?,minimum:String=DEFAULT,notifyUnknown:Boolean=true):Boolean {
        require(minimum in grades)
        val grade=local(raw,place)?:return notifyUnknown
        return grade!="0" && grades.indexOf(grade)>=grades.indexOf(minimum)
    }
    fun accepts(ctx:Context,e:EewIncident)=accepts(e.localIntensityByCounty,SeismicUserLocation.current(ctx)?.name,chosen(ctx),unknownAllowed(ctx))
}
object SeismicDisplayFormat {
    fun location(value:String)=Regex("""(\d+(?:\.\d+)?)\s*公里""").replace(value){"${it.groupValues[1]} km"}

    fun magnitude(value:Double?)=value?.takeIf{it.isFinite()&&it in 0.0..10.0}?.let{"M %.1f".format(java.util.Locale.US,it)}?:"M —"
    fun depth(value:Double?)=value?.takeIf{it.isFinite()&&it in 0.0..700.0}?.let{(if(it%1.0==0.0)"%.0f km" else "%.1f km").format(java.util.Locale.US,it)}?:"— km"
    fun symbol(value:String?)=when(value){"5弱"->"5−";"5強"->"5+";"6弱"->"6−";"6強"->"6+";else->value?:"—"}
}
