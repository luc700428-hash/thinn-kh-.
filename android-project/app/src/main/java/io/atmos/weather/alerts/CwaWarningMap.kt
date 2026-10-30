package io.atmos.weather.alerts

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal data class WarningMapColours(val background:Int,val land:Int,val edge:Int,val warning:Int,val warningLand:Int,val text:Int,val selected:Int,
    val upcoming:Int,val upcomingLand:Int) {
    companion object {
        fun forTheme(dark:Boolean,selected:Int)=if(dark)WarningMapColours(0xFF0D202C.toInt(),0xFF294552.toInt(),0xFF83A6B6.toInt(),0xFFFFB4AB.toInt(),0xFF5F3036.toInt(),0xFFF1F7FA.toInt(),selected,0xFFFFD88C.toInt(),0xFF57452B.toInt())
            else WarningMapColours(0xFFF1F6F9.toInt(),0xFFD9E5E9.toInt(),0xFF567686.toInt(),0xFF9F231C.toInt(),0xFFFFDAD6.toInt(),0xFF102733.toInt(),selected,0xFF815100.toInt(),0xFFFFE5AD.toInt())
    }
}

/** Only an administrative overview: hatching never claims an entire county is a hazard polygon. */
internal class WarningMapPainter {
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND}
    private val hatch=Paint(Paint.ANTI_ALIAS_FLAG)
    private val label=Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND}
    fun draw(canvas:android.graphics.Canvas,width:Float,height:Float,density:Float,fontScale:Float,atlas:WarningCountyAtlas,
        state:CwaWarningMapState,selected:String?,colours:WarningMapColours,zoom:Float=1f,pan:Offset=Offset.Zero) {
        val views=WarningMapLayout.viewports(width,height,density,zoom,pan)
        canvas.drawColor(colours.background)
        label.textSize=14*density*fontScale;label.color=colours.text
        halo.color=colours.background;halo.strokeWidth=3*density
        views.forEach{view->
            val rect=view.rectangle;val saved=canvas.save();canvas.clipRect(rect.left,rect.top,rect.right,rect.bottom)
            val matrix=view.matrix();val inverse=android.graphics.Matrix();matrix.invert(inverse)
            val shapes=atlas.counties.filter{view.county==null||it.name==view.county}
            shapes.forEach{county->
                val active=county.name in state.titles
                val upcoming=county.name in state.upcoming
                val warningInk=if(upcoming)colours.upcoming else colours.warning
                val pathSaved=canvas.save();canvas.concat(matrix)
                fill.color=if(active){if(upcoming)colours.upcomingLand else colours.warningLand} else colours.land
                edge.color=if(active)warningInk else colours.edge
                edge.strokeWidth=(.9f*density/view.scale).toFloat()
                canvas.drawPath(county.path,fill)
                if(active) {
                    val clipped=canvas.save();canvas.clipPath(county.path);canvas.concat(inverse)
                    hatch.color=warningInk;hatch.strokeWidth=.8f*density
                    var x=rect.left-rect.height
                    while(x<rect.right){canvas.drawLine(x,rect.bottom,x+rect.height,rect.top,hatch);x+=9*density}
                    canvas.restoreToCount(clipped)
                }
                canvas.drawPath(county.path,edge);canvas.restoreToCount(pathSaved)
            }
            shapes.firstOrNull{it.name==selected}?.let{county->
                val outline=canvas.save();canvas.concat(matrix)
                edge.color=colours.selected;edge.strokeWidth=(2.6f*density/view.scale).toFloat()
                canvas.drawPath(county.path,edge);canvas.restoreToCount(outline)
            }
            if(view.county==null) {
                val occupied=mutableListOf<Rect>()
                shapes.sortedWith(compareByDescending<WarningCountyShape>{it.name==selected}.thenByDescending{it.name in state.titles}).forEach{county->
                    val point=view.project(county.label.first,county.label.second)
                    label.typeface=if(county.name in state.titles)android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    halo.typeface=label.typeface;halo.textSize=label.textSize
                    val w=label.measureText(county.name);val h=label.textSize
                    val candidates=listOf(Offset(-w/2,0f),Offset(5*density,-6*density),Offset(-w-5*density,12*density))
                    val box=candidates.map{d->Rect(point.x+d.x-2*density,point.y+d.y-h,point.x+d.x+w+2*density,point.y+d.y+3*density)}
                        .firstOrNull{r->rect.contains(r.topLeft)&&rect.contains(r.bottomRight)&&occupied.none{it.overlaps(r)}}
                    if(box!=null){occupied+=box;canvas.drawText(county.name,box.left+2*density,box.bottom-3*density,halo);canvas.drawText(county.name,box.left+2*density,box.bottom-3*density,label)}
                }
            }
            canvas.restoreToCount(saved)
            if(view.county!=null) {
                edge.color=if(view.county==selected)colours.selected else colours.edge;edge.strokeWidth=density
                canvas.drawRoundRect(RectF(rect.left-2*density,rect.top-3*density,rect.right+2*density,rect.bottom+3*density),6*density,6*density,edge)
                label.typeface=android.graphics.Typeface.DEFAULT_BOLD
                val name=when(view.county){"連江縣"->"馬祖";"金門縣"->"金門";"澎湖縣"->"澎湖";else->view.county}
                canvas.drawText(name,rect.left,rect.top-7*density,label)
            }
        }
    }
}

