package io.atmos.weather.widgets

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Light or dark glass for launcher widgets.
 *
 * A widget cannot read the wallpaper, but the system publishes its dominant colour and
 * whether dark text is readable on it. Those two public facts choose the glass, so white
 * text never lands on a bright wallpaper and dark text never lands on a dark one. Without
 * them the system day/night setting decides and the glass keeps a more opaque floor.
 */
object WidgetGlassTone {
    data class Tone(val dark:Boolean,val knownBackdrop:Boolean)

    /** Used before anything is known about the launcher: light glass with the readable floor. */
    val FALLBACK=Tone(dark=false,knownBackdrop=false)
    /** Dominant wallpaper luminance from which dark text on light glass is used. */
    const val LIGHT_GLASS_FROM=.58f
    private const val FRESH_MS=20_000L

    @Volatile private var cached:Tone?=null
    @Volatile private var cachedAt=0L
    @Volatile private var installed=false

    /** Pure decision so it can be verified without a device. */
    fun decide(supportsDarkText:Boolean?,luminance:Float?,night:Boolean):Tone=when {
        supportsDarkText==true->Tone(dark=false,knownBackdrop=true)
        luminance!=null&&luminance.isFinite()->Tone(dark=luminance<LIGHT_GLASS_FROM,knownBackdrop=true)
        else->Tone(dark=night,knownBackdrop=false)
    }

    fun current(context:Context):Tone {
        val now=SystemClock.elapsedRealtime()
        val recent=cached
        if(recent!=null&&now-cachedAt in 0L..FRESH_MS)return recent
        val tone=runCatching{read(context)}.getOrDefault(FALLBACK)
        cached=tone;cachedAt=now
        return tone
    }

    private fun read(context:Context):Tone {
        val night=(context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES
        val colors:WallpaperColors?=runCatching{
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull()
        if(colors==null)return decide(null,null,night)
        val darkText=(colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT)!=0
        val luminance=runCatching{colors.primaryColor.luminance()}.getOrNull()
        return decide(darkText,luminance,night)
    }

    fun invalidate() {cached=null}

    /**
     * Repaint placed widgets when the wallpaper changes while the process is alive.
     * Otherwise the next scheduled widget refresh picks the new tone up.
     */
    fun install(context:Context) {
        if(installed)return
        installed=true
        val app=context.applicationContext
        runCatching {
            WallpaperManager.getInstance(app).addOnColorsChangedListener({_,which->
                if((which and WallpaperManager.FLAG_SYSTEM)!=0) {
                    invalidate()
                    WidgetSync.refreshCached(app)
                }
            },Handler(Looper.getMainLooper()))
        }
    }
}
