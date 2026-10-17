package io.atmos.weather.earthquake

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** A private, user-initiated practice voice. Never creates an EEW notification.
 * It can be immediately shut down without stopping an independent real EEW voice.
 */
class SeismicTrainingVoice(context: Context) {
    private val active = AtomicBoolean(true)
    private var engine: TextToSpeech? = null
    init {
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(audio.ringerMode==AudioManager.RINGER_MODE_NORMAL &&
            nm.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_ALL) {
            var candidate: TextToSpeech? = null
            candidate=TextToSpeech(context.applicationContext) { status ->
                val tts=candidate
                if(!active.get()) { tts?.shutdown() }
                else if(status==TextToSpeech.SUCCESS && tts!=null) {
                    engine=tts
                    val available=tts.isLanguageAvailable(Locale.TAIWAN)
                    if(available>=TextToSpeech.LANG_AVAILABLE) tts.language=Locale.TAIWAN
                    else tts.language=Locale.CHINESE
                    tts.setSpeechRate(.97f)
                    tts.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    tts.speak("請注意，這是 thinn-khì 地震模擬演練，不是真實地震速報。現在模擬花蓮近海發生地震。請立即趴下、掩護、穩住，保護頭部與頸部。",
                        TextToSpeech.QUEUE_FLUSH,null,"thinn_practice_only")
                } else tts?.shutdown()
            }
        }
    }
    fun stop() {
        active.set(false)
        engine?.stop()
        engine?.shutdown()
        engine=null
    }
}
