package io.atmos.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Feature pages stay inside the workspace; editors and emergency alerts stay modal. */
@Composable internal fun WorkspacePageDialog(onDismissRequest:()->Unit,
    properties:DialogProperties=DialogProperties(),adaptiveContentWidth:Dp?=960.dp,content:@Composable ()->Unit) {
    if(LocalWorkspaceLayout.current.tablet) {
        BackHandler{onDismissRequest()}
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){content()}
    } else ThinnDialog(onDismissRequest,properties,adaptiveContentWidth,content)
}

@Composable internal fun WorkspacePageSheet(onDismissRequest:()->Unit,
    containerColor:Color=MaterialTheme.colorScheme.surface,contentColor:Color=MaterialTheme.colorScheme.onSurface,
    content:@Composable ColumnScope.()->Unit) {
    if(LocalWorkspaceLayout.current.tablet) {
        BackHandler{onDismissRequest()}
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
            Column(Modifier.fillMaxSize().background(containerColor),content=content)
        }
    } else GlassModalBottomSheet(onDismissRequest,containerColor,contentColor,content)
}

@Composable internal fun WorkspaceSearchDialog(onDismissRequest:()->Unit,
    properties:DialogProperties=DialogProperties(),content:@Composable ()->Unit) {
    if(!LocalWorkspaceLayout.current.tablet){ThinnDialog(onDismissRequest,properties,content=content);return}
    ThinnDialog(onDismissRequest,DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false),adaptiveContentWidth=null) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .background(Color.Black.copy(alpha=.32f)).clickable(onClick=onDismissRequest),contentAlignment=Alignment.Center) {
            Box(Modifier.width(minOf(560.dp,(maxWidth-32.dp).coerceAtLeast(0.dp)))
                .height(minOf(740.dp,(maxHeight-32.dp).coerceAtLeast(0.dp))).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.background)
                .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick={})) {
                AdaptiveWindowProvider{content()}
            }
        }
    }
}
