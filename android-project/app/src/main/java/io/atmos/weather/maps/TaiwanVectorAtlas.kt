package io.atmos.weather.maps

import android.content.Context
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import kotlin.math.abs

/** Offline NLSC county contours. Paths stay vectors at every camera scale. */
class TaiwanVectorAtlas private constructor(private val levels:List<Level>) {
    private data class Level(val tolerance:Float,val path:android.graphics.Path,val sourcePaths:Int)
    internal val contourDrawCalls get()=2
    internal val sourceDrawCalls get()=levels.last().sourcePaths*2
    private val land=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xff284c52.toInt();style=Paint.Style.FILL }
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0xff80b5b5.toInt();style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND}

    fun draw(scope:DrawScope,project:(Double,Double)->Offset) {
        val origin=project(118.0,30.0)
        val unit=project(119.0,29.0)
        val sx=unit.x-origin.x;val sy=unit.y-origin.y
        if(!sx.isFinite() || !sy.isFinite() || abs(sx)<.001f || abs(sy)<.001f)return
        val pixelsPerDegree=maxOf(abs(sx),abs(sy))
        val level=levels.firstOrNull {it.tolerance*pixelsPerDegree<=.55f} ?: levels.last()
        val canvas=scope.drawContext.canvas.nativeCanvas
        canvas.save();canvas.clipRect(0f,0f,scope.size.width,scope.size.height);canvas.translate(origin.x,origin.y);canvas.scale(sx,sy)
        edge.strokeWidth=(.8f*scope.density/pixelsPerDegree).coerceAtLeast(.000001f)
        canvas.drawPath(level.path,land);canvas.drawPath(level.path,edge)
        canvas.restore()
    }

    companion object {
        @Volatile private var cache:TaiwanVectorAtlas?=null
        suspend fun load(context:Context):TaiwanVectorAtlas=withContext(Dispatchers.IO) {
            cache ?: synchronized(this@Companion) {
                cache ?: DataInputStream(context.assets.open("maps/taiwan_counties.tkatlas").buffered()).use { input ->
                    val header=ByteArray(8);input.readFully(header)
                    require(String(header,Charsets.US_ASCII)=="TKMAP001")
                    val count=input.readInt();require(count in 1..4)
                    TaiwanVectorAtlas(List(count) {
                        val tolerance=input.readFloat();val rings=input.readInt();require(rings in 1..10000)
                        val paths=List(rings) {
                            val points=input.readInt();require(points in 4..1000000)
                            android.graphics.Path().apply {
                                fillType=android.graphics.Path.FillType.EVEN_ODD
                                repeat(points) {i ->val x=input.readFloat();val y=input.readFloat()
                                    require(x.isFinite() && y.isFinite())
                                    if(i==0)moveTo(x,y) else lineTo(x,y)
                                };close()
                            }
                        }
                        Level(tolerance,android.graphics.Path().apply{fillType=android.graphics.Path.FillType.EVEN_ODD;paths.forEach{addPath(it)}},paths.size)
                    }).also {cache=it}
                }
            }
        }
    }
}

@Composable fun rememberTaiwanVectorAtlas():TaiwanVectorAtlas? {
    val context=LocalContext.current.applicationContext
    return produceState<TaiwanVectorAtlas?>(null,context) {value=TaiwanVectorAtlas.load(context)}.value
}

/** Greedy screen-space label placement keeps offshore names from piling up. */
fun DrawScope.drawTaiwanPlaceLabels(project:(Double,Double)->Offset) {
    val canvas=drawContext.canvas.nativeCanvas
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=0xffe6f3f5.toInt();textSize=12*density
        typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
        setShadowLayer(3*density,0f,density,0xff06151c.toInt())
    }
    val occupied=mutableListOf<android.graphics.RectF>()
    listOf(Triple("臺灣",121.0,23.65),Triple("澎湖",119.57,23.57),
        Triple("金門",118.38,24.44),Triple("馬祖",119.95,26.17)).forEach {(name,lon,lat) ->
        val p=project(lon,lat);val width=paint.measureText(name);val height=16*density
        if(p.x<0 || p.y<0 || p.x>size.width || p.y>size.height)return@forEach
        val candidates=listOf(Offset(7*density,-7*density),Offset(7*density,19*density),
            Offset(-width-7*density,-7*density),Offset(-width-7*density,19*density))
        val box=candidates.map {off ->android.graphics.RectF(p.x+off.x,p.y+off.y-height,p.x+off.x+width,p.y+off.y+4*density)}
            .firstOrNull {r ->r.left>=4*density && r.top>=4*density && r.right<=size.width-4*density &&
                r.bottom<=size.height-22*density && occupied.none {android.graphics.RectF.intersects(it,r)}}
        if(box!=null) {occupied+=box;canvas.drawText(name,box.left,box.bottom-4*density,paint)}
    }
}

fun DrawScope.drawTaiwanAtlasCredit() {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0xffbad2d7.toInt();textSize=9*density
        setShadowLayer(2*density,0f,0f,0xff07151b.toInt())}
    drawContext.canvas.nativeCanvas.drawText("向量底圖 © 國土測繪中心 · 開放資料",9*density,size.height-8*density,paint)
}
