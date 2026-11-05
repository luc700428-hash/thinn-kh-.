package io.atmos.weather.earthquake

import kotlin.math.min

internal data class SeismicPanePlan(val sideBySide:Boolean,val inspectorWidth:Float,val mapHeight:Float)
internal object SeismicPaneGeometry {
    fun plan(width:Float,height:Float,fontScale:Float,criticalFirst:Boolean=false):SeismicPanePlan {
        val w=width.takeIf{it.isFinite()}?.coerceAtLeast(1f)?:1f
        val h=height.takeIf{it.isFinite()}?.coerceAtLeast(1f)?:1f
        val scale=fontScale.takeIf{it.isFinite()}?.coerceIn(1f,2f)?:1f
        val minimum=(if(criticalFirst)340f else 300f)*min(scale,1.25f)
        val wide=w>=minimum+360f+20f+32f&&h>=250f
        val inspector=(w*.34f).coerceIn(minimum,maxOf(minimum,420f))
        val mapWidth=if(wide)w-32f-20f-inspector else w-32f
        val chrome=if(scale>1.2f)220f else 185f
        val summary=if(scale>1.2f)220f else 130f
        val mapHeight=if(criticalFirst){if(wide)min(mapWidth*1.08f,h-100f) else min(mapWidth*1.05f,600f)}
            else min(mapWidth*1.05f,h-chrome-if(wide)0f else summary)
        return SeismicPanePlan(wide,inspector,mapHeight.coerceIn(180f,720f))
    }
}
