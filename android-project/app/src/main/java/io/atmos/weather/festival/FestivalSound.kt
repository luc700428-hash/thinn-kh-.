package io.atmos.weather.festival

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import io.atmos.weather.R

/**
 * Festival sounds on the media stream, quietly, and only while the app is on screen. Nothing plays
 * when the phone is on silent or vibrate, or when 節慶音效 is off.
 */
object FestivalSound {
    enum class Clip(val res: Int, val volume: Float) {
        LAUNCH(R.raw.festival_launch, .22f), BOOM(R.raw.festival_boom, .55f), BOOM_SMALL(R.raw.festival_boom_small, .42f),
        CRACKLE(R.raw.festival_crackle, .32f), FIRECRACKER(R.raw.festival_firecracker, .5f), SLEIGH(R.raw.festival_sleigh, .45f),
        TICK(R.raw.festival_tick, .5f), CHIME(R.raw.festival_chime, .6f), GHOST(R.raw.festival_ghost, .5f), DRUM(R.raw.festival_drum, .55f),
    }

    const val SETTING = "festival_sound"
    private var pool: SoundPool? = null
    private val ids = HashMap<Clip, Int>()
    private val ready = HashSet<Int>()
    private var carol: MediaPlayer? = null
    private var app: Context? = null

    private fun allowed(ctx: Context): Boolean {
        if (!ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE).getBoolean(SETTING, true)) return false
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        return am.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    fun open(ctx: Context) {
        if (pool != null) return
        app = ctx.applicationContext
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val p = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(ready) { ready += id } }
        pool = p
        runCatching { Clip.entries.forEach { ids[it] = p.load(app, it.res, 1) } }
    }

    fun play(clip: Clip, gain: Float = 1f) {
        val ctx = app ?: return
        if (!allowed(ctx)) return
        val p = pool ?: return
        val id = ids[clip] ?: return
        if (synchronized(ready) { id !in ready }) return
        val v = (clip.volume * gain).coerceIn(0f, 1f)
        val pan = (Math.random().toFloat() - .5f) * .3f
        p.play(id, v * (1 - pan), v * (1 + pan), 1, 0, .96f + Math.random().toFloat() * .08f)
    }

    /** The carol, once at a time. */
    fun carol() {
        val ctx = app ?: return
        if (!allowed(ctx) || carol?.isPlaying == true) return
        runCatching {
            carol?.release()
            carol = MediaPlayer.create(ctx, R.raw.festival_carol)?.apply {
                setVolume(.42f, .42f); setOnCompletionListener { it.release(); if (carol === it) carol = null }; start()
            }
        }
    }

    fun close() {
        runCatching { carol?.release() }; carol = null
        runCatching { pool?.release() }; pool = null
        ids.clear(); synchronized(ready) { ready.clear() }
    }
}
