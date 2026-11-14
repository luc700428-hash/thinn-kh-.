package io.atmos.weather.widgets

import kotlin.math.min
import kotlin.math.sqrt

/** All dimensions are available launcher dp. Text is fitted after both font scales. */
data class WidgetLayoutPlan(
    val width:Float,val height:Float,val padding:Float,val mode:Mode,
    val hero:Float,val city:Float,val body:Float,val caption:Float,val icon:Float,
    val clock:Float,val dial:Float,val hours:Int,val metrics:Int,
    val showFooter:Boolean,val showHighLow:Boolean,val showAgenda:Boolean,
    val systemFontScale:Float,val textScale:Float=1f
) {
    enum class Mode { STRIP, SQUARE, WIDE, PORTRAIT, EXPANDED }
    val typeGrowth get()=sqrt((minOf(width,height)/180f).coerceAtLeast(1f)).coerceAtMost(2.6f)
    val detailHeight get()=innerHeight/(body/14f).coerceAtLeast(1f)
    val strip get()=mode==Mode.STRIP
    val wide get()=mode==Mode.WIDE||mode==Mode.EXPANDED
    val innerWidth get()=(width-padding*2).coerceAtLeast(1f)
    val innerHeight get()=(height-padding*2).coerceAtLeast(1f)
    fun sp(visualDp:Float)=visualDp/systemFontScale
    companion object {
        fun create(width:Float,height:Float,appScale:Float=1f,systemScale:Float=1f,widgetScale:Float=1f):WidgetLayoutPlan {
            val w=width.takeIf{it.isFinite()}?.coerceIn(60f,1600f)?:160f
            val h=height.takeIf{it.isFinite()}?.coerceIn(48f,1600f)?:160f
            val sys=systemScale.takeIf{it.isFinite()}?.coerceIn(.85f,2f)?:1f
            val custom=WidgetTypography.bounded(widgetScale)
            // App reading size stays inside the app. Widgets have their own preference.
            val read=sys
            val mode=when{
                h<150f||w/h>3.2f->Mode.STRIP
                h/w>1.4f->Mode.PORTRAIT
                w/h>1.4f->Mode.WIDE
                w>=260f&&h>=260f->Mode.EXPANDED
                else->Mode.SQUARE
            }
            val growth=sqrt((minOf(w,h)/180f).coerceAtLeast(1f)).coerceAtMost(2.6f)
            val pad=if(minOf(w,h)<100f)8f else if(minOf(w,h)<140f)11f else 16f // iOS widget margins
            val iw=w-pad*2;val ih=h-pad*2;val max=WidgetTypography.MAX
            // Fit the BASE against the largest preference. Optional detail changes first.
            val bodyBase=minOf(14f*read*growth,iw/(4f*max),if(mode==Mode.STRIP)Float.MAX_VALUE else ih/(7.2f*max))
            val cityBase=bodyBase
            val heroBase=minOf(44f*read*growth,iw/(2f*max),ih*(if(mode==Mode.STRIP)1f else .54f)/(1.65f*max))
            val clockBase=minOf(48f*read*growth,iw/(3.2f*max),ih*(if(mode==Mode.STRIP)1f else .42f)/(1.65f*max))
            val icon=minOf(iw*.40f,ih*.42f,210f*growth)
            return WidgetLayoutPlan(w,h,pad,mode,heroBase*custom,cityBase*custom,bodyBase*custom,bodyBase*.86f*custom,
                icon,clockBase*custom,minOf(iw,ih*.55f),
                (iw/(bodyBase*max*3.8f)).toInt().coerceIn(1,6),if(ih>280)4 else 2,
                ih>180,ih>150,ih>420,sys,custom)
        }
    }
}
