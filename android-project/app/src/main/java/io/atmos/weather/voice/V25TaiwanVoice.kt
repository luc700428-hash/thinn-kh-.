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

    /** Sets the best voice. False when this engine cannot speak Mandarin right now. */
    fun configure(tts: TextToSpeech, urgent: Boolean = true): Boolean {
        val taiwan = Locale.forLanguageTag("zh-TW")
        val list = candidates(tts, urgent)
        val best = list.firstOrNull { it.name == chosen } ?: list.firstOrNull()
        val ok = if (best != null) {
            runCatching { tts.voice = best }.isSuccess || tts.setLanguage(taiwan) >= TextToSpeech.LANG_AVAILABLE
        } else {
            val result = if (tts.isLanguageAvailable(taiwan) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(taiwan)
                else tts.setLanguage(Locale.CHINESE)
            result >= TextToSpeech.LANG_AVAILABLE
        }
        // A touch slower and natural pitch for everyday speech; brisk but clear for warnings.
        tts.setSpeechRate(if (urgent) 1.04f else 0.94f)
        tts.setPitch(1.0f)
        return ok
    }

    /** Google's speech engine, whose neural Mandarin voices sound most like a person. */
    const val GOOGLE = "com.google.android.tts"

    fun preferredEngine(ctx: Context): String? = runCatching {
        ctx.packageManager.getPackageInfo(GOOGLE, 0); GOOGLE
    }.getOrNull()

    /**
     * Starts the most natural engine: Google's when it is installed and has a usable Mandarin
     * voice (for warnings: one stored on the phone), otherwise the phone's default engine.
     * [onReady] runs on the main thread with a configured engine, or null if none can speak.
     */
    fun open(ctx: Context, urgent: Boolean, onReady: (TextToSpeech?) -> Unit) {
        val app = ctx.applicationContext
        load(app)
        fun fallback() {
            var d: TextToSpeech? = null
            d = TextToSpeech(app) { status ->
                val t = d
                if (status == TextToSpeech.SUCCESS && t != null && configure(t, urgent)) onReady(t)
                else { t?.shutdown(); onReady(null) }
            }
        }
        val preferred = preferredEngine(app) ?: return fallback()
        var g: TextToSpeech? = null
        g = TextToSpeech(app, { status ->
            val t = g
            if (status == TextToSpeech.SUCCESS && t != null && configure(t, urgent)) onReady(t)
            else { t?.shutdown(); fallback() }
        }, preferred)
    }

    /**
     * Speaks [text] the way a person reads aloud: sentence by sentence, with a short breath after
     * each. The last sentence carries [id], so a listener waiting for [id] hears the end.
     */
    fun say(tts: TextToSpeech, text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, id: String): Int {
        val parts = phrases(humanize(text)).ifEmpty { return TextToSpeech.SUCCESS }
        var result = TextToSpeech.SUCCESS
        parts.forEachIndexed { i, part ->
            val last = i == parts.lastIndex
            val r = tts.speak(part, if (i == 0) queueMode else TextToSpeech.QUEUE_ADD, null, if (last) id else "$id#$i")
            if (r != TextToSpeech.SUCCESS) result = r
            if (!last) tts.playSilentUtterance(pause(part), TextToSpeech.QUEUE_ADD, "$id#p$i")
        }
        return result
    }

    /** Sentences, each ending with its punctuation; tiny fragments join the next one. */
    fun phrases(text: String): List<String> {
        val raw = Regex("""[^。！？!?；;]+[。！？!?；;]*""").findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        val out = mutableListOf<String>()
        var carry = ""
        for (piece in raw) {
            val joined = carry + piece
            if (joined.trimEnd('。', '！', '？', '!', '?', '；', ';').length < 4) carry = joined else { out += joined; carry = "" }
        }
        if (carry.isNotEmpty()) { if (out.isEmpty()) out += carry else out[out.lastIndex] = out.last() + carry }
        return out
    }

    /** A breath after a sentence: longer after an exclamation or question, short after a semicolon. */
    fun pause(sentence: String): Long = when (sentence.lastOrNull()) {
        '！', '!' -> 280L
        '？', '?' -> 260L
        '；', ';' -> 160L
        else -> 220L
    }

    /** Text the way people say it: no symbols read out as symbols, natural pauses. */
    fun humanize(text: String): String = text
        // 11/18 → 11月18日, 05:00 → 5點, 17:30 → 17點30分: how people say dates and times.
        .replace(Regex("""(?<![\d/])(1[0-2]|0?[1-9])/(3[01]|[12]\d|0?[1-9])(?![\d/])""")) { "${it.groupValues[1].toInt()}月${it.groupValues[2].toInt()}日" }
        .replace(Regex("""(?<![\d:])([01]?\d|2[0-3]):([0-5]\d)(?![\d:])""")) {
            val h = it.groupValues[1].toInt(); val m = it.groupValues[2]
            if (m == "00") "${h}點" else "${h}點${m}分"
        }
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
