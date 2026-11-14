package io.atmos.weather.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.SunPosition
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One light for every piece of glass, placed where the sun is in the user's sky: it rises on
 * the left (east), stands high at noon and sets on the right (west), as if the user were facing
 * south. After sunset the moon takes over the same way. With neither above the horizon the
 * light rests at the upper left, the classic reading light.
 */
object GlassLight {
    /** Unit vector in screen coordinates pointing from the glass TOWARD the light. */
    data class Light(val x:Float,val y:Float)
    val REST=Light(-.5f,-.866f)
    private const val UPDATE_MS=60_000L

    /**
     * azimuth in degrees (0 north, 90 east, 180 south, 270 west), altitude in degrees.
     * Returns null when the body is below the horizon.
     */
    fun fromSky(azimuthDegrees:Double,altitudeDegrees:Double):Light? {
        if(!azimuthDegrees.isFinite()||!altitudeDegrees.isFinite()||altitudeDegrees<=0.0)return null
        val az=azimuthDegrees*PI/180.0;val alt=altitudeDegrees.coerceAtMost(90.0)*PI/180.0
        // East (90°) maps to the left edge, west (270°) to the right; height follows altitude.
        val x=(-sin(az)*cos(alt)).toFloat()
        val y=-(0.25+0.75*sin(alt)).toFloat()
        val length=sqrt(x*x+y*y).coerceAtLeast(1e-4f)
        // Whole-degree steps: a light that moves a fraction of a degree must not rebuild shaders.
        val angle=Math.round(Math.toDegrees(kotlin.math.atan2((y/length).toDouble(),(x/length).toDouble()))).toDouble()*PI/180.0
        return Light(cos(angle).toFloat(),sin(angle).toFloat())
    }

    fun at(latitude:Double,longitude:Double,time:Date):Light {
        if(!latitude.isFinite()||!longitude.isFinite()||latitude !in -90.0..90.0||longitude !in -180.0..180.0)return REST
        return runCatching {
            val sun=SunPosition.compute().on(time).at(latitude,longitude).execute()
            fromSky(sun.azimuth,sun.altitude)?:run {
                val moon=MoonPosition.compute().on(time).at(latitude,longitude).execute()
                fromSky(moon.azimuth,moon.altitude)
            }
        }.getOrNull()?:REST
    }

    private fun place(context:Context):Pair<Double,Double> {
        val prefs=context.getSharedPreferences("atmos_settings",Context.MODE_PRIVATE)
        val lat=if(prefs.contains("lat"))Double.fromBits(prefs.getLong("lat",0L)) else 25.0330
        val lon=if(prefs.contains("lon"))Double.fromBits(prefs.getLong("lon",0L)) else 121.5654
        return lat to lon
    }

    /** Current light for the selected city, refreshed once a minute while glass is on screen. */
    @Composable fun current(follow:Boolean):Light {
        val context=LocalContext.current
        var light by remember{mutableStateOf(REST)}
        LaunchedEffect(follow) {
            if(!follow){light=REST;return@LaunchedEffect}
            while(true) {
                light=withContext(Dispatchers.Default){val (lat,lon)=place(context);at(lat,lon,Date())}
                delay(UPDATE_MS)
            }
        }
        return light
    }
}
