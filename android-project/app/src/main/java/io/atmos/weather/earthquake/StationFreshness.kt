package io.atmos.weather.earthquake

enum class StationViewState { WAITING, LIVE, EXPIRED, OFFLINE, MAINTENANCE }

/** A missing update expires an observation after 20s; only the provider can report maintenance. */
object StationFreshness {
    fun state(station:SeismicStation,now:Long,fallbackFrameAt:Long=0L):StationViewState {
        when(station.availability) {
            StationAvailability.MAINTENANCE->return StationViewState.MAINTENANCE
            StationAvailability.OFFLINE->return StationViewState.OFFLINE
            else->Unit
        }
        val at=station.observedAtMs?:fallbackFrameAt
        if(at<=0L||station.intensity==null&&station.accelerationGal==null)return StationViewState.WAITING
        return if(now-at in -2_000L..20_000L)StationViewState.LIVE else StationViewState.EXPIRED
    }
    fun label(state:StationViewState)=when(state) {
        StationViewState.LIVE->"最近觀測更新中"
        StationViewState.WAITING->"等待測站觀測"
        StationViewState.EXPIRED->"超過 20 秒未更新 · 觀測暫時失效"
        StationViewState.OFFLINE->"來源回報離線"
        StationViewState.MAINTENANCE->"來源回報維修"
    }
    fun merge(previous:List<SeismicStation>,incoming:List<SeismicStation>):List<SeismicStation> {
        val before=previous.associateBy{it.id};val result=linkedMapOf<String,SeismicStation>()
        incoming.forEach {s->
            val old=before[s.id]
            result[s.id]=if(s.observedAtMs==null&&s.availability==StationAvailability.UNKNOWN&&old!=null)
                s.copy(intensity=old.intensity,accelerationGal=old.accelerationGal,observedAtMs=old.observedAtMs,availability=old.availability)
                else s
        }
        previous.forEach{if(it.id !in result)result[it.id]=it}
        return result.values.take(1500)
    }
}
