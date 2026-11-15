package io.atmos.weather.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import io.atmos.weather.next.DockLiquidGeometry
import kotlin.math.abs
import kotlin.math.roundToInt

/** The sampled optical bounds follow the drop, not the width of the entire Dock. */
@Composable fun BoxScope.AppleLiquidDockPaint(selectedIndex:Int,tabCount:Int,dragging:Boolean,dragOffsetPx:Float,
 reduceMotion:Boolean,modifier:Modifier=Modifier,vertical:Boolean=false,darkBackdrop:Boolean?=null,
 engaged:Boolean=dragging,reduceTransparency:Boolean=false,dragOffset:(()->Float)?=null) {
 if(tabCount<1)return
 val density=LocalDensity.current.density
 var pixels by remember{mutableStateOf(IntSize.Zero)}
 val length=if(vertical)pixels.height.toFloat() else pixels.width.toFloat()
 val cross=if(vertical)pixels.width.toFloat() else pixels.height.toFloat()
 val inset=(if(vertical)5f else 6f)*density
 val step=((length-2*inset)/tabCount).coerceAtLeast(1f)
 val target=(selectedIndex.toFloat()+if(dragging)(dragOffset?.invoke()?:dragOffsetPx)/step else 0f).coerceIn(0f,tabCount-1f)
 val location by animateFloatAsState(target,if(dragging||reduceMotion)snap() else spring(FluidGlassStyle.POSITION_DAMPING,FluidGlassStyle.POSITION_STIFFNESS),label="dock-position")
 val tail by animateFloatAsState(location,if(reduceMotion)snap() else spring(FluidGlassStyle.TAIL_DAMPING,FluidGlassStyle.TAIL_STIFFNESS),label="dock-water-trail")
 val touch by animateFloatAsState(if(engaged)1f else 0f,if(reduceMotion)snap() else spring(FluidGlassStyle.CONTACT_DAMPING,FluidGlassStyle.CONTACT_STIFFNESS),label="dock-elastic-contact")
 val dark=darkBackdrop?:(androidx.compose.material3.MaterialTheme.colorScheme.background.red<.5f)
 val p=if(dark)BrandPalette.dark else BrandPalette.light
 val colors=FluidGlassStyle.navigation(p,dark)
 Box(modifier.matchParentSize().onSizeChanged{pixels=it}) {
  val g=DockLiquidGeometry.shape(length,cross,density,tabCount,location,if(reduceMotion)location else tail,vertical,touch)
  if(g!=null){
   val path=dockLensPath(length,cross,density,tabCount,location,if(reduceMotion)location else tail,touch,vertical)
   if(vertical)path.transform(Matrix().apply{translate(cross,0f);rotateZ(90f)})
   val b=path.getBounds()
   if(b.width>0&&b.height>0){
    val local=remember(path,b){Path().apply{addPath(path,Offset(-b.left,-b.top))}}
    val mask=remember(local){GenericShape{_,_->addPath(local)}}
    Canvas(Modifier.offset{IntOffset(b.left.roundToInt(),b.top.roundToInt())}
     .requiredSize((b.width/density).dp,(b.height/density).dp).testTag("dock-selection-lens").clip(mask)
     .thinnBackdrop(reduceTransparency,(g.height/density/2).dp,.5f,frostScale=.78f,materialDark=dark)) {
     drawPath(local,if(reduceTransparency)p.primaryContainer else colors.selection)
     val energy=touch.coerceIn(0f,1f)
     val center=Offset(size.width*.38f,size.height*.20f)
     drawPath(local,Brush.radialGradient(listOf(Color.White.copy(alpha=if(dark).06f+energy*.10f else .16f+energy*.14f),Color.Transparent),center,size.maxDimension))
     drawPath(local,Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark).50f else .72f),colors.rim,Color.White.copy(alpha=.10f)),Offset.Zero,Offset(size.width,size.height)),style=Stroke(.8f*density))
     if(energy>.01f){
      val ripple=(abs(g.headX-g.tailX)/step).coerceIn(0f,.5f)
      drawPath(local,colors.rim.copy(alpha=.08f+energy*.10f+ripple*.10f),style=Stroke((2f+energy+ripple*3)*density))
     }
    }
   }
  }
 }
}
internal fun dockLensPath(width:Float,height:Float,density:Float,count:Int,head:Float,tail:Float,touch:Float,vertical:Boolean=false):Path {
 val g=DockLiquidGeometry.shape(width,height,density,count,head,tail,vertical,touch)?:return Path()
 return liquidCapsulePath(g.headX,g.tailX,g.width,g.height,g.centerY,g.neckHalfHeight)
}
