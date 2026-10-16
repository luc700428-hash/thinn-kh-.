package io.atmos.weather.earthquake

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/** Replay ONLY genuine archived ExpTech EEW frames; never extrapolates fake serials. */
object SeismicHistoricalReplay {
    private val endpoints=listOf("api.core-tyo1.exptech.dev","api.core-tnn1.exptech.dev")
    suspend fun findFor(report:EarthquakeReport):List<EewIncident> = withContext(Dispatchers.IO) {
        val results=mutableListOf<EewIncident>()
        val origin=report.origin.epochSecond
        // Bounded 8 snapshots; replay is explicit user action, never background polling.
        for(offset in listOf(0L,8L,16L,25L,35L,50L,70L,95L)) {
            val sec=origin+offset
            var raw:String?=null
            for(host in endpoints) {
                try {
                    val connection=URL("https://$host/api/v2/eq/eew/$sec").openConnection() as HttpURLConnection
                    connection.connectTimeout=3500;connection.readTimeout=3500
                    connection.setRequestProperty("Accept","application/json")
                    try {
                        if(connection.responseCode==200) raw=connection.inputStream.bufferedReader().use { it.readText().take(850000) }
                    } finally {connection.disconnect()}
                    if(raw!=null) break
                } catch(e:CancellationException) {throw e}
                catch(_:Exception) { /* move to backup */ }
            }
            if(raw==null) continue
            for(frame in ExpTechRealtimeParser.parseEew(raw)) {
                if(frame.test || frame.cancelled) continue
                // Fail-closed: same origin time, magnitude and epicentre evidence required.
                val eventTime=frame.originAtMs ?: continue
                if(abs(eventTime-report.origin.toEpochMilli())>90_000L) continue
                if(frame.latitude==null || frame.longitude==null || report.latitude==null || report.longitude==null) continue
                if(SeismicWavePhysics.epicentralKm(frame.latitude,frame.longitude,report.latitude,report.longitude)>65.0) continue
                if(frame.magnitude!=null && abs(frame.magnitude-report.magnitude)>1.1) continue
                results.add(frame)
            }
        }
        results.distinctBy { "${it.id}:${it.serial}" }.sortedWith(compareBy<EewIncident> { it.issuedAtMs }.thenBy { it.serial }).take(15)
    }
}
