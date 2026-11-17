package io.atmos.weather.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosJellyPress

/** Lists the phone's Mandarin voices, best first; tap to hear, the tick marks the one in use. */
@Composable fun VoicePickerSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var chosen by remember { mutableStateOf(ctx.getSharedPreferences("thinn_settings", 0).getString(V25TaiwanVoice.PREF, null)) }
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) { engine = t; voices = V25TaiwanVoice.candidates(t, urgent = false) }
        }
        onDispose { tts?.stop(); tts?.shutdown() }
    }
    fun preview(v: Voice?) {
        val t = engine ?: return
        V25TaiwanVoice.choose(ctx, v?.name); chosen = v?.name
        V25TaiwanVoice.configure(t, urgent = false)
        t.speak(V25TaiwanVoice.humanize("早安！今天 22–28°C，多雲時晴，降雨機率 20%。出門可以帶把折疊傘。"), TextToSpeech.QUEUE_FLUSH, null, "voice-preview")
    }
    io.atmos.weather.ui.IOSFormSheet("播報聲音", onDismiss = onClose) {
        Text("選一個聽起來最自然的聲音。標示「線上」的聲音最像真人，需要網路；地震速報一律使用手機內建聲音，確保立刻播出。",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
        if (engine != null && voices.isEmpty()) Text("這支手機沒有可用的中文語音。可到系統設定 › 文字轉語音 安裝 Google 語音服務的中文（台灣）語音。",
            color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        val rows = listOf<Voice?>(null) + voices.take(12)
        rows.forEachIndexed { i, v ->
            val on = (v?.name ?: "") == (chosen ?: "")
            Row(Modifier.fillMaxWidth().iosAppear(i).iosJellyPress().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { preview(v) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (v == null) "自動（最自然）" else "聲音 ${i}" + (if (v.isNetworkConnectionRequired) " · 線上" else " · 內建"),
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(if (v == null) "依手機上的語音自動挑最好的" else "品質 ${when { v.quality >= 500 -> "極佳"; v.quality >= 400 -> "很好"; else -> "一般" }} · ${v.locale.displayName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                if (on) Icon(Icons.Rounded.Check, "使用中", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
