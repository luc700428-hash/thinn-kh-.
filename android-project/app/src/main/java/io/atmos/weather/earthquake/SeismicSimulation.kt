package io.atmos.weather.earthquake

/** The simulated incident is constructed locally and must never go to notification,
 * FCM, persistence, live feeds, report lists, analytics, or upstream providers.
 */
object SeismicSimulation {
    fun scenario(nowMs:Long,strong:Boolean=false):EewIncident = EewIncident(
        id=if(strong) "TRAINING_LOCAL_ONLY_7" else "TRAINING_LOCAL_ONLY", serial=1,
        source="thinn-khì 本機防災演練",
        issuedAtMs=nowMs, epicenter="花蓮縣近海（模擬）", magnitude=if(strong) 7.6 else 6.2,
        depthKm=18.0, latitude=23.9, longitude=121.72, cancelled=false,
        test=true, urgent=false, originAtMs=nowMs-2000L,
        // Higher-intensity training is a fictional scenario, never a prediction.
        // Official CWA scale is 0–7 (5/6 have weak/strong), NOT intensity 8.
        localIntensityByCounty=if(strong) mapOf(
            "花蓮縣" to "7", "宜蘭縣" to "6強", "臺東縣" to "6弱", "南投縣" to "5強",
            "臺中市" to "5弱", "臺北市" to "4", "新北市" to "4", "桃園市" to "4",
            "基隆市" to "4", "新竹縣" to "4", "新竹市" to "4", "苗栗縣" to "4",
            "彰化縣" to "3", "雲林縣" to "3", "嘉義縣" to "3", "嘉義市" to "3",
            "臺南市" to "2", "高雄市" to "2", "屏東縣" to "2",
            "澎湖縣" to "1", "金門縣" to "0", "連江縣" to "0"
        ) else mapOf(
            "花蓮縣" to "4", "宜蘭縣" to "3", "臺東縣" to "3", "南投縣" to "2",
            "臺中市" to "2", "臺北市" to "2", "新北市" to "2", "桃園市" to "1",
            "基隆市" to "1", "新竹縣" to "1", "新竹市" to "1", "苗栗縣" to "1",
            "彰化縣" to "1", "雲林縣" to "1", "嘉義縣" to "1", "嘉義市" to "1",
            "臺南市" to "1", "高雄市" to "1", "屏東縣" to "1",
            "澎湖縣" to "0", "金門縣" to "0", "連江縣" to "0")
    )
}
