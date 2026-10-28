package io.atmos.weather.maps

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import io.atmos.weather.typhoon.TyphoonViewport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.*

/** Cached native paths retain the source coastlines. Camera changes transform paths, not bitmaps.
 * The .tkatlasz suffix preserves gzip bytes: Android asset merging automatically expands .gz files. */
class RegionalVectorAtlas private constructor(private val polygons:List<Polygon>,val vertexCount:Int) {
    private data class Detail(val path:Path,val vertices:Int)
    private data class Polygon(val minor:Boolean,val bounds:RectF,val details:List<Detail>)
    private data class Key(val level:Int,val left:Int,val top:Int,val right:Int,val bottom:Int)
    private data class Batch(val path:Path,val polygons:Int,val vertices:Int,val minor:List<Polygon>)
    internal data class RenderStats(val vertices:Int,val polygons:Int,val contourDrawCalls:Int,val errorPixels:Float)
    private val batches=object:LinkedHashMap<Key,Batch>(4,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Key,Batch>?)=size>4
    }
    internal var batchBuildCount=0;private set
    private fun batch(view:TyphoonViewport):Pair<Key,Batch> {
        val ppd=(111.2*view.pxPerKm).toFloat()
        val level=TOLERANCES.indexOfFirst{it*ppd<=.4f}.coerceAtLeast(0)
        val bounds=view.rasterBounds()
        val key=Key(level,floor(bounds.west/2).toInt()-1,floor(bounds.south/2).toInt()-1,
            ceil(bounds.east/2).toInt()+1,ceil(bounds.north/2).toInt()+1)
        return key to batches.getOrPut(key) {
            val visible=polygons.filter{it.bounds.right>=key.left*2 && it.bounds.left<=key.right*2 &&
                it.bounds.bottom>=key.top*2 && it.bounds.top<=key.bottom*2}
            val path=Path().apply{fillType=Path.FillType.EVEN_ODD;visible.forEach{addPath(it.details[level].path)}}
            batchBuildCount++
            Batch(path,visible.size,visible.sumOf{it.details[level].vertices},visible.filter{it.minor})
        }
    }
    internal fun renderStats(view:TyphoonViewport):RenderStats {
        val (key,batch)=batch(view)
        return RenderStats(batch.vertices,batch.polygons,if(batch.polygons>0)2 else 0,
            TOLERANCES[key.level]*(111.2*view.pxPerKm).toFloat())
    }
    val polygonCount get()=polygons.size
    val minorIslandCount get()=polygons.count{it.minor}
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND}
    fun hasLandNear(lon:Double,lat:Double,tolerance:Double=.08)=polygons.any {
        lon>=it.bounds.left-tolerance&&lon<=it.bounds.right+tolerance&&lat>=it.bounds.top-tolerance&&lat<=it.bounds.bottom+tolerance
    }
    fun draw(scope:DrawScope,view:TyphoonViewport,dark:Boolean) {
        val pixelsPerDegree=(111.2*view.pxPerKm).toFloat()
        if(!pixelsPerDegree.isFinite()||pixelsPerDegree<=0f)return
        val (_,batch)=batch(view)
        fill.color=if(dark)0xff294d51.toInt() else 0xffd3e3d1.toInt()
        edge.color=if(dark)0xff8cb6bd.toInt() else 0xff628a91.toInt()
        edge.strokeWidth=(.75f*scope.density/pixelsPerDegree).coerceAtLeast(.000001f)
        val origin=view.project(100.0,0.0);val canvas=scope.drawContext.canvas.nativeCanvas
        canvas.save();canvas.clipRect(0f,0f,scope.size.width,scope.size.height)
        canvas.translate(origin.first,origin.second)
        canvas.scale((pixelsPerDegree*view.cosCenter).toFloat(),-pixelsPerDegree)
        // One immutable vector batch replaces thousands of drawPath calls per gesture frame.
        canvas.drawPath(batch.path,fill);canvas.drawPath(batch.path,edge)
        val dots=FloatArray(batch.minor.size*2);var count=0
        batch.minor.forEach {p->
            if(p.bounds.width()*pixelsPerDegree<scope.density*1.4f) {
                dots[count++]=p.bounds.centerX()-100;dots[count++]=p.bounds.centerY()
            }
        }
        if(count>0) {
            val previous=fill.style
            fill.style=Paint.Style.STROKE;fill.strokeCap=Paint.Cap.ROUND
            fill.strokeWidth=1.4f*scope.density/pixelsPerDegree
            canvas.drawPoints(dots,0,count,fill);fill.style=previous
        }
        canvas.restore()
    }
    companion object {
        private val TOLERANCES=floatArrayOf(.04f,.008f,.0016f,0f)
        @Volatile private var cached:RegionalVectorAtlas?=null
        suspend fun load(context:Context):RegionalVectorAtlas=withContext(Dispatchers.IO) {
            cached?:synchronized(this@Companion) {
                cached?:DataInputStream(GZIPInputStream(context.assets.open("maps/west_pacific_10m.tkatlasz")).buffered()).use {input->
                    val magic=ByteArray(8);input.readFully(magic);require(String(magic,Charsets.US_ASCII)=="TKREG001")
                    val count=input.readInt();require(count in 1..20_000);var total=0
                    val data=List(count) {
                        val minor=input.readBoolean()
                        val box=RectF(input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat())
                        require(box.left.isFinite()&&box.top.isFinite()&&box.right.isFinite()&&box.bottom.isFinite())
                        val ringCount=input.readInt();require(ringCount in 1..10_000)
                        val rings=List(ringCount) {
                            val n=input.readInt();require(n in 4..1_000_000);total+=n;require(total<=2_000_000)
                            FloatArray(n*2) {i->input.readFloat().also {v->
                                require(v.isFinite() && if(i%2==0)v in -10f..80f else v in -15f..60f)
                            }}
                        }
                        val details=TOLERANCES.map {tolerance->
                            var vertices=0
                            val path=Path().apply {
                                fillType=Path.FillType.EVEN_ODD
                                rings.forEach {source->
                                    val ring=VectorRingSimplifier.simplify(source,tolerance);vertices+=ring.size/2
                                    moveTo(ring[0],ring[1]);for(i in 1 until ring.size/2)lineTo(ring[i*2],ring[i*2+1]);close()
                                }
                            }
                            Detail(path,vertices)
                        }
                        Polygon(minor,box,details)
                    }
                    RegionalVectorAtlas(data,total).also{cached=it}
                }
            }
        }
    }
}
@Composable fun rememberRegionalVectorAtlas():RegionalVectorAtlas? {
    val context=LocalContext.current.applicationContext
    return produceState<RegionalVectorAtlas?>(null,context) {value=RegionalVectorAtlas.load(context)}.value
}

