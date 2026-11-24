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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
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
 engaged:Boolean=dragging,reduceTransparency:Boolean=false,dragOffset:(()->Float)?=null,position:Float?=null) {
 if(tabCount<1)return
 val density=LocalDensity.current.density
 var pixels by remember{mutableStateOf(IntSize.Zero)}
 val length=if(vertical)pixels.height.toFloat() else pixels.width.toFloat()
 val cross=if(vertical)pixels.width.toFloat() else pixels.height.toFloat()
 val inset=(if(vertical)5f else 6f)*density
 val step=((length-2*inset)/tabCount).coerceAtLeast(1f)
 val target=(selectedIndex.toFloat()+if(dragging)(dragOffset?.invoke()?:dragOffsetPx)/step else 0f).coerceIn(0f,tabCount-1f)
 val animated by animateFloatAsState(target,if(dragging||reduceMotion)snap() else spring(FluidGlassStyle.POSITION_DAMPING,FluidGlassStyle.POSITION_STIFFNESS),label="dock-position")
 // The Dock passes the lens position so the resting pill and the lifted lens never part ways.
 val location=position?.takeIf{it.isFinite()}?.coerceIn(0f,tabCount-1f)?:animated
 val tail by animateFloatAsState(location,if(reduceMotion)snap() else spring(FluidGlassStyle.TAIL_DAMPING,FluidGlassStyle.TAIL_STIFFNESS),label="dock-water-trail")
 val touch by animateFloatAsState(if(engaged)1f else 0f,if(reduceMotion)snap() else spring(FluidGlassStyle.CONTACT_DAMPING,FluidGlassStyle.CONTACT_STIFFNESS),label="dock-elastic-contact")
 val dark=darkBackdrop?:(androidx.compose.material3.MaterialTheme.colorScheme.background.red<.5f)
 val p=if(dark)BrandPalette.dark else BrandPalette.light
 val colors=FluidGlassStyle.navigation(p,dark)
 // Without a sampled backdrop (power-saving glass) the tinted pill must carry contrast on its own.
 val sampled=thinnBackdropAvailable(reduceTransparency)
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
     .requiredSize((b.width/density).dp,(b.height/density).dp).testTag("dock-selection-lens")
     // iOS 27: the tinted pill gives way to the clear lens while a finger is down.
     .graphicsLayer{alpha=(1f-touch).coerceIn(0f,1f)}.clip(mask)
     .thinnBackdrop(reduceTransparency,(g.height/density/2).dp,.5f,frostScale=.78f,materialDark=dark)) {
     drawPath(local,if(reduceTransparency||!sampled)p.primaryContainer else colors.selection)
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

/**
 * iOS 27 tab-bar lens. While a finger is down the selection lifts out of the bar as clear glass,
 * taller than the bar itself, magnifying the icons beneath it. It follows the finger with the
 * interactive spring, stretches along its motion and squashes across it, and on release it
 * springs back into the tinted pill on the chosen tab. Drawn outside the Dock's clip.
 */
@Composable fun BoxScope.AppleLiquidDockLens(position:Float,tail:Float,contact:Float,tabCount:Int,
 icons:androidx.compose.ui.graphics.layer.GraphicsLayer,vertical:Boolean=false,darkBackdrop:Boolean?=null,
 reduceTransparency:Boolean=false,touch:(()->Float)?=null) {
 if(tabCount<1)return
 var pixels by remember{mutableStateOf(IntSize.Zero)}
 // A plain inner Box: the Dock's outer Box propagates min constraints, which would centre the lens.
 Box(Modifier.matchParentSize().onSizeChanged{pixels=it}) {LensBody(pixels,position,tail,contact,tabCount,icons,vertical,darkBackdrop,reduceTransparency,touch)}
}

@Composable private fun LensBody(pixels:IntSize,position:Float,tail:Float,contact:Float,tabCount:Int,
 icons:androidx.compose.ui.graphics.layer.GraphicsLayer,vertical:Boolean,darkBackdrop:Boolean?,
 reduceTransparency:Boolean,touch:(()->Float)?) {
 val density=LocalDensity.current.density
 val energy=contact.coerceIn(0f,1.25f)
 if(energy<.015f||pixels.width<=0||pixels.height<=0)return
 val length=if(vertical)pixels.height.toFloat() else pixels.width.toFloat()
 val cross=if(vertical)pixels.width.toFloat() else pixels.height.toFloat()
 val g=DockLiquidGeometry.shape(length,cross,density,tabCount,position,position,vertical,0f)?:return
 val inset=(if(vertical)5f else 6f)*density
 val slot=((length-2*inset)/tabCount).coerceAtLeast(1f)
 val lift=energy.coerceIn(0f,1.15f)
 val lag=position-tail
 val stretch=LiquidGlassKit.stretch(lag)
 val major=g.width*(1f+.12f*lift)*(1f+stretch)
 val minor=(cross-10f*density)*(1f+LiquidGlassKit.DOCK_LIFT*lift)*(1f-stretch*.40f)
 // The body leans back toward where it came from, like a drop being pulled.
 val along=g.headX-lag.coerceIn(-.6f,.6f)*slot*.16f
 val rect=if(vertical)Rect(cross/2-minor/2,along-major/2,cross/2+minor/2,along+major/2)
  else Rect(along-major/2,cross/2-minor/2,along+major/2,cross/2+minor/2)
 val dark=darkBackdrop?:(androidx.compose.material3.MaterialTheme.colorScheme.background.red<.5f)
 val sampled=thinnBackdropAvailable(reduceTransparency)
 val base=androidx.compose.material3.MaterialTheme.colorScheme.surface
 val light=GlassLight.current(follow=!reduceTransparency)
 val optics=remember{liquidLensEffectOrNull()}
 // The glass is painted on ONE fixed-size surface (the Dock plus the room the lens may lift
 // into); only the clip outline changes per frame. A surface that resized every frame made
 // the blur and its offscreen buffers reallocate on every frame of the spring.
 val margin=maxOf(cross*.40f,24f*density)
 val local=LiquidGlassKit.capsule(rect.translate(margin,margin))
 val mask=GenericShape{_,_->addPath(local)}
 val fullW=(if(vertical)cross else length)+2*margin;val fullH=(if(vertical)length else cross)+2*margin
 // Geometry for accessibility and tests: the lens itself, layout only.
 Box(Modifier.offset{IntOffset(rect.left.roundToInt(),rect.top.roundToInt())}
  .wrapContentSize(androidx.compose.ui.Alignment.TopStart,unbounded=true)
  .requiredSize((rect.width/density).dp,(rect.height/density).dp).testTag("dock-liquid-lens"))
 Canvas(Modifier.offset{IntOffset(-margin.roundToInt(),-margin.roundToInt())}
  .wrapContentSize(androidx.compose.ui.Alignment.TopStart,unbounded=true)
  .requiredSize((fullW/density).dp,(fullH/density).dp)
  .graphicsLayer{alpha=(energy*1.8f).coerceIn(0f,1f)
   // iOS 27 lensing: icons and scene bend toward the centre along the rim, with dispersion.
   renderEffect=if(android.os.Build.VERSION.SDK_INT>=33)optics?.effect(size.width,size.height,rect.translate(margin,margin),minor/2,lift,density) else null}
  .clip(mask)
  .thinnBackdrop(reduceTransparency,(minor/density/2).dp,.5f,frostScale=.18f,refract=false)) {
  // Clear glass: the backdrop is sampled without the Dock's material tone, and without the
  // runtime shader (the magnified icons already give the lens; the Dock itself skips it too).
  // Without a live backdrop the icons below must still be covered before the magnified copy.
  if(!sampled)drawPath(local,base.copy(alpha=.96f))
  drawPath(local,Color.White.copy(alpha=if(dark).03f else .05f))
  val zoom=1f+(LiquidGlassKit.MAGNIFY-1f)*lift.coerceIn(0f,1f)
  withTransform({translate(margin,margin);scale(zoom,zoom,rect.center)}) {drawLayer(icons)}
  val finger=touch?.invoke()?.takeIf{it.isFinite()}?.let{t->if(vertical)Offset(margin+cross/2,margin+t) else Offset(margin+t,margin+cross/2)}
  liquidGlassEdge(local,light,energy,dark,finger,rect.translate(margin,margin))
 }
}
