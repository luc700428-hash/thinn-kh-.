package io.atmos.weather.earthquake

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.alerts.WarningCountyAtlas
import io.atmos.weather.alerts.WarningMapLayout
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassTextButton

internal object SeismicIntensityColors {
    fun color(value:String?):Color=when(value){
        "0"->Color(0xFFD4DFE8);"1"->Color(0xFFB1D9C6);"2"->Color(0xFF59BA9E);"3"->Color(0xFF12A269)
        "4"->Color(0xFFF0CB4B);"5","5弱"->Color(0xFFF4A447);"5強"->Color(0xFFF07848)
        "6","6弱"->Color(0xFFE14D4B);"6強"->Color(0xFFBD202D);"7"->Color(0xFF9644C7);else->Color(0xFF81909E)
    }
}
/** County detail stays vector. Advancing rings redraw independently of static polygon/label layers. */
@Composable internal fun SeismicWarningMap(incident:EewIncident,values:Map<String,String>,place:SeismicPlace?,historical:Boolean,frameNow:State<Long>,modifier:Modifier=Modifier) {
    val ctx=LocalContext.current.applicationContext
    val atlas by produceState<WarningCountyAtlas?>(null,ctx){value=WarningCountyAtlas.load(ctx)}
    val dark=MaterialTheme.colorScheme.background.red<.5f
    val readingScale=LocalDensity.current.fontScale.coerceIn(1f,1.3f)
    val sea=if(dark)Color(0xFF092233) else Color(0xFFDEEEF6)
    var zoom by remember(incident.id){mutableFloatStateOf(1f)};var pan by remember(incident.id){mutableStateOf(Offset.Zero)}
    val fill=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}}
    val edge=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND}}
    val labels=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}}
    Column(modifier){
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            (if(historical)listOf("1","2","3","4","5","6","7") else SeismicAlertThreshold.grades).forEach{grade->Box(Modifier.clip(RoundedCornerShape(6.dp)).background(SeismicIntensityColors.color(grade)).padding(horizontal=7.dp,vertical=4.dp)){
                Text(SeismicDisplayFormat.symbol(grade),fontSize=14.sp,color=if(grade in listOf("6強","7"))Color.White else Color(0xFF101B22))
            }}
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(310.dp).clip(RoundedCornerShape(24.dp)).background(sea)
            .semantics{contentDescription="可縮放的臺灣縣市震度地圖；依來源縣市震度著色，灰色為未提供"}
            .pointerInput(incident.id){detectTransformGestures{centroid,move,factor,_->
                if(factor.isFinite()&&factor>0&&move.x.isFinite()&&move.y.isFinite()){
                    val next=(zoom*factor).coerceIn(1f,8f);val anchor=centroid-Offset(size.width/2f,size.height/2f)
                    val delta=anchor+(pan-anchor)*(next/zoom)+move
                    zoom=next;pan=Offset(delta.x.coerceIn(-size.width*next,size.width*next),delta.y.coerceIn(-size.height*next,size.height*next))
                }
            }}){
            Canvas(Modifier.matchParentSize().graphicsLayer()){
                val loaded=atlas?:return@Canvas;val canvas=drawContext.canvas.nativeCanvas
                WarningMapLayout.viewports(size.width,size.height,density,zoom,pan).forEach{view->
                    canvas.save();canvas.clipRect(view.rectangle.left,view.rectangle.top,view.rectangle.right,view.rectangle.bottom);canvas.concat(view.matrix())
                    edge.color=if(dark)0xff163741.toInt() else 0xff4c6e77.toInt();edge.strokeWidth=(.75f*density/view.scale).toFloat()
                    loaded.counties.filter{view.county==null||view.county==it.name}.forEach{shape->
                        val color=SeismicIntensityColors.color(values[shape.name]?:values[shape.name.replace('臺','台')])
                        fill.color=android.graphics.Color.rgb((color.red*255).toInt(),(color.green*255).toInt(),(color.blue*255).toInt())
                        canvas.drawPath(shape.path,fill);canvas.drawPath(shape.path,edge)
                    };canvas.restore()
                    val occupied=mutableListOf<android.graphics.RectF>()
                    labels.textSize=12*density*readingScale
                    labels.setShadowLayer(2*density,0f,0f,if(dark)0xff081924.toInt() else 0xffeffaff.toInt())
                    loaded.counties.filter{if(view.county==null)it.name !in setOf("澎湖縣","金門縣","連江縣") else it.name==view.county}.forEach{shape->
                        val ground=SeismicIntensityColors.color(values[shape.name]?:values[shape.name.replace('臺','台')])
                        val lightInk=if(view.county!=null)dark else ground.luminance()<.18f
                        labels.color=if(lightInk)android.graphics.Color.WHITE else android.graphics.Color.BLACK
                        labels.setShadowLayer(1.5f*density,0f,0f,if(lightInk)0xff071923.toInt() else 0xfff6fcff.toInt())
                        val at=view.project(shape.label.first,shape.label.second);val w=labels.measureText(shape.name)
                        val box=android.graphics.RectF(at.x-w/2,at.y-labels.textSize-2*density,at.x+w/2,at.y+2*density)
                        if(view.rectangle.contains(at)&&occupied.none{android.graphics.RectF.intersects(it,box)}){canvas.drawText(shape.name,box.left,at.y,labels);occupied+=box}
                    }
                }
            }
            Canvas(Modifier.matchParentSize().graphicsLayer()){
                val main=WarningMapLayout.viewports(size.width,size.height,density,zoom,pan).first()
                if(SeismicGeo.visible(incident.latitude,incident.longitude)){
                    val at=main.project(incident.longitude!!,incident.latitude!!)
                    val wave=incident.originAtMs?.let{SeismicWavePhysics.wavefront(it,frameNow.value,incident.depthKm?:0.0)}
                    if(wave!=null){val px=main.scale/111.2
                        if(wave.pSurfaceRadiusKm>0)drawCircle(Color(0xFFFFA63D),(wave.pSurfaceRadiusKm*px).toFloat(),at,style=Stroke(1.5.dp.toPx()))
                        if(wave.sSurfaceRadiusKm>0)drawCircle(Color(0xFFE34844),(wave.sSurfaceRadiusKm*px).toFloat(),at,style=Stroke(2.dp.toPx()))
                    }
                    val d=9.dp.toPx();listOf(-1,1).forEach{n->
                        drawLine(Color.White,at+Offset(-d,n*d),at+Offset(d,-n*d),7.dp.toPx())
                        drawLine(Color(0xFFD32630),at+Offset(-d,n*d),at+Offset(d,-n*d),3.5.dp.toPx())
                    }
                }
                place?.let{val at=main.project(it.lon,it.lat);drawCircle(Color.White,6.dp.toPx(),at);drawCircle(Color(0xFF007AFF),4.dp.toPx(),at)}
            }
            GlassTextButton({zoom=1f;pan=Offset.Zero},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){Text("重設地圖")}
            Text(if(atlas==null)"載入縣市向量地圖…" else "© 國土測繪中心 · 灰：未提供",fontSize=14.sp,color=if(dark)Color(0xFFD2E8F6) else Color(0xFF1C465D),
                modifier=Modifier.align(Alignment.BottomStart).background(sea.copy(alpha=.94f)).padding(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(if(historical)"色塊：921 歷史最大震度（舊制） · 波圈為演練示意" else "色塊：來源預估震度 · 波圈為示意，非到達預測",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
    }
}
