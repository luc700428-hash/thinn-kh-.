package io.atmos.weather.ui

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Explicit, opt-in embedded 2h historical radar map. Satellite is intentionally disabled
 * until a licensed provider is configured. All historical frames are real provider frames.
 * No Javascript interface, arbitrary URL navigation or privileged Android bridge.
 */
@Composable
fun RadarHistoryDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val html = remember(context) {
        context.assets.open("radar/history.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    val web = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    // Do not navigate away from the radar viewer inside the App.
                    return true
                }
            }
            loadDataWithBaseURL("https://www.rainviewer.com/", html, "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(web) { onDispose { web.stopLoading(); web.destroy() } }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101F32))) {
            GlassTextButton(onClick=onClose,modifier=Modifier.fillMaxWidth()) {
                Text("關閉歷史雷達｜衛星來源未開通", color=Color.White)
            }
            AndroidView(factory={web},modifier=Modifier.fillMaxWidth().weight(1f))
        }
    }
}
