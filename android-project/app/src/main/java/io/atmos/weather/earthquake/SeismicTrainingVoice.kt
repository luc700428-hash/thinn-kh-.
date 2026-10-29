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
class SeismicTrainingVoice(context: Context, incident: EewIncident, place:SeismicPlace?) {
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
                    io.atmos.weather.voice.V25TaiwanVoice.configure(tts)
                    tts.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    val narration=if(ChiChi921.isScenario(incident))ChiChi921.trainingNarration(incident,place)
                        else "收到地震速報！慎防強烈搖晃！這是本機虛構模擬演練，並非真實地震。"+
                            SeismicSpeech.warning(incident,place).substringAfter("趴下、掩護、穩住！")
                    tts.speak(narration,
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
