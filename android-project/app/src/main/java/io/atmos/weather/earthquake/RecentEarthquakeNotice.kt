package io.atmos.weather.earthquake

import android.content.Context

/** Cache the most recently verified POST-event report for a short-lived home banner.
 * Never label an ExpTech fallback as a CWA-issued bulletin.
 */
data class RecentEarthquakeNotice(
    val id:String,val location:String,val magnitude:Double,val sourceOfficial:Boolean,
    val officialUrl:String,val receivedAt:Long,val originAt:Long
)
object RecentEarthquakeNoticeStore {
    const val MAX_AGE_MS=30*60*1000L
    private const val STORE="thinn_recent_eq_report"
    fun save(ctx:Context,report:EarthquakeReport,official:Boolean,now:Long=System.currentTimeMillis()) {
        // Ignore stale and implausible reports. A new report replaces the older one.
        val age=now-report.origin.toEpochMilli()
        if(age !in 0..60*60*1000L || report.magnitude !in 0.0..10.0) return
        ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .putString("id",report.id).putString("location",report.location.take(120))
            .putString("url",report.officialUrl.take(500)).putString("source",if(official)"CWA" else "ExpTech")
            .putString("magnitude",report.magnitude.toString()).putLong("received",now)
            .putLong("origin",report.origin.toEpochMilli()).apply()
    }
    fun current(ctx:Context,now:Long=System.currentTimeMillis()):RecentEarthquakeNotice? {
        val p=ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE)
        val received=p.getLong("received",0L)
        if(received==0L || now-received !in 0 until MAX_AGE_MS) return null
        val id=p.getString("id",null)?.takeIf { it.isNotBlank() } ?: return null
        val magnitude=p.getString("magnitude",null)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return RecentEarthquakeNotice(id,p.getString("location","臺灣附近").orEmpty(),magnitude,
            p.getString("source","")=="CWA",p.getString("url","").orEmpty(),received,p.getLong("origin",0L))
    }
    fun dismiss(ctx:Context,id:String) {
        val p=ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE)
        if(p.getString("id",null)==id) p.edit().clear().apply()
    }
}
