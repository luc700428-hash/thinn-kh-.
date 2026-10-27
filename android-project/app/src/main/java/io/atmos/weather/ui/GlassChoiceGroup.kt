package io.atmos.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine

/** Selection is communicated by a check, weight and contrast, never tint alone. */
@Composable
fun GlassChoiceGroup(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    require(labels.isNotEmpty())
    val scheme=MaterialTheme.colorScheme
    val fontScale=LocalDensity.current.fontScale
    val reduce=rememberAppleReduceMotion()
    val feedback=LocalHapticEngine.current
    val shape=RoundedCornerShape(22.dp)
    BoxWithConstraints(modifier.fillMaxWidth().selectableGroup()) {
        // Large text and narrow windows get new rows instead of clipped labels.
        val widest=labels.maxOf{it.length}.coerceAtLeast(2)*14f*fontScale+52f
        val columns=((maxWidth.value+6f)/(widest+6f)).toInt().coerceIn(1,labels.size)
        Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
            labels.indices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach { index ->
                        val chosen=index==selected
                        val fill by animateColorAsState(if(chosen) Color(0xFF0866CC) else scheme.surfaceVariant,
                            tween(if(reduce)0 else 150),label="choice-fill")
                        val ink=if(chosen) Color.White else scheme.onSurface
                        Row(Modifier.weight(1f).heightIn(min=48.dp).iosJellyPress()
                            .clip(shape).background(fill)
                            .border(if(chosen)1.5.dp else 1.dp,if(chosen)Color(0xFF79CBFF) else scheme.outline.copy(alpha=.35f),shape)
                            .thinnSpecularEdge(22.dp)
                            .semantics {stateDescription=if(chosen)"已選取" else "未選取"}
                            .selectable(selected=chosen,role=Role.RadioButton,onClick={
                                if(!chosen){feedback?.play(HapticEngine.Event.TAP);onSelect(index)}
                            }).padding(horizontal=10.dp,vertical=10.dp),
                            horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                            if(chosen) {
                                Icon(Icons.Rounded.Check,null,Modifier.size(16.dp),tint=ink)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(labels[index],color=ink,fontSize=14.sp,
                                fontWeight=if(chosen)FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                    repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                }
            }
        }
    }
}
