package io.atmos.weather.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.DockLiquidGeometry
import kotlin.math.abs

/** One refracting capsule lens, never a moving container or duplicate selection boxes. */
@Composable fun BoxScope.AppleLiquidDockPaint(selectedIndex:Int,tabCount:Int,dragging:Boolean,dragOffsetPx:Float,
 reduceMotion:Boolean,modifier:Modifier=Modifier,vertical:Boolean=false,darkBackdrop:Boolean?=null,
 engaged:Boolean=dragging,reduceTransparency:Boolean=false) {
 if(tabCount<1)return
 val density=LocalDensity.current.density;var widthPx by remember{mutableIntStateOf(0)}
 val inset=(if(vertical)5f else 6f)*density;val step=((widthPx-2*inset)/tabCount).coerceAtLeast(1f)
 val target=(selectedIndex.toFloat()+if(dragging)dragOffsetPx/step else 0f).coerceIn(0f,tabCount-1f)
 val location by animateFloatAsState(target,if(dragging||reduceMotion)snap() else spring(.78f,360f),label="dock-continuous-position")
 val tail by animateFloatAsState(location,if(reduceMotion)snap() else spring(.92f,145f),label="dock-viscous-tail")
 val touch by animateFloatAsState(if(engaged)1f else 0f,if(reduceMotion)snap() else spring(.8f,520f),label="dock-contact-flex")
 val light=!(darkBackdrop?:(MaterialTheme.colorScheme.background.red<.5f));val accent=MaterialTheme.colorScheme.primary
 val mask=remember(location,tail,touch,vertical,density,tabCount,reduceMotion){GenericShape{size,_->
  val logical=if(vertical)Size(size.height,size.width) else size
  val path=dockLensPath(logical.width,logical.height,density,tabCount,location,if(reduceMotion)location else tail,touch,vertical)
  if(vertical)path.transform(Matrix().apply{translate(size.width,0f);rotateZ(90f)})
  addPath(path)
 }}
 Canvas(modifier.matchParentSize().onSizeChanged{widthPx=if(vertical)it.height else it.width}.clip(mask)
  .thinnBackdrop(reduceTransparency,24.dp,(location+.5f)/tabCount,frostScale=.12f)) {
  val w=if(vertical)size.height else size.width;val h=if(vertical)size.width else size.height
  val path=dockLensPath(w,h,density,tabCount,location,if(reduceMotion)location else tail,touch,vertical)
  val center=Offset(inset+(location+.5f)*step,h*.5f)
  withTransform({if(vertical){translate(size.width,0f);rotate(90f,pivot=Offset.Zero)}}){
   drawPath(path,if(light)accent.copy(alpha=.035f+.035f*touch) else Color.White.copy(alpha=.07f+.035f*touch))
   drawPath(path,Brush.radialGradient(listOf(Color.White.copy(alpha=.14f+.09f*touch),Color.Transparent),Offset(center.x-18.dp.toPx(),center.y-16.dp.toPx()),90.dp.toPx()))
   drawPath(path,Brush.linearGradient(listOf(Color.White.copy(alpha=if(light).70f else .46f),Color.White.copy(alpha=.1f),if(light)Color.Black.copy(alpha=.08f) else Color.White.copy(alpha=.13f)),Offset(center.x-32.dp.toPx(),0f),Offset(center.x+32.dp.toPx(),h)),style=Stroke(.85.dp.toPx()))
  }
 }
}
internal fun dockLensPath(width:Float,height:Float,density:Float,count:Int,head:Float,tail:Float,touch:Float,vertical:Boolean=false):Path {
 val g=DockLiquidGeometry.shape(width,height,density,count,head,tail,vertical,touch)?:return Path()
 val a=g.width/2;val r=g.height/2;val y=g.centerY;val left=minOf(g.headX,g.tailX);val right=maxOf(g.headX,g.tailX)
 if(abs(right-left)<density*.5f)return Path().apply{addRoundRect(RoundRect(Rect(left-a,y-r,left+a,y+r),CornerRadius(r)))}
 val mid=(left+right)/2;val neck=g.neckHalfHeight.coerceAtLeast(r*.64f);val l=left-a;val z=right+a;val shoulder=(a-r).coerceAtLeast(0f)
 return Path().apply{
  moveTo(l+r,y-r);cubicTo(left+shoulder,y-r,mid-(right-left)*.14f,y-neck,mid,y-neck)
  cubicTo(mid+(right-left)*.14f,y-neck,right-shoulder,y-r,z-r,y-r)
  cubicTo(z-r*.45f,y-r,z,y-r*.55f,z,y);cubicTo(z,y+r*.55f,z-r*.45f,y+r,z-r,y+r)
  cubicTo(right-shoulder,y+r,mid+(right-left)*.14f,y+neck,mid,y+neck)
  cubicTo(mid-(right-left)*.14f,y+neck,left+shoulder,y+r,l+r,y+r)
  cubicTo(l+r*.45f,y+r,l,y+r*.55f,l,y);cubicTo(l,y-r*.55f,l+r*.45f,y-r,l+r,y-r);close()
 }
}
