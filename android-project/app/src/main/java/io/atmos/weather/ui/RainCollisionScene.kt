package io.atmos.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot

internal class RainLayout(val world:RainCollisionWorld) {
    var origin=Offset.Zero;var density=1f;var collisionBottom=Float.POSITIVE_INFINITY
    val frame=mutableLongStateOf(0L)
    val particles=WeatherSurfacePhysics()
    val glyphs=HashMap<Int,Pair<LayoutCoordinates,CollisionContour>>()
    fun refreshGlyph(id:Int,coords:LayoutCoordinates,shape:CollisionContour) {
        if(glyphs[id]?.first!==coords||glyphs[id]?.second!==shape)glyphs[id]=coords to shape
        if(!coords.isAttached)return
        val at=(coords.positionInWindow()-origin)/density
        world.surface(id,at.x+shape.offsetX,at.y+shape.offsetY,at.x+shape.offsetX+shape.width,at.y+shape.offsetY+shape.height,0f)
        world.surfaces[id]?.let{it.contour=shape;it.active=!coords.boundsInWindow().isEmpty;it.clipBottom=collisionBottom}
    }
    fun refreshAll(){cards.forEach{(id,p)->refresh(id,p.first,p.second)};glyphs.forEach{(id,p)->refreshGlyph(id,p.first,p.second)}}
    val cards=HashMap<Int,Pair<LayoutCoordinates,Float>>()
    fun refresh(id:Int,coords:LayoutCoordinates,radius:Float) {
        if(cards[id]?.first!==coords||cards[id]?.second!=radius)cards[id]=coords to radius
        if(!coords.isAttached)return
        val at=(coords.positionInWindow()-origin)/density
        world.surface(id,at.x,at.y,at.x+coords.size.width/density,at.y+coords.size.height/density,radius)
        world.surfaces[id]?.let{it.active=!coords.boundsInWindow().isEmpty;it.clipBottom=collisionBottom}
    }
}
internal val LocalRainLayout=staticCompositionLocalOf<RainLayout?> {null}
private val surfaceIds=AtomicInteger(1)

/** Registers the actual laid-out card, including scroll/translation, with the rain world. */
@Composable fun Modifier.weatherCollisionSurface(radius:Dp=24.dp):Modifier {
    val layout=LocalRainLayout.current ?: return this
    val id=remember {surfaceIds.getAndIncrement()}
    val radiusDp=radius.value
    DisposableEffect(layout,id) {onDispose {layout.cards.remove(id);layout.world.remove(id);layout.particles.remove(id)}}
    return onGloballyPositioned {layout.refresh(id,it,radiusDp)}
}

/** One foreground Canvas for all visible cards. Its particles never intercept input.
 * Simulation/rendering follows display-vsync; fixed 240 Hz physics makes speed FPS-independent.
 */