@Composable internal fun CwaWarningMapCard(state:CwaWarningMapState,selectedTitle:String?,atlasOverride:WarningCountyAtlas?=null) {
    val palette=MaterialTheme.colorScheme;val density=LocalDensity.current
    val context=LocalContext.current.applicationContext
    var atlas by remember(atlasOverride){mutableStateOf(atlasOverride)}
    var atlasFailed by remember{mutableStateOf(false)}
    LaunchedEffect(context,atlasOverride) {
        if(atlasOverride==null)try{atlas=WarningCountyAtlas.load(context)}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){atlasFailed=true}
    }
    var selectedCounty by remember{mutableStateOf<String?>(null)}
    var zoom by remember{mutableFloatStateOf(1f)};var pan by remember{mutableStateOf(Offset.Zero)}
    var size by remember{mutableStateOf(IntSize.Zero)}
    val dark=palette.background.luminance()<.4f
    val colours=remember(dark,palette.primary){WarningMapColours.forTheme(dark,palette.primary.toArgb())}
    val painter=remember{WarningMapPainter()}
    val time=remember{DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei"))}
    GlassSurface {
        Text("臺灣警戒範圍",modifier=Modifier.fillMaxWidth(),fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,color=palette.onSurface)
        Text(selectedTitle?.let{"顯示：$it"}?:"已發布的警特報與預告涉及縣市",color=palette.onSurfaceVariant,fontSize=14.sp)
        val status=when {
            state.expiredSelection->"選定公告已過有效時間，不標示為目前警戒。"
            state.sourceMismatch->"官方資料正在更新，已納入較新公告；請核對原文範圍。"
            state.complete->"全國 22 縣市已查詢"+(state.sourceAt?.let{" · 官方更新 ${time.format(it)}"}?:"")
            state.cached->"全國資料更新未完成，顯示快取；請核對公告時間。"
            else->"全國資料尚未完成；目前僅標示已載入的公告。"
        }
        Text(status,color=palette.onSurfaceVariant,fontSize=14.sp)
        Spacer(Modifier.height(10.dp))
        val loaded=atlas
        if(loaded!=null) {
            val activeNames=state.titles.keys.joinToString("、")
            Canvas(Modifier.fillMaxWidth().aspectRatio(.94f).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .background(Color(colours.background)).onSizeChanged{size=it}
                .semantics{contentDescription="臺灣警戒範圍地圖；"+if(activeNames.isBlank())"請查看查詢狀態" else "涉及 $activeNames"}
                .pointerInput(loaded,size,density.density,zoom,pan) {
                    detectTapGestures {point->selectedCounty=WarningMapLayout.hit(loaded,WarningMapLayout.viewports(size.width.toFloat(),size.height.toFloat(),density.density,zoom,pan),point)}
                }
                .pointerInput(loaded,size,density.density) {
                    detectTransformGestures {centroid,move,scale,_->
                        val main=WarningMapLayout.viewports(size.width.toFloat(),size.height.toFloat(),density.density).first().rectangle
                        if(main.contains(centroid)&&scale.isFinite()&&scale>0&&move.x.isFinite()&&move.y.isFinite()) {
                            val next=(zoom*scale).coerceIn(1f,6f);val ratio=next/zoom
                            val anchor=centroid-main.center
                            val nextPan=anchor-(anchor-pan)*ratio+move
                            zoom=next;pan=Offset(nextPan.x.coerceIn(-main.width*(zoom-1)/2,main.width*(zoom-1)/2),nextPan.y.coerceIn(-main.height*(zoom-1)/2,main.height*(zoom-1)/2))
                        }
                    }
                }) {
                painter.draw(drawContext.canvas.nativeCanvas,this.size.width,this.size.height,this.density,density.fontScale,loaded,state,selectedCounty,colours,zoom,pan)
            }
            Text("紅色：已生效 · 琥珀色：預告時段 · 灰色：本次未列示",color=palette.onSurface,fontSize=14.sp)
            Text("縣市標示不代表全區受影響；山區、沿海與局部地區請核對原文。離島以分圖呈現。",color=palette.onSurfaceVariant,fontSize=14.sp)
            Text("向量底圖 © 國土測繪中心 · 警示資料：中央氣象署",color=palette.onSurfaceVariant,fontSize=14.sp)
            GlassTextButton({zoom=1f;pan=Offset.Zero;selectedCounty=null}){Text("重設地圖視角")}
        } else Text(if(atlasFailed)"向量地圖暫時無法繪製，下方警示地區與公告仍可閱讀。" else "正在準備臺灣向量地圖…",color=palette.onSurfaceVariant,fontSize=15.sp)
        val warned=CwaWarningGeography.counties.filter{it in state.titles}
        if(warned.isNotEmpty()) {
            Text("點選縣市查看警示",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
            GlassChoiceGroup(warned,selectedCounty?.let(warned::indexOf)?:-1,{selectedCounty=warned[it]})
        } else if(state.complete&&!state.expiredSelection)Text("本次全國縣市資料未列已生效或預告的警特報。",color=palette.onSurface,fontSize=15.sp)
        selectedCounty?.let{county->
            Text(county,modifier=Modifier.fillMaxWidth(),color=palette.onSurface,fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold)
            Text(state.titles[county]?.joinToString("、")?:if(state.complete)"本次資料未列有效警示。" else "此縣市資料尚未完成，請查看官方公告。",color=palette.onSurface,fontSize=16.sp)
            state.upcoming[county]?.let{Text("預告 · ${time.format(it)} 起生效（臺灣時間）",color=palette.onSurface,fontSize=15.sp)}
        }
        if(state.unresolved.isNotEmpty())Text("另有公告地區尚未對應縣市：${state.unresolved.joinToString("、")}",color=palette.onSurfaceVariant,fontSize=14.sp)
    }
}
