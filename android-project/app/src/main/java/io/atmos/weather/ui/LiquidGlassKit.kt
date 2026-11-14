package io.atmos.weather.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * iOS 27 clear Liquid Glass, shared by the Dock lens, the slider and switch knobs and the
 * glass buttons, so every control lifts, bends light and settles the same way.
 *
 * The look is built from four layers, in this order:
 *  1. what sits behind the glass, magnified (the lens);
 *  2. a darker refraction band just inside the edge, where light bends away from the eye;
 *  3. a crisp specular line on the edge facing the light (sun or moon, see [GlassLight]) and a
 *     weaker exit highlight on the opposite edge;
 *  4. while a finger is down, a soft bloom of light that spreads from the touch point.
 */
internal object LiquidGlassKit {
    /** The tab-bar lens grows this much taller than the bar's inner height, so it pokes out. */
    const val DOCK_LIFT=.34f
    /** Content under a lifted lens is magnified by this factor. */
    const val MAGNIFY=1.22f
    /** iOS "interactive" spring: follows the finger almost one to one. */
    const val FOLLOW_DAMPING=.86f
    const val FOLLOW_STIFFNESS=1754.6f
    /** How far the lens stretches along its motion for each slot (or knob width) of lag. */
    const val STRETCH_PER_LAG=.55f
    const val MAX_STRETCH=.34f

    fun lerp(a:Float,b:Float,t:Float)=a+(b-a)*t

    /** Squash and stretch from the distance between the lens and its lagging tail. */
    fun stretch(lag:Float):Float=if(lag.isFinite())(abs(lag)*STRETCH_PER_LAG).coerceAtMost(MAX_STRETCH) else 0f

    fun capsule(rect:Rect):Path=Path().apply {
        val r=min(rect.width,rect.height)/2f
        addRoundRect(RoundRect(rect,CornerRadius(r,r)))
    }
}

/**
 * Draws layers 2 and 3 onto [path], which fills this draw scope. The caller has already drawn
 * the lens content. [energy] is 0 at rest and 1 while held; springs may overshoot it.
 */
internal fun DrawScope.liquidGlassEdge(path:Path,light:GlassLight.Light,energy:Float,dark:Boolean,
    touch:Offset?=null,bounds:Rect=Rect(Offset.Zero,size)) {
    val e=if(energy.isFinite())energy.coerceIn(0f,1.25f) else 0f
    val c=bounds.center
    val reach=max(bounds.width,bounds.height)*.5f
    val toward=Offset(c.x+light.x*reach,c.y+light.y*reach)
    val away=Offset(c.x-light.x*reach,c.y-light.y*reach)
    clipPath(path) {
        // Refraction band: the rim of real glass looks darker on bright scenes.
        drawPath(path,Color.Black.copy(alpha=if(dark).16f else .07f),style=Stroke(min(bounds.minDimension*.22f,7f*density)))
        // Light gathering on the inside of the lit edge.
        drawPath(path,Brush.radialGradient(listOf(Color.White.copy(alpha=(if(dark).07f else .11f)+.06f*e),Color.Transparent),
            Offset(c.x+light.x*reach*.72f,c.y+light.y*reach*.72f),reach*1.1f))
        if(touch!=null&&e>.01f) {
            drawPath(path,Brush.radialGradient(listOf(Color.White.copy(alpha=.16f*min(e,1f)),Color.White.copy(alpha=.05f*min(e,1f)),Color.Transparent),
                touch,max(bounds.width,bounds.height)*.75f))
        }
    }
    // Soft bloom along the rim, then the crisp specular line.
    drawPath(path,Brush.linearGradient(0f to Color.White.copy(alpha=.30f+.18f*e),.38f to Color.White.copy(alpha=.04f),
        .62f to Color.White.copy(alpha=.03f),1f to Color.White.copy(alpha=.16f+.10f*e),start=toward,end=away),style=Stroke(2.6f*density))
    drawPath(path,Brush.linearGradient(0f to Color.White.copy(alpha=.96f),.30f to Color.White.copy(alpha=.24f),
        .50f to Color.White.copy(alpha=.07f),.70f to Color.White.copy(alpha=.18f),1f to Color.White.copy(alpha=.66f),
        start=toward,end=away),style=Stroke(1.1f*density))
}

/**
 * Where the finger is on a glass button, observed without consuming anything, so the light can
 * bloom from that point and the glass can lean toward it the way iOS interactive glass does.
 */
@androidx.compose.runtime.Stable
internal class GlassTouch {
    var point by androidx.compose.runtime.mutableStateOf(Offset.Unspecified)
    var size by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Size.Zero)
    /** Rubber-banded lean toward the finger, in px; grows slowly once the finger leaves the glass. */
    fun lean(limit:Float):Offset {
        if(!point.isSpecified||size.width<=0f||size.height<=0f)return Offset.Zero
        val c=Offset(size.width/2,size.height/2)
        fun axis(d:Float,half:Float):Float {
            val inside=d.coerceIn(-half,half)
            val beyond=d-inside
            return (inside*.045f+beyond*.22f).coerceIn(-limit,limit)
        }
        return Offset(axis(point.x-c.x,size.width/2),axis(point.y-c.y,size.height/2))
    }
}

internal fun Modifier.glassTouchTracker(touch:GlassTouch):Modifier=this
    .onSizeChanged{touch.size=androidx.compose.ui.geometry.Size(it.width.toFloat(),it.height.toFloat())}
    .pointerInput(touch) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            touch.point=down.position
            try {
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull{it.id==down.id}?.let{if(it.pressed)touch.point=it.position}
                } while(event.changes.any{it.pressed})
            } finally {touch.point=Offset.Unspecified}
        }
    }

/** Specular edge, refraction band and touch bloom for a glass control of the given corner. */
internal fun Modifier.liquidGlassSurface(corner:Dp,energy:()->Float,touch:GlassTouch?,dark:Boolean,
    light:GlassLight.Light):Modifier=drawWithCache {
    val r=corner.toPx().coerceAtMost(size.minDimension/2)
    val path=Path().apply{addRoundRect(RoundRect(Rect(Offset.Zero,size),CornerRadius(r,r)))}
    var last=Offset(size.width/2,size.height/2)
    onDrawWithContent {
        drawContent()
        val p=touch?.point
        if(p!=null&&p.isSpecified)last=p
        liquidGlassEdge(path,light,energy(),dark,last)
    }
}
