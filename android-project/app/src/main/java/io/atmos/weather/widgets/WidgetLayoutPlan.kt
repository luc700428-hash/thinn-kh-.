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
    val typeGrowth get()=sqrt((minOf(width,height)/180f).coerceAtLeast(1f)).coerceAtMost(2f)
    val detailHeight get()=height/typeGrowth/textScale.coerceAtLeast(1f)
    val strip get()=mode==Mode.STRIP
    val wide get()=mode==Mode.WIDE||mode==Mode.EXPANDED
    val innerWidth get()=(width-padding*2).coerceAtLeast(1f)
    val innerHeight get()=(height-padding*2).coerceAtLeast(1f)
    fun sp(visualDp:Float)=visualDp/systemFontScale
    companion object {
        fun create(width:Float,height:Float,appScale:Float=1f,systemScale:Float=1f,widgetScale:Float=1f):WidgetLayoutPlan {
            val w=width.takeIf{it.isFinite()}?.coerceIn(60f,1200f)?:160f
            val h=height.takeIf{it.isFinite()}?.coerceIn(48f,1200f)?:160f
            val sys=systemScale.takeIf{it.isFinite()}?.coerceIn(.85f,2f)?:1f
            val custom=WidgetTypography.bounded(widgetScale)
            val scale=(appScale.takeIf{it.isFinite()}?.coerceIn(1f,1.5f)?:1f)*sys*custom
            val mode=when {
                h<156f||(w<138f&&h<180f)->Mode.STRIP
                w>=260f&&h>=260f->Mode.EXPANDED
                w>=260f->Mode.WIDE
                h>=280f->Mode.PORTRAIT
                else->Mode.SQUARE
            }
            val growth=sqrt((minOf(w,h)/180f).coerceAtLeast(1f)).coerceAtMost(2f)
            val details=h/growth/custom.coerceAtLeast(1f)
            val strip=mode==Mode.STRIP
            val pad=if(h<76f||w<120f)6f else if(strip)10f else if(w>=260f)16f else 12f
            val iw=w-2*pad;val ih=h-2*pad
            val city=min(14f*scale*growth,min(18f*growth*custom,iw/6f)).coerceAtLeast(10f)
            val body=min(13f*scale*growth,16f*growth*custom)
            val caption=min(11f*scale*growth,13f*growth*custom)
            val icon=if(strip)min(46f,ih) else min(if(w>=260f)80f*growth else 68f*growth,ih*.40f)
            val desiredHero=if(strip)min(32f*scale,min(ih*.54f,iw*.27f)) else min(if(h<210f)44f*custom else min(64f*growth*custom,58f*scale*growth),min(iw*.42f,ih*.40f))
            val readingBudget=(ih-city*1.7f-body*1.7f-(if(h>=240f&&ih>=200f)caption*1.7f+8f else 0f)-16f)/1.4f
            val hero=if(!strip&&custom>1f)min(desiredHero,readingBudget.coerceAtLeast(12f)) else desiredHero
            val clock=min(64f*scale*growth,min(iw/3.05f,ih*.38f))
            val dial=min(iw,ih-city*1.5f-body*1.5f-14f-(if(h>=240f)26f else 0f)).coerceAtLeast(24f)
            return WidgetLayoutPlan(w,h,pad,mode,hero,city,body,caption,icon,clock,dial,
                (iw/(48f*min(scale,1.25f)*growth)).toInt().coerceIn(1,6),if(details>=280f)4 else if(details>=180f)2 else 0,
                h>=240f&&ih>=200f,details>=172f,details>=420f,sys,custom)
        }
    }
}
