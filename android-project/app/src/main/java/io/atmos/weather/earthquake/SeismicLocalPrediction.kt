package io.atmos.weather.earthquake

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToLong

internal enum class PredictionOrigin { SOURCE, LOCAL_MODEL }

/** Independent local estimate. Never changes notification eligibility or county source data. */
internal data class LocalEewPrediction(
    val intensity:String?=null,
    val intensityOrigin:PredictionOrigin?=null,
    val arrivalAtMs:Long?=null,
    val arrivalOrigin:PredictionOrigin?=null,
    val intensityReason:String="待來源提供",
    val arrivalReason:String="待來源提供"
) {
    fun arrivalText(nowMs:Long):String = arrivalAtMs?.let { at ->
        if(at<=nowMs) {
            if(arrivalOrigin==PredictionOrigin.LOCAL_MODEL) "可能已抵達" else "已抵達"
        } else {
            val seconds=ceil((at-nowMs)/1000.0).toInt()
            if(arrivalOrigin==PredictionOrigin.LOCAL_MODEL) "約 $seconds s" else "$seconds s"
        }
    } ?: "—"
}

/** Source values take priority independently for each field.
 * The PGA relation uses an uncalibrated site factor of 1, so it is deliberately coarse.
 * At high PGA, PGV may still yield grade 4. Never invent a 5− lower bound without PGV.
 * S travel times use a precomputed global IASP91 model, NOT the illustrative map rings.
 */
internal object SeismicLocalPrediction {
    fun resolve(incident:EewIncident,place:SeismicPlace?,nowMs:Long=System.currentTimeMillis()):LocalEewPrediction {
        if(incident.cancelled)return LocalEewPrediction(intensityReason="速報已取消",arrivalReason="速報已取消")
        if(place==null || !validPlace(place))return LocalEewPrediction(
            intensityReason="尚未取得所在地 · 請在地震設定選擇地區",
            arrivalReason="尚未取得所在地 · 無法計算抵達時間")
        val county=place.name.replace('台','臺').removeSuffix("附近").trim()
        val sourceIntensity=SeismicWarningCounties.fromProvider(incident.localIntensityByCounty)
            .firstOrNull{it.county==county}?.intensity
        val sourceArrival=incident.arrivalAtMsByCounty.entries.firstOrNull{it.key.replace('台','臺')==county}
            ?.value?.takeIf{it in incident.issuedAtMs-120_000..incident.issuedAtMs+300_000}
        val live=ExpTechRealtimeParser.isFresh(incident,nowMs)
        val lat=incident.latitude?.takeIf{it.isFinite()&&it in 15.0..32.0}
        val lon=incident.longitude?.takeIf{it.isFinite()&&it in 110.0..135.0}
        val depth=incident.depthKm?.takeIf{it.isFinite()&&it in 0.0..700.0}
        val distance=if(lat!=null&&lon!=null)SeismicWavePhysics.epicentralKm(lat,lon,place.lat,place.lon) else null
        val origin=incident.originAtMs?.takeIf{it in incident.issuedAtMs-300_000..incident.issuedAtMs+5_000}
        val seconds=if(live&&distance!=null&&depth!=null&&origin!=null)travelSeconds(depth,distance) else null
        val derivedArrival=seconds?.let{origin!!+(it*1000.0).roundToLong()}
        val derivedIntensity=if(live&&distance!=null&&depth!=null)coarseIntensity(incident.magnitude,depth,distance) else null
        val intensityReason=when {
            sourceIntensity!=null->"來源提供的所在地震度"
            derivedIntensity=="4級以上"->"本機 PGA 初估 · 細級待確認"
            derivedIntensity!=null->"本機模型估算 · 未校準場址"
            !live->"非有效即時資料 · 不補算震度"
            lat==null||lon==null->"來源缺少有效震央座標"
            depth==null->"來源缺少有效深度"
            incident.magnitude==null->"來源缺少規模"
            else->"超出震度模型範圍 · 待來源提供"
        }
        val arrivalReason=when {
            sourceArrival!=null->"來源提供的所在地時間"
            derivedArrival!=null->"本機 IASP91 模型估算"
            !live->"非有效即時資料 · 不補算倒數"
            lat==null||lon==null->"來源缺少有效震央座標"
            depth==null->"來源缺少有效深度"
            origin==null->"來源缺少可靠發震時間"
            else->"超出走時模型範圍 · 待來源提供"
        }
        return LocalEewPrediction(sourceIntensity?:derivedIntensity,
            if(sourceIntensity!=null)PredictionOrigin.SOURCE else if(derivedIntensity!=null)PredictionOrigin.LOCAL_MODEL else null,
            sourceArrival?:derivedArrival,
            if(sourceArrival!=null)PredictionOrigin.SOURCE else if(derivedArrival!=null)PredictionOrigin.LOCAL_MODEL else null,
            intensityReason,arrivalReason)
    }

    private fun validPlace(place:SeismicPlace)=place.lat.isFinite()&&place.lon.isFinite()&&
        place.lat in 18.0..28.0&&place.lon in 115.0..124.0&&place.ageMs in 0..900_000

    internal fun coarseIntensity(magnitude:Double?,depthKm:Double,distanceKm:Double):String? {
        if(magnitude==null||!magnitude.isFinite()||magnitude !in 2.0..7.5||
            !depthKm.isFinite()||depthKm !in 0.0..100.0||
            !distanceKm.isFinite()||distanceKm !in 0.0..600.0)return null
        val r=hypot(distanceKm,depthKm).coerceAtLeast(10.0)
        val pga=1.657*exp(1.533*magnitude)*r.pow(-1.607)
        return when {pga<.8->"0";pga<2.5->"1";pga<8->"2";pga<25->"3";pga<80->"4";else->"4級以上"}
    }

    /** Bilinear interpolation only inside the table, never extrapolation. */
    internal fun travelSeconds(depthKm:Double,distanceKm:Double):Double? {
        if(!depthKm.isFinite()||!distanceKm.isFinite()||depthKm !in 0.0..700.0||distanceKm !in 0.0..1200.0)return null
        val h=SeismicTravelTimeTable.depths;val r=SeismicTravelTimeTable.distances
        fun lower(axis:DoubleArray,value:Double):Int {
            val at=axis.binarySearch(value)
            return (if(at>=0)at else -at-2).coerceIn(0,axis.lastIndex-1)
        }
        val hi=lower(h,depthKm);val ri=lower(r,distanceKm)
        val hf=(depthKm-h[hi])/(h[hi+1]-h[hi]);val rf=(distanceKm-r[ri])/(r[ri+1]-r[ri])
        fun row(index:Int):Double {
            val a=SeismicTravelTimeTable.seconds[index]
            return (a[ri]+(a[ri+1]-a[ri])*rf)/100.0
        }
        return row(hi)*(1-hf)+row(hi+1)*hf
    }
}
