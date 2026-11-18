package io.atmos.weather.earthquake

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.push.SeismicCloudTest
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 「從雲端送一則測試警報給這支手機」 with a live five-minute countdown between requests. */
@Composable internal fun SeismicCloudTestRow() {
    val ctx = LocalContext.current
    val palette = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    // Once a second while waiting, so the countdown and the "received" status stay current.
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val left = remember(tick) { SeismicCloudTest.secondsLeft(ctx) }
    val status = remember(tick) { SeismicCloudTest.status(ctx) }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("seismic-cloud-test")) {
        Text("從雲端送一則測試警報給這支手機", color = palette.onSurface, fontSize = 16.sp, lineHeight = 22.sp)
        Text("從雲端主機送一則測試警報到這支手機，確認收得到。", color = palette.onSurfaceVariant, fontSize = 14.sp,
            lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        GlassButton(onClick = {
            if (sending || left > 0) return@GlassButton
            sending = true; message = null
            scope.launch {
                message = when (val r = SeismicCloudTest.request(ctx)) {
                    SeismicCloudTest.Result.Sent -> null
                    is SeismicCloudTest.Result.Wait -> "請再等 ${minutes(r.seconds)}"
                    is SeismicCloudTest.Result.Failed -> r.message
                }
                sending = false; tick++
            }
        }, enabled = !sending && left == 0L, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
                // The label rolls between states instead of jumping.
                AnimatedContent(when {
                    sending -> "正在送出請求…"
                    left > 0 -> "${minutes(left)}後可再次測試"
                    else -> "送出雲端測試警報"
                }, transitionSpec = {
                    (slideInVertically(spring(.8f, 520f)) { it / 2 } + fadeIn(tween(160))) togetherWith
                        (slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(120)))
                }, label = "雲端測試") { label ->
                    Text(label, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Text(message ?: status, color = if (message != null) palette.error else palette.onSurfaceVariant,
            fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

private fun minutes(seconds: Long): String {
    val m = seconds / 60; val s = seconds % 60
    return if (m > 0) "$m 分 ${"%02d".format(s)} 秒" else "$s 秒"
}
