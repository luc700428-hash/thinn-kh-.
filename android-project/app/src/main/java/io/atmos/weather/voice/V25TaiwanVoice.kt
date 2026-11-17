package io.atmos.weather.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Picks the most natural Mandarin (Taiwan) voice the phone has, and phrases text the way a person
 * would say it.
 *
 * - Urgent speech (earthquake warnings) only uses voices installed on the phone, so it starts at
 *   once without a network round-trip.
 * - Everyday speech (reports, replays, previews) may use the engine's higher-quality network
 *   voices when they exist; the engine falls back on its own when offline.
 * - A voice the user picked in settings always wins when it is available.
 * Never downloads voices or promises a gender.
 */
object V25TaiwanVoice {
    const val PREF = "tts_voice_name"
    @Volatile private var chosen: String? = null

    fun load(ctx: Context) { chosen = ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE).getString(PREF, null) }
    fun choose(ctx: Context, name: String?) {
        chosen = name
        ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE).edit().putString(PREF, name).apply()
    }

    /** Installed Mandarin voices, best first: Taiwan, then quality, then (for calm speech) network. */
    fun candidates(tts: TextToSpeech, urgent: Boolean): List<Voice> {
        val all = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { v -> v.locale.language == "zh" && "notInstalled" !in v.features.orEmpty() }
        val taiwan = all.filter { it.locale.country.equals("TW", true) }
        val pool = (taiwan.ifEmpty { all }).filter { v -> !urgent || !v.isNetworkConnectionRequired }
        return pool.sortedWith(compareByDescending<Voice> { it.quality }
            .thenBy { if (!urgent && it.isNetworkConnectionRequired) 0 else 1 }
            .thenBy { it.latency })
    }

    fun configure(tts: TextToSpeech, urgent: Boolean = true) {
        val taiwan = Locale.forLanguageTag("zh-TW")
        val list = candidates(tts, urgent)
        val best = list.firstOrNull { it.name == chosen } ?: list.firstOrNull()
        if (best != null) {
            runCatching { tts.voice = best }.onFailure { tts.language = taiwan }
        } else {
            if (tts.isLanguageAvailable(taiwan) >= TextToSpeech.LANG_AVAILABLE) tts.language = taiwan
            else tts.language = Locale.CHINESE
        }
        // A touch slower and natural pitch for everyday speech; brisk but clear for warnings.
        tts.setSpeechRate(if (urgent) 1.04f else 0.94f)
        tts.setPitch(1.0f)
    }

    /** Text the way people say it: no symbols read out as symbols, natural pauses. */
    fun humanize(text: String): String = text
        .replace(Regex("""M\s*(\d+(?:\.\d+)?)"""), "規模$1")
        .replace(Regex("""(\d+(?:\.\d+)?)\s*km/h"""), "每小時$1公里")
        .replace(Regex("""(\d+(?:\.\d+)?)\s*km"""), "$1公里")
        .replace(Regex("""(-?\d+(?:\.\d+)?)\s*°C?"""), "$1度")
        .replace(Regex("""(\d+)\s*%"""), "百分之$1")
        .replace(Regex("""(\d+)\s*[–~～-]\s*(\d+)度"""), "$1到$2度")
        .replace(Regex("""\s*[·•|／/]\s*"""), "，")
        .replace(Regex("""[（(]([^）)]*)[）)]"""), "，$1，")
        .replace(Regex("""，{2,}"""), "，")
        .replace(Regex("""\s+"""), " ").trim()
}