@Composable internal fun RainCollisionScene(intensity:Float,windKmh:Float,modifier:Modifier=Modifier,
    style:WeatherPhysicsStyle=WeatherPhysicsStyle(rain=intensity),strongWind:Boolean=false,lightningEvent:Int=0,
    lightBackdrop:Boolean=false,active:Boolean=true,content:@Composable BoxScope.()->Unit) {
    val layout=remember {RainLayout(RainCollisionWorld())}
    val world=layout.world
    val particles=layout.particles
    world.speedScale=style.speed;world.radiusScale=style.radius;world.gusts=strongWind
    val density=LocalDensity.current.density
    val reduce=rememberAppleReduceMotion()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val frame=layout.frame
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->started=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(lightningEvent,style.thunder,reduce,active){
        if(active&&lightningEvent>0&&style.thunder&&!reduce)particles.strike(world.surfaces.values,world.width,world.height,lightningEvent)
    }
    LaunchedEffect(style,windKmh,strongWind,started,reduce,active) {
        if(!active||!started || reduce) {world.clearAir();particles.clearAir();frame.longValue++;return@LaunchedEffect}
        var last=withFrameNanos {it}
        while(style.active||strongWind||world.hasMotion||particles.hasMotion)withFrameNanos {now ->
            val dt=((now-last)/1_000_000_000.0).toFloat();last=now
            layout.refreshAll()
            world.step(dt,style.rain,windKmh*3.2f)
            particles.step(dt,style,windKmh,strongWind,world.width,world.height,world.surfaces.values)
            frame.longValue++
        }
    }
    CompositionLocalProvider(LocalRainLayout provides layout) {
        Box(modifier.onGloballyPositioned {coords ->
            layout.origin=coords.positionInWindow();layout.density=density
            world.width=coords.size.width/density;world.height=coords.size.height/density
            layout.refreshAll()
        }) {
            content()
            Canvas(Modifier.matchParentSize().zIndex(10f)) {
                frame.longValue // Draw-phase dependency only; card text does not recompose every frame.
                // Water reflects neutral sky light rather than being dyed by the interface theme.
                val water=if(lightBackdrop)Color(0xFF587782) else Color(0xFFD6EAF4)
                world.air.forEach {drop ->
                    val p=Offset(drop.x*density,drop.y*density)
                    if(drop.drip) {
                        val r=drop.radius*density
                        drawOval(water.copy(alpha=.14f),Offset(p.x-r,p.y-r),Size(r*2,r*2.5f))
                        drawOval(water.copy(alpha=.27f),Offset(p.x-r,p.y-r),Size(r*2,r*2.5f),style=Stroke(.4f*density))
                        drawCircle(Color(0xDBFFFFFF),r*.23f,Offset(p.x-r*.3f,p.y-r*.3f))
                    } else {
                        val speed=hypot(drop.vx,drop.vy).coerceAtLeast(1f)
                        val length=(style.trail+drop.vy*.006f)*density
                        val tail=Offset(drop.vx/speed*length,drop.vy/speed*length)
                        drawLine((if(lightBackdrop)Color(0xFF68858C) else Color(0xFFD4EAF1)).copy(alpha=if(style.rain<.2f).30f else .48f),p-tail,p,(drop.radius*.7f).coerceAtLeast(.55f)*density,StrokeCap.Round)
                    }
                }
                world.sparks.forEach {spark ->
                    val p=Offset(spark.x*density,spark.y*density)
                    drawLine((if(lightBackdrop)water else Color.White).copy(alpha=(spark.life/.6f).coerceIn(0f,.65f)),
                        p-Offset(spark.vx*.012f,spark.vy*.012f)*density,p,.9f*density,StrokeCap.Round)
                }
                drawWeatherSurfaceEffects(particles,world,density,windKmh,lightBackdrop)
                world.surfaces.values.filter{it.active}.forEach {surface ->
                    if(surface.wet>.005f&&surface.contour==null) {
                        drawLine(water.copy(alpha=surface.wet*.23f),
                            Offset((surface.left+surface.r)*density,surface.top*density),
                            Offset((surface.right-surface.r)*density,surface.top*density),1.1f*density,StrokeCap.Round)
                    }
                    surface.beads.forEach {bead ->
                        val point=world.beadPoint(surface,bead)
                        if(point.y>surface.clipBottom)return@forEach
                        val r=world.radius(bead)*density
                        val stretch=(bead.neckAge/.25f).coerceIn(0f,1f)
                        val center=Offset(point.x*density,point.y*density+stretch*r*1.4f)
                        if(stretch>0f) {
                            val neck=Path().apply {
                                moveTo(center.x-r*.65f,point.y*density)
                                cubicTo(center.x-r*.15f,center.y-r,center.x-r,center.y,center.x,center.y+r)
                                cubicTo(center.x+r,center.y,center.x+r*.15f,center.y-r,center.x+r*.65f,point.y*density);close()
                            }
                            drawPath(neck,water.copy(alpha=.13f))
                        }
                        drawOval(water.copy(alpha=.12f),center-Offset(r,r*.8f),Size(r*2,r*(1.6f+stretch*.5f)))
                        drawOval(water.copy(alpha=.25f),center-Offset(r,r*.8f),Size(r*2,r*(1.6f+stretch*.5f)),style=Stroke(.45f*density))
                        drawArc(Color.White.copy(alpha=.58f),205f,82f,false,center-Offset(r*.77f,r*.62f),Size(r*1.54f,r*(1.25f+stretch*.4f)),style=Stroke(.55f*density,cap=StrokeCap.Round))
                        drawCircle(Color.White.copy(alpha=.88f),r*.23f,center-Offset(r*.28f,r*.32f))
                    }
                }
            }
        }
    }
}

/** Glyph registrations stay in the same viewport as ordinary weather cards. */
@Composable internal fun Modifier.weatherGlyphCollisionSurfaces(contours:List<CollisionContour>):Modifier {
    val layout=LocalRainLayout.current?:return this
    val ids=remember(contours){contours.map{surfaceIds.getAndIncrement()}}
    DisposableEffect(layout,ids){onDispose{ids.forEach{layout.glyphs.remove(it);layout.world.remove(it);layout.particles.remove(it)}}}
    return onGloballyPositioned{coords->contours.forEachIndexed{i,shape->layout.refreshGlyph(ids[i],coords,shape)}}
}

/** Constrain contact to the visible forecast; the sky remains full window behind Dock. */
@Composable internal fun Modifier.weatherCollisionViewport():Modifier {
 val layout=LocalRainLayout.current?:return this
 return onGloballyPositioned {coords->layout.collisionBottom=(coords.positionInWindow().y-layout.origin.y+coords.size.height)/layout.density}
}
