package io.atmos.weather.earthquake

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.atmos.weather.ui.appleBouncingScroll
import io.atmos.weather.ui.LocalWorkspaceLayout

internal val LocalSeismicSettingsCellWidth=staticCompositionLocalOf{Dp.Unspecified}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SeismicSettingsFlow(scroll:ScrollState,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    val scale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal=20.dp)) {
        val columns=if(maxWidth.value>=760f*scale.coerceIn(1f,1.3f))2 else 1
        val width=(maxWidth-20.dp*(columns-1))/columns
        CompositionLocalProvider(LocalSeismicSettingsCellWidth provides width) {
            FlowRow(Modifier.fillMaxSize().appleBouncingScroll().verticalScroll(scroll),
                horizontalArrangement=Arrangement.spacedBy(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp),
                maxItemsInEachRow=columns){content()}
        }
    }
}

/** Movable slots preserve the map, selected station and replay when panes move. */
@Composable internal fun SeismicAdaptivePanels(
    modifier:Modifier=Modifier,criticalFirst:Boolean=false,overview:@Composable ()->Unit={},
    leading:@Composable ColumnScope.(Dp)->Unit,trailing:@Composable ColumnScope.(Dp)->Unit
) {
    val scale=LocalDensity.current.fontScale
    val currentOverview=rememberUpdatedState(overview)
    val currentLeading=rememberUpdatedState(leading);val currentTrailing=rememberUpdatedState(trailing)
    val summary=remember{movableContentOf{currentOverview.value()}}
    val first=remember{movableContentOf<Dp>{height->Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)){currentLeading.value(this,height)}}}
    val second=remember{movableContentOf<Dp>{height->Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)){currentTrailing.value(this,height)}}}
    val compactScroll=rememberScrollState();val firstScroll=rememberScrollState();val secondScroll=rememberScrollState()
    val bottom=if(criticalFirst||LocalWorkspaceLayout.current.tablet)16.dp else 114.dp
    BoxWithConstraints(modifier.fillMaxSize()) {
        val plan=SeismicPaneGeometry.plan(maxWidth.value,maxHeight.value,scale,criticalFirst)
        if(plan.sideBySide) {
            Row(Modifier.fillMaxSize().padding(horizontal=16.dp).padding(bottom=bottom),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                val firstModifier=if(criticalFirst)Modifier.width(plan.inspectorWidth.dp) else Modifier.weight(1f)
                Column(firstModifier.fillMaxHeight().verticalScroll(firstScroll).testTag("seismic-leading-pane")){first(plan.mapHeight.dp)}
                val secondModifier=if(criticalFirst)Modifier.weight(1f) else Modifier.width(plan.inspectorWidth.dp)
                Column(secondModifier.fillMaxHeight().verticalScroll(secondScroll).testTag("seismic-inspector-pane"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    summary();second(plan.mapHeight.dp)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(compactScroll).padding(horizontal=16.dp).padding(bottom=bottom)
                .testTag("seismic-stacked-pane"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                summary();first(plan.mapHeight.dp);second(plan.mapHeight.dp)
            }
        }
    }
}
