package io.atmos.weather.radar

import kotlin.math.*

/** A continuous cursor between real observations, never between the end and start of a loop. */
internal data class RadarSample(val lower:Int,val upper:Int,val fraction:Float) {
    fun time(frames:List<RadarFrame>):Long {
        val a=frames.getOrNull(lower)?.time?:return 0
        val b=frames.getOrNull(upper)?.time?:a
        return a+((b-a)*fraction).roundToLong()
    }
}

internal object RadarPlayback {
    const val MILLIS_PER_OBSERVATION=1100.0
    const val MAX_SOURCE_TILES=4
    const val MAX_INTERPOLATION_GAP_SECONDS=900L

    fun sample(position:Float,count:Int):RadarSample {
        if(count<=1)return RadarSample(0,0,0f)
        val p=(position.takeIf{it.isFinite()}?:0f).coerceIn(0f,(count-1).toFloat())
        val lower=floor(p).toInt();val upper=ceil(p).toInt().coerceAtMost(count-1)
        return RadarSample(lower,upper,(p-lower).coerceIn(0f,1f))
    }
    fun advance(position:Float,elapsedNanos:Long,count:Int):Float {
        if(count<=1)return 0f
        // A background/resume interval must not fast-forward the history.
        val delta=elapsedNanos.coerceIn(0,100_000_000L)/1_000_000.0/MILLIS_PER_OBSERVATION
        return (position+delta).toFloat().coerceIn(0f,(count-1).toFloat())
    }
    fun loadOrder(count:Int,position:Float):List<Int> {
        val s=sample(position,count)
        return (listOf(s.lower,s.upper)+(0 until count).sortedBy{abs(it-position)})
            .filter{it in 0 until count}.distinct()
    }
    fun canInterpolate(a:RadarFrame,b:RadarFrame)=b.time-a.time in 1..MAX_INTERPOLATION_GAP_SECONDS
    fun step(position:Float,direction:Int,available:List<Int>):Float {
        val choices=available.sorted()
        return (if(direction<0)choices.lastOrNull{it<position-.001f} else choices.firstOrNull{it>position+.001f})
            ?.toFloat()?:position
    }

    /** Keep all source frames resident without multiplying native tiles at wide viewports. */
    fun tiles(camera:RadarCamera,width:Float,height:Float,density:Float,maxSources:Int=MAX_SOURCE_TILES,maxZoom:Int=RadarTilePolicy.RADAR_MAX_ZOOM):List<RadarTile> {
        var zoom=maxZoom.coerceIn(0,9)
        while(true) {
            val tiles=RadarProjection.tiles(camera,width,height,density,zoom)
            if(zoom==0 || tiles.distinctBy{Triple(it.z,it.x,it.y)}.size<=maxSources.coerceAtLeast(1))return tiles
            zoom--
        }
    }
}

/** Rolling provider budget. Disk and memory hits do not consume network requests. */
internal class RadarRequestBudget(private val limit:Int=90,private val windowMillis:Long=60_000L) {
    private val sent=ArrayDeque<Long>()
    @Synchronized fun reserve(now:Long):Long {
        while(sent.isNotEmpty() && now-sent.first()>=windowMillis)sent.removeFirst()
        if(sent.size>=limit)return (sent.first()+windowMillis-now).coerceAtLeast(1)
        sent.addLast(now);return 0
    }
}
