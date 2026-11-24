package io.atmos.weather.ui
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** 降雨雷達 and 衛星雲圖 share one page; a switch under the title flips between them. */
@Composable fun RadarHistoryDialog(onClose:()->Unit,latitude:Double=23.75,longitude:Double=120.95) {
    WorkspacePageDialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false),adaptiveContentWidth=null) {
        AdaptiveWindowProvider {
            var mode by rememberSaveable { mutableIntStateOf(0) }
            val switch:@Composable ()->Unit={
                IOSSegmentedControl(listOf("降雨雷達","衛星雲圖"),mode,{mode=it},Modifier.padding(horizontal=16.dp,vertical=4.dp))
            }
            if(mode==0)io.atmos.weather.radar.NativeRadarViewer(latitude,longitude,onClose,switch)
            else io.atmos.weather.satellite.SatelliteViewer(latitude,longitude,onClose,switch)
        }
    }
}
