package io.atmos.weather.earthquake

import java.time.Instant
import java.util.Locale
import kotlin.math.*

/** CWA report 043: historical observations, never a current early-warning message. */
object ChiChi921 {
    const val ID="TRAINING_LOCAL_ONLY_921"
    const val SOURCE="https://scweb.cwa.gov.tw/special/19990921/1999092101471273043.htm"
    val origin:Instant=Instant.parse("1999-09-20T17:47:15.900Z")
    // Preserve the report's historical 0–7 scale. Old 5/6 must not become modern weak/strong.
    val historicalCountyMaxima=mapOf("南投縣" to "7","雲林縣" to "6","臺中市" to "6",
        "嘉義縣" to "6","嘉義市" to "5","花蓮縣" to "5","苗栗縣" to "5",
        "高雄市" to "5","臺南市" to "5","宜蘭縣" to "5","新竹縣" to "5",
        "臺東縣" to "5","桃園市" to "4","屏東縣" to "4","澎湖縣" to "4",
        "臺北市" to "4","新北市" to "4")
    fun isScenario(incident:EewIncident)=incident.test && incident.id==ID

    data class LocalEstimate(val intensity:String,val epicentralKm:Double,val arrivalSeconds:Double)
    /** Educational attenuation only, never used for live EEW or substituted for observations.
     * Taiwan EEW empirical PGA relation (cm/s²), site factor fixed to 1.
     * Coefficients: MOI Architecture and Building Research Institute 2023 report, p.136;
     * no site/rupture model.
     * At PGA >=80 gal, PGV is unavailable, so modern 5/6 subdivisions cannot be inferred.
     */
    fun estimate(place:SeismicPlace?):LocalEstimate? {
        place?:return null
        if(!place.lat.isFinite()||!place.lon.isFinite()||place.lat !in -90.0..90.0||place.lon !in -180.0..180.0)return null
        val distance=SeismicWavePhysics.epicentralKm(place.lat,place.lon,23.85,120.82)
        val r=hypot(distance,8.0)
        val pga=1.657*exp(1.533*7.3)*r.pow(-1.607)
        val grade=when {pga<.8->"0";pga<2.5->"1";pga<8->"2";pga<25->"3";pga<80->"4";else->"5弱以上"}
        return LocalEstimate(grade,distance,r/3.5)
    }
    fun trainingNarration(incident:EewIncident,place:SeismicPlace?):String {
        require(isScenario(incident))
        val local=estimate(place)
        val county=place?.name?.replace('台','臺')?.removeSuffix("附近")
        val historical=historicalCountyMaxima[county]
        return "收到地震速報！慎防強烈搖晃！這是臺灣九二一大地震情境演練，並非現在發生的地震。"+
            "趴下、掩護、穩住！震央位於南投縣集集鎮，北緯二十三點八五度，東經一百二十點八二度。"+
            "芮氏規模七點三，深度八公里。歷史報告南投地區最大震度七級。"+
            if(local==null)"尚未設定所在地，無法估算當地震度。請在地震監測設定選擇所在地。" else
                "您所在的${place!!.name}，距震央約${String.format(Locale.TAIWAN,"%.0f",local.epicentralKm)}公里，"+
                "本機演練估算震度${local.intensity}${if(local.intensity.contains("以上"))"" else "級"}。"+
                "這是簡化模型，不是官方預測。"+(historical?.let{"該縣市歷史最大震度為舊制${it}級，與您的確切位置可能不同。"}.orEmpty())
    }
}
