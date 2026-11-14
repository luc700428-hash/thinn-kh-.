package io.atmos.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * App-wide touch feedback in place of Android's ripple: the iOS highlight. The pressed area
 * greys over the instant a finger lands and fades back out after release, the way table rows,
 * list cells and plain buttons respond on iOS. Controls with their own glass motion pass
 * indication=null and are unaffected.
 */
internal object IOSPressIndication:IndicationNodeFactory {
    /** Mid grey reads as a highlight on both light and dark surfaces. */
    val HIGHLIGHT=Color(0xFF8E8E93)
    const val PRESSED_ALPHA=.20f
    const val RELEASE_MS=280
    override fun create(interactionSource:InteractionSource):DelegatableNode=HighlightNode(interactionSource)
    override fun equals(other:Any?)=other===this
    override fun hashCode()=1
}

private class HighlightNode(private val source:InteractionSource):Modifier.Node(),DrawModifierNode {
    private val level=Animatable(0f)
    override fun onAttach() {
        coroutineScope.launch {
            var pressed=0
            source.interactions.collect {interaction->
                when(interaction) {
                    is PressInteraction.Press->pressed++
                    is PressInteraction.Release,is PressInteraction.Cancel->pressed=(pressed-1).coerceAtLeast(0)
                    else->return@collect
                }
                launch {
                    if(pressed>0)level.snapTo(1f) else level.animateTo(0f,tween(IOSPressIndication.RELEASE_MS))
                }
            }
        }
        coroutineScope.launch {
            androidx.compose.runtime.snapshotFlow{level.value}.collect{invalidateDraw()}
        }
    }
    override fun ContentDrawScope.draw() {
        drawContent()
        val a=level.value
        if(a>0f)drawRect(IOSPressIndication.HIGHLIGHT.copy(alpha=IOSPressIndication.PRESSED_ALPHA*a))
    }
}
