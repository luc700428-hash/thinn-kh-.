package io.atmos.weather.earthquake

/** The simulated incident is constructed locally and must never go to notification,
 * FCM, persistence, live feeds, report lists, analytics, or upstream providers.
 */
object SeismicSimulation {
    fun scenario(nowMs:Long):EewIncident = EewIncident(
        id="TRAINING_LOCAL_ONLY", serial=1, source="thinn-khì 本機防災演練",
        issuedAtMs=nowMs, epicenter="花蓮縣近海（模擬）", magnitude=6.2,
        depthKm=18.0, latitude=23.9, longitude=121.72, cancelled=false,
        test=true, urgent=false, originAtMs=nowMs-2000L,
        localIntensityByCounty=mapOf(
            "花蓮縣" to "4", "宜蘭縣" to "3", "臺東縣" to "3", "南投縣" to "2",
            "臺中市" to "2", "臺北市" to "2", "新北市" to "2", "桃園市" to "1",
            "基隆市" to "1", "新竹縣" to "1", "新竹市" to "1", "苗栗縣" to "1",
            "彰化縣" to "1", "雲林縣" to "1", "嘉義縣" to "1", "嘉義市" to "1",
            "臺南市" to "1", "高雄市" to "1", "屏東縣" to "1",
            "澎湖縣" to "0", "金門縣" to "0", "連江縣" to "0")
    )
}