/** Country, sea and island names use the same geographic camera and avoid text collisions. */
fun DrawScope.drawRegionalPlaceLabels(view:TyphoonViewport,dark:Boolean) {
    data class Label(val text:String,val lon:Double,val lat:Double,val sea:Boolean=false,val minScale:Double=0.0)
    val labels=listOf(
        Label("台灣",121.0,23.65),Label("日本",138.0,36.6),Label("菲律賓",122.3,12.8),
        Label("中國",110.0,34.0),Label("南韓",127.7,36.3),Label("北韓",126.6,40.0),Label("越南",106.2,18.5),
        Label("馬來西亞",113.5,3.3),Label("印尼",116.0,-2.5),Label("帛琉",134.55,7.5),
        Label("關島",144.79,13.45),Label("塞班島",145.75,15.18),
        Label("東海",125.2,29.3,true),Label("菲律賓海",134.0,20.5,true),Label("南海",114.0,15.5,true),
        Label("西北太平洋",151.0,27.0,true),Label("日本海",134.0,41.0,true),Label("黃海",122.7,35.0,true),
        Label("澎湖",119.57,23.57,false,.07),Label("金門",118.38,24.44,false,.07),Label("馬祖",119.95,26.17,false,.07),
        Label("臺灣海峽",119.5,24.5,true,.14),Label("沖繩",127.7,26.3,false,.08),Label("宮古島",125.3,24.8,false,.16),
        Label("石垣島",124.2,24.4,false,.16),Label("釣魚臺（台灣）",123.53,25.75,false,.22),
        Label("綠島",121.49,22.67,false,.30),Label("蘭嶼",121.55,22.04,false,.30))
    val canvas=drawContext.canvas.nativeCanvas;val occupied=mutableListOf<RectF>()
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
        textSize=12*density
        if(dark)setShadowLayer(2*density,0f,density,0xff06151c.toInt())
    }
    labels.forEach {label->
        if(view.pxPerKm<label.minScale)return@forEach
        val (x,y)=view.project(label.lon,label.lat);val w=paint.measureText(label.text)
        if(x<0||x>size.width||y<0||y>size.height)return@forEach
        val box=RectF((x+5*density).coerceAtMost(size.width-w-5*density),y-17*density,(x+5*density).coerceAtMost(size.width-w-5*density)+w,y+2*density)
        if(box.left<3*density||box.top<4*density||box.bottom>size.height-26*density||occupied.any {RectF.intersects(it,box)})return@forEach
        occupied+=box
        paint.color=if(dark)if(label.sea)0xffaecbdc.toInt() else 0xffeef7fa.toInt() else if(label.sea)0xff36596f.toInt() else 0xff203e40.toInt()
        canvas.drawText(label.text,box.left,box.bottom-2*density,paint)
    }
}
