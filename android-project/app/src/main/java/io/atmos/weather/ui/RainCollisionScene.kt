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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot

private class RainLayout(val world:RainCollisionWorld) {
    var origin=Offset.Zero;var density=1f
    val cards=HashMap<Int,Pair<LayoutCoordinates,Float>>()
    fun refresh(id:Int,coords:LayoutCoordinates,radius:Float) {
        cards[id]=coords to radius
        if(!coords.isAttached)return
        val at=(coords.positionInWindow()-origin)/density
        world.surface(id,at.x,at.y,at.x+coords.size.width/density,at.y+coords.size.height/density,radius)
    }
}
private val LocalRainLayout=staticCompositionLocalOf<RainLayout?> {null}
private val surfaceIds=AtomicInteger(1)

/** Registers the actual laid-out card, including scroll/translation, with the rain world. */
@Composable fun Modifier.weatherCollisionSurface(radius:Dp=24.dp):Modifier {
    val layout=LocalRainLayout.current ?: return this
    val id=remember {surfaceIds.getAndIncrement()}
    val radiusDp=radius.value
    DisposableEffect(layout,id) {onDispose {layout.cards.remove(id);layout.world.remove(id)}}
    return onGloballyPositioned {layout.refresh(id,it,radiusDp)}
}

/** One foreground Canvas for all visible cards. Its particles never intercept input.
 * Simulation/rendering follows display-vsync; fixed 240 Hz physics makes speed FPS-independent.
 */
@Composable fun RainCollisionScene(intensity:Float,windKmh:Float,modifier:Modifier=Modifier,
    content:@Composable BoxScope.()->Unit) {
    val layout=remember {RainLayout(RainCollisionWorld())}
    val world=layout.world
    val density=LocalDensity.current.density
    val reduce=rememberAppleReduceMotion()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val frame=remember {mutableLongStateOf(0L)}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->started=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(intensity,windKmh,started,reduce) {
        if(!started || reduce) {world.clearAir();frame.longValue++;return@LaunchedEffect}
        var last=withFrameNanos {it}
        while(intensity>0f || world.hasMotion)withFrameNanos {now ->
            val dt=((now-last)/1_000_000_000.0).toFloat();last=now
            world.step(dt,intensity,windKmh*.6f)
            frame.longValue++
        }
    }
    CompositionLocalProvider(LocalRainLayout provides layout) {
        Box(modifier.onGloballyPositioned {coords ->
            layout.origin=coords.positionInWindow();layout.density=density
            world.width=coords.size.width/density;world.height=coords.size.height/density
            layout.cards.toMap().forEach {(id,pair)->layout.refresh(id,pair.first,pair.second)}
        }) {
            content()
            Canvas(Modifier.matchParentSize().zIndex(10f)) {
                frame.longValue // Draw-phase dependency only; card text does not recompose every frame.
                world.air.forEach {drop ->
                    val p=Offset(drop.x*density,drop.y*density)
                    if(drop.drip) {
                        val r=drop.radius*density
                        drawOval(Color(0x8FCDEEFF),Offset(p.x-r,p.y-r),Size(r*2,r*2.5f))
                        drawCircle(Color(0xDBFFFFFF),r*.23f,Offset(p.x-r*.3f,p.y-r*.3f))
                    } else {
                        val speed=hypot(drop.vx,drop.vy).coerceAtLeast(1f)
                        val length=(11f+drop.vy*.018f)*density
                        val tail=Offset(drop.vx/speed*length,drop.vy/speed*length)
                        drawLine(Color(0x70D8F1FF),p-tail,p,(drop.radius*.7f).coerceAtLeast(.55f)*density,StrokeCap.Round)
                    }
                }
                world.sparks.forEach {spark ->
                    val p=Offset(spark.x*density,spark.y*density)
                    drawLine(Color.White.copy(alpha=(spark.life/.6f).coerceIn(0f,.8f)),
                        p-Offset(spark.vx*.012f,spark.vy*.012f)*density,p,.9f*density,StrokeCap.Round)
                }
                world.surfaces.values.forEach {surface ->
                    if(surface.wet>.005f) {
                        drawLine(Color(0xFFB4E6FA).copy(alpha=surface.wet*.45f),
                            Offset((surface.left+surface.r)*density,surface.top*density),
                            Offset((surface.right-surface.r)*density,surface.top*density),1.1f*density,StrokeCap.Round)
                    }
                    surface.beads.forEach {bead ->
                        val point=world.beadPoint(surface,bead)
                        val r=world.radius(bead)*density
                        val stretch=(bead.neckAge/.25f).coerceIn(0f,1f)
                        val center=Offset(point.x*density,point.y*density+stretch*r*1.4f)
                        if(stretch>0f) {
                            val neck=Path().apply {
                                moveTo(center.x-r*.65f,point.y*density)
                                cubicTo(center.x-r*.15f,center.y-r,center.x-r,center.y,center.x,center.y+r)
                                cubicTo(center.x+r,center.y,center.x+r*.15f,center.y-r,center.x+r*.65f,point.y*density);close()
                            }
                            drawPath(neck,Color(0x8BC7EBFF))
                        }
                        drawOval(Color(0x7F8FC9E9),center-Offset(r,r*.8f),Size(r*2,r*(1.6f+stretch*.5f)))
                        drawOval(Color.White.copy(alpha=.53f),center-Offset(r,r*.8f),Size(r*2,r*(1.6f+stretch*.5f)),style=Stroke(.5f*density))
                        drawCircle(Color.White.copy(alpha=.88f),r*.23f,center-Offset(r*.28f,r*.32f))
                    }
                }
            }
        }
    }
}
