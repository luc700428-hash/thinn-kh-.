package io.atmos.weather.alerts

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import kotlin.math.*

internal class WarningCountyShape(val name:String,val code:String,val label:Pair<Double,Double>,val rings:List<FloatArray>) {
    val path=Path().apply {
        fillType=Path.FillType.EVEN_ODD
        rings.forEach{ring->for(i in ring.indices step 2) {
            if(i==0)moveTo(ring[i],ring[i+1]) else lineTo(ring[i],ring[i+1])
        };close()}
    }
    fun contains(longitude:Double,latitude:Double):Boolean {
        var inside=false
        rings.forEach{ring->
            var previous=ring.size-2
            for(current in ring.indices step 2) {
                val x=ring[current].toDouble();val y=ring[current+1].toDouble()
                val px=ring[previous].toDouble();val py=ring[previous+1].toDouble()
                if((y>latitude)!=(py>latitude)&&longitude<(px-x)*(latitude-y)/(py-y)+x)inside=!inside
                previous=current
            }
        }
        return inside
    }
}

internal class WarningCountyAtlas private constructor(val counties:List<WarningCountyShape>) {
    companion object {
        @Volatile private var cache:WarningCountyAtlas?=null
        fun parse(text:String):WarningCountyAtlas {
            val root=JSONObject(text);require(root.getInt("schema")==1)
            val list=root.getJSONArray("counties");require(list.length()==22)
            var pointCount=0
            val shapes=(0 until list.length()).map{i->
                val county=list.getJSONObject(i);val name=county.getString("name")
                val label=county.getJSONArray("label")
                val raw=county.getJSONArray("rings");require(raw.length() in 1..2000)
                val rings=(0 until raw.length()).map{r->
                    val points=raw.getJSONArray(r);require(points.length() in 4..100000)
                    pointCount+=points.length();require(pointCount<=200000)
                    FloatArray(points.length()*2).also{out->for(p in 0 until points.length()) {
                        val v=points.getJSONArray(p);val lon=v.getDouble(0);val lat=v.getDouble(1)
                        require(lon in 110.0..126.0&&lat in 9.0..28.0)
                        out[p*2]=lon.toFloat();out[p*2+1]=lat.toFloat()
                    }}
                }
                WarningCountyShape(name,county.getString("code"),label.getDouble(0) to label.getDouble(1),rings)
            }
            require(shapes.map{it.name}.toSet()==CwaWarningGeography.counties.toSet())
            return WarningCountyAtlas(shapes)
        }
        suspend fun load(context:Context):WarningCountyAtlas=withContext(Dispatchers.IO) {
            cache?:synchronized(this@Companion) {
                cache?:GZIPInputStream(context.assets.open("maps/warning_counties.tkatlasz")).bufferedReader().use{parse(it.readText())}.also{cache=it}
            }
        }
    }
}

internal data class WarningMapViewport(val rectangle:Rect,val west:Double,val south:Double,val east:Double,val north:Double,
    val county:String?=null,val zoom:Float=1f,val pan:Offset=Offset.Zero) {
    private val cosine=cos(Math.toRadians((south+north)/2))
    val scale=min(rectangle.width/((east-west)*cosine),rectangle.height/(north-south))*zoom
    private val centerX=(west+east)/2;private val centerY=(north+south)/2
    fun project(lon:Double,lat:Double)=Offset((rectangle.center.x+pan.x+(lon-centerX)*scale*cosine).toFloat(),
        (rectangle.center.y+pan.y-(lat-centerY)*scale).toFloat())
    fun unproject(point:Offset)=((point.x-rectangle.center.x-pan.x)/(scale*cosine)+centerX) to
        (centerY-(point.y-rectangle.center.y-pan.y)/scale)
    fun matrix()=Matrix().apply{setValues(floatArrayOf((scale*cosine).toFloat(),0f,
        (rectangle.center.x+pan.x-centerX*scale*cosine).toFloat(),0f,-scale.toFloat(),
        (rectangle.center.y+pan.y+centerY*scale).toFloat(),0f,0f,1f))}
}

internal object WarningMapLayout {
    fun viewports(width:Float,height:Float,density:Float,zoom:Float=1f,pan:Offset=Offset.Zero):List<WarningMapViewport> {
        val margin=8*density;val side=width*.245f
        val main=WarningMapViewport(Rect(side+margin,margin,width-margin,height-margin),119.90,21.65,122.30,25.50,zoom=zoom,pan=pan)
        val slots=listOf(
            WarningMapViewport(Rect.Zero,119.87,25.89,120.55,26.45,"連江縣"),
            WarningMapViewport(Rect.Zero,118.10,24.10,119.53,25.05,"金門縣"),
            WarningMapViewport(Rect.Zero,119.28,23.15,119.78,23.85,"澎湖縣"))
        val slot=(height-2*margin)/slots.size
        return listOf(main)+slots.mapIndexed{i,v->v.copy(rectangle=Rect(margin,margin+i*slot+22*density,side-margin,margin+(i+1)*slot-12*density))}
    }
    fun hit(atlas:WarningCountyAtlas,views:List<WarningMapViewport>,point:Offset):String? {
        val view=views.lastOrNull{it.rectangle.inflate(4f).contains(point)}?:return null
        view.county?.let{return it}
        val geo=view.unproject(point)
        return atlas.counties.firstOrNull{it.contains(geo.first,geo.second)}?.name
    }
}
