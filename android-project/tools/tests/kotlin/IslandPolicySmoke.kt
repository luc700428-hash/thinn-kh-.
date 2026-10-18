package io.atmos.weather.notifications

fun main() {
    val p0=IslandPriorityPolicy.next("eew:1",IslandPriority.SEISMIC,1000)
    check(!IslandPriorityPolicy.shouldPresent(p0,"weather:3",IslandPriority.WEATHER_ALERT,1100))
    check(!IslandPriorityPolicy.shouldPresent(p0,"timer:1",IslandPriority.TIME_CRITICAL,1100))
    check(IslandPriorityPolicy.shouldPresent(p0,"eew:1",IslandPriority.SEISMIC,1100))
    check(IslandPriorityPolicy.shouldPresent(p0,"weather:3",IslandPriority.WEATHER_ALERT,77000))
    val p3=IslandPriorityPolicy.next("reminder:1",IslandPriority.REMINDER,1000)
    check(IslandPriorityPolicy.shouldPresent(p3,"eew:2",IslandPriority.SEISMIC,1500))
    check(!IslandPriorityPolicy.shouldPresent(p3,"ambient:1",IslandPriority.AMBIENT,1500))
    check(IslandPriorityPolicy.shouldPresent(null,"ambient:1",IslandPriority.AMBIENT,1500))
    println("ISLAND_PRIORITY_POLICY_OK 7 checks")
}
