package io.atmos.weather.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech

/** Selection of a *published Android TTS service*, never the private Bixby API.
 * Package visibility is declared via manifest <queries>.
 */
object SamsungTtsSelector {
    fun preferredEngine(context: Context): String? {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return null
        val intent=Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        @Suppress("DEPRECATION")
        val names=runCatching {
            context.packageManager.queryIntentServices(intent, 0).mapNotNull { it.serviceInfo?.packageName }
        }.getOrDefault(emptyList())
        return names.firstOrNull { name ->
            name.equals("com.samsung.SMT", ignoreCase = true) ||
                (name.startsWith("com.samsung.") &&
                    (name.contains("tts",true) || name.contains("smt",true)))
        }
    }
}
