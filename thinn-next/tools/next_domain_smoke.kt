import io.atmos.weather.next.domain.*
fun main() {
    require(WeatherScene.entries.size==14)
    require(WmoWeatherCodes.scene(999)==null)
    require(DataFreshnessPolicy.classify(0,1)==DataFreshness.UNKNOWN)
    val now=1800000000000L
    val e=NextEvent("event",NextEvent.Type.VERIFIED_EARTHQUAKE,"警告","",now-1000,now+10000,false)
    require(!EventPolicy.isDeliverable(e,now))
    require(EventPolicy.isDeliverable(e.copy(verified=true),now))
    println("NEXT foundation pure Kotlin engine smoke OK")
}
