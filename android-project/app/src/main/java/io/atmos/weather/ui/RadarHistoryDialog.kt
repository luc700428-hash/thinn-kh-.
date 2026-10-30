package io.atmos.weather.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
@Composable fun RadarHistoryDialog(onClose:()->Unit,latitude:Double=23.75,longitude:Double=120.95) {
    ThinnDialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false),adaptiveContentWidth=null) {
        AdaptiveWindowProvider { io.atmos.weather.radar.NativeRadarViewer(latitude,longitude,onClose) }
    }
}
