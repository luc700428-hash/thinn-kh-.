package io.atmos.weather.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

/** Optical controls supply their own press response; never add a Material ripple. */
internal object IOSPressIndication:IndicationNodeFactory {
    override fun create(interactionSource:InteractionSource):DelegatableNode=OpticalContentNode()
    override fun equals(other:Any?)=other===this
    override fun hashCode()=1
}

private class OpticalContentNode:Modifier.Node(),DrawModifierNode {
    override fun ContentDrawScope.draw(){drawContent()}
}
