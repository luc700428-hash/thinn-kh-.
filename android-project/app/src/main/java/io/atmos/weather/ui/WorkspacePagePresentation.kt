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

/**
 * True while composing the page of a [MainDetailLayer]. That layer already slides the page in and
 * out and owns the back gesture, so a page container inside it draws in place: a separate dialog
 * window would neither slide with the layer nor leave until the layer's exit finished — the page
 * sat frozen for about a second after tapping back.
 */
internal val LocalDetailPageHost=androidx.compose.runtime.staticCompositionLocalOf{false}

/** Feature pages stay inside the workspace; editors and emergency alerts stay modal. */
@Composable internal fun WorkspacePageDialog(onDismissRequest:()->Unit,
    properties:DialogProperties=DialogProperties(),adaptiveContentWidth:Dp?=960.dp,content:@Composable ()->Unit) {
    val hosted=LocalDetailPageHost.current
    if(LocalWorkspaceLayout.current.tablet||hosted) {
        if(!hosted)BackHandler{onDismissRequest()}
        // Editors opened from this page are real dialogs again.
        androidx.compose.runtime.CompositionLocalProvider(LocalDetailPageHost provides false) {
            // On a phone the page is full screen now: keep it clear of the status bar (consumed, so a
            // page that pads for it itself is not pushed down twice).
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .then(if(LocalWorkspaceLayout.current.tablet)Modifier else Modifier.statusBarsPadding())){content()}
        }
    } else ThinnDialog(onDismissRequest,properties,adaptiveContentWidth,content)
}

@Composable internal fun WorkspacePageSheet(onDismissRequest:()->Unit,
    containerColor:Color=MaterialTheme.colorScheme.surface,contentColor:Color=MaterialTheme.colorScheme.onSurface,
    content:@Composable ColumnScope.()->Unit) {
    val hosted=LocalDetailPageHost.current
    val tablet=LocalWorkspaceLayout.current.tablet
    if(tablet||hosted) {
        if(!hosted)BackHandler{onDismissRequest()}
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor,LocalDetailPageHost provides false) {
            // On a phone the page is full screen now, so it keeps clear of the status bar.
            Column(Modifier.fillMaxSize().background(containerColor).then(if(tablet)Modifier else Modifier.statusBarsPadding()),content=content)
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
