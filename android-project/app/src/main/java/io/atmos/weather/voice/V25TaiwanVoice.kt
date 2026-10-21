package io.atmos.weather.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** Prefer an already installed high-quality zh-TW voice; never silently download
 * cloud voices, promise a female voice, or prevent an emergency speech fallback.
 */
object V25TaiwanVoice {
    fun configure(tts: TextToSpeech) {
        val taiwan = Locale.forLanguageTag("zh-TW")
        val candidates = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { voice ->
                voice.locale.language == "zh" && voice.locale.country.equals("TW",true) &&
                    !voice.isNetworkConnectionRequired
            }
        val best = candidates.sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenBy { it.latency }
        ).firstOrNull()
        if (best != null) {
            runCatching { tts.voice = best }.onFailure { tts.language = taiwan }
        } else {
            if (tts.isLanguageAvailable(taiwan) >= TextToSpeech.LANG_AVAILABLE) tts.language = taiwan
            else tts.language = Locale.CHINESE
        }
        tts.setSpeechRate(0.97f)
    }
}
