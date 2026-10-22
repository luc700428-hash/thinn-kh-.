package io.atmos.weather.alarm

import kotlin.math.roundToInt

/** Shared iPhone-inspired detent geometry for ALL three alarm picker columns.
 *  AM/PM is bounded to two rows; hour and minute wrap cyclically.
 *  Does not store time. The existing V25AlarmMeridiem remains the conversion authority.
 */
internal object IOSWheelSelection {
    fun clampOffset(value:Int,count:Int,proposed:Float,stepPx:Float):Float {
        require(count>=2 && value in 0 until count && stepPx.isFinite() && stepPx>0f)
        if (!proposed.isFinite()) return 0f
        return if(count==2) proposed.coerceIn((value-count+1)*stepPx,value*stepPx)
            else proposed.coerceIn(-stepPx*160f,stepPx*160f)
    }
    fun clampSteps(value:Int,count:Int,steps:Int):Int {
        require(count>=2 && value in 0 until count)
        return if(count==2) steps.coerceIn(value-count+1,value) else steps.coerceIn(-160,160)
    }
    fun afterSteps(value:Int,count:Int,steps:Int):Int {
        require(count>=2 && value in 0 until count)
        val accepted=clampSteps(value,count,steps)
        return if(count==2) value-accepted else ((value-accepted)%count+count)%count
    }
    fun rowValue(value:Int,count:Int,row:Int,shifted:Int):Int? {
        require(count>=2 && value in 0 until count)
        val index=value+row-shifted
        return if(count==2) index.takeIf{it in 0 until count}
            else ((index%count)+count)%count
    }
    fun predictedSteps(value:Int,count:Int,offsetPx:Float,coastPx:Float,stepPx:Float):Int {
        require(stepPx.isFinite() && stepPx>0f)
        val displacement=offsetPx+coastPx
        if(!displacement.isFinite())return 0
        return clampSteps(value,count,(displacement/stepPx).roundToInt())
    }
}
