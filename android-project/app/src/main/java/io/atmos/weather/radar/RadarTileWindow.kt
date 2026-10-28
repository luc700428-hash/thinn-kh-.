package io.atmos.weather.radar

/** Keep the previous viewport moving until its replacements arrive; never replace the map in one jump. */
internal object RadarTileWindow {
    fun <T> merge(existing:Map<RadarTile,T>,tile:RadarTile,image:T):Map<RadarTile,T> =
        (existing.filterKeys{it!=tile}+mapOf(tile to image)).entries.toList().takeLast(RadarTilePolicy.BASE_MAX_VISIBLE_TILES)
            .associate{it.key to it.value}
}
