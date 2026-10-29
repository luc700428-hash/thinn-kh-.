package io.atmos.weather.earthquake

/** The simulated incident is constructed locally and must never go to notification,
 * FCM, persistence, live feeds, report lists, analytics, or upstream providers.
 */
object SeismicSimulation {
    fun scenario(nowMs:Long,strong:Boolean=false):EewIncident = EewIncident(
        id=if(strong) ChiChi921.ID else "TRAINING_LOCAL_ONLY", serial=1,
        source=if(strong) "中央氣象署 921 歷史資料 · 本機情境演練" else "thinn-khì 本機防災演練",
        issuedAtMs=nowMs, epicenter=if(strong) "南投縣集集鎮 · 日月潭地震站西偏南 10 公里" else "花蓮縣近海（模擬）", magnitude=if(strong) 7.3 else 6.2,
        depthKm=if(strong) 8.0 else 18.0, latitude=if(strong) 23.85 else 23.9, longitude=if(strong) 120.82 else 121.72, cancelled=false,
        test=true, urgent=false, originAtMs=nowMs-2000L,
        // A historical-source scenario runs on a fresh, local-only simulation clock.
        // Recorded maxima remain historical observations, not modern EEW predictions.
        // Official CWA scale is 0–7 (5/6 have weak/strong), NOT intensity 8.
        localIntensityByCounty=if(strong) ChiChi921.historicalCountyMaxima else mapOf(
            "花蓮縣" to "4", "宜蘭縣" to "3", "臺東縣" to "3", "南投縣" to "2",
            "臺中市" to "2", "臺北市" to "2", "新北市" to "2", "桃園市" to "1",
            "基隆市" to "1", "新竹縣" to "1", "新竹市" to "1", "苗栗縣" to "1",
            "彰化縣" to "1", "雲林縣" to "1", "嘉義縣" to "1", "嘉義市" to "1",
            "臺南市" to "1", "高雄市" to "1", "屏東縣" to "1",
            "澎湖縣" to "0", "金門縣" to "0", "連江縣" to "0")
    )
}
