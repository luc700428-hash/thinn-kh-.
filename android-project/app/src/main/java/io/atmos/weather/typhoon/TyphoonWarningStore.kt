package io.atmos.weather.typhoon

import android.content.Context
import io.atmos.weather.location.LocationLabels
import java.time.Instant

/** Cache verified official RSS warnings without pretending network failures mean clear skies. */
object TyphoonWarningStore {
    private const val FILE="thinn_typhoon_official"
    private const val MAX_AGE_SECONDS=6*3600L
    fun save(context:Context, warning:VerifiedTyphoonWarning) {
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit()
            .putString("level",warning.level.name).putLong("issued",warning.issuedAt.epochSecond)
            .putString("title",warning.title).putString("url",warning.officialUrl)
            .putString("name",warning.name)
            .putString("counties",warning.landCounties.joinToString("|"))
            .putString("seas",warning.seaAreas.joinToString("|"))
            .apply()
    }
    fun clear(context:Context) {
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun current(context:Context, now:Instant=Instant.now()):VerifiedTyphoonWarning? {
        val p=context.getSharedPreferences(FILE,Context.MODE_PRIVATE)
        val level=runCatching { TyphoonWarningLevel.valueOf(p.getString("level","").orEmpty()) }.getOrNull() ?: return null
        val issued=p.getLong("issued",0)
        if(now.epochSecond-issued !in -600L..MAX_AGE_SECONDS) return null
        val url=p.getString("url","").orEmpty()
        if(!url.startsWith("https://www.cwa.gov.tw/")) return null
        fun items(key:String)=p.getString(key,"").orEmpty().split('|').filter(String::isNotBlank)
        return VerifiedTyphoonWarning(level,Instant.ofEpochSecond(issued),
            p.getString("title","").orEmpty(),url,items("counties"),items("seas"),
            p.getString("name","本次颱風").orEmpty())
    }
    fun county(place:String):String? {
        val tokens=LocationLabels.clean(place).split(Regex("[·,/]"))
        return tokens.map(String::trim).firstOrNull { it.endsWith("縣") || it.endsWith("市") }
    }
    fun shouldNotify(context:Context, warning:VerifiedTyphoonWarning, now:Instant=Instant.now()):Boolean {
        val p=context.getSharedPreferences(FILE,Context.MODE_PRIVATE)
        val previous=p.getString("sent_level","")
        val last=p.getLong("sent_at",0L)
        val previousName=p.getString("sent_name", "")
        // If no storm identifier is available in RSS, do not endlessly resend
        // each 3-hour bulletin. New storm after 72h can notify again.
        if(now.epochSecond-last>72*3600L || previousName!=warning.name) return true
        return previous != warning.level.name && warning.level==TyphoonWarningLevel.LAND
    }
    fun markNotified(context:Context, warning:VerifiedTyphoonWarning, now:Instant=Instant.now()) {
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit()
            .putString("sent_level",warning.level.name).putString("sent_name",warning.name)
            .putLong("sent_at",now.epochSecond).apply()
    }
}
