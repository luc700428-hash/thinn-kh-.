import io.atmos.weather.domain.visual.IslandRegion
import io.atmos.weather.domain.visual.LandmarkGeo

fun main() {
    val regions=listOf(
        IslandRegion("蘭嶼","臺東縣",22.053,121.546,17.0),
        IslandRegion("小琉球","屏東縣",22.344,120.375,7.0),
        IslandRegion("龜山島","宜蘭縣",24.843,121.952,6.0),
        IslandRegion("綠島","臺東縣",22.660,121.488,13.0)
    )
    check(LandmarkGeo.closestIsland(22.053,121.546,regions)?.name == "蘭嶼")
    check(LandmarkGeo.closestIsland(22.344,120.375,regions)?.name == "小琉球")
    check(LandmarkGeo.closestIsland(24.843,121.952,regions)?.name == "龜山島")
    check(LandmarkGeo.closestIsland(22.660,121.488,regions)?.name == "綠島")
    check(LandmarkGeo.closestIsland(22.6273,120.3014,regions)==null)
    check(LandmarkGeo.distanceKm(22.053,121.546,22.053,121.546)==0.0)
    println("6 landmark geo assertions OK")
}
