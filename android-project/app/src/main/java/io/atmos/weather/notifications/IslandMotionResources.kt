package io.atmos.weather.notifications

import io.atmos.weather.R

/** Original 32-vector icon atlas; two states per event, driven only by source event updates. */
internal object IslandMotionResources {
    fun drawable(scene: IslandIconScene, frame: Int): Int {
        val active = frame == 1
        return when (scene) {
            IslandIconScene.ALARM -> if(active) R.drawable.island_alarm_1 else R.drawable.island_alarm_0
            IslandIconScene.SUN -> if(active) R.drawable.island_sun_1 else R.drawable.island_sun_0
            IslandIconScene.CLOUD -> if(active) R.drawable.island_cloud_1 else R.drawable.island_cloud_0
            IslandIconScene.RAIN -> if(active) R.drawable.island_rain_1 else R.drawable.island_rain_0
            IslandIconScene.SNOW -> if(active) R.drawable.island_snow_1 else R.drawable.island_snow_0
            IslandIconScene.TIMER -> if(active) R.drawable.island_timer_1 else R.drawable.island_timer_0
            IslandIconScene.QUAKE -> if(active) R.drawable.island_quake_1 else R.drawable.island_quake_0
            IslandIconScene.TYPHOON -> if(active) R.drawable.island_typhoon_1 else R.drawable.island_typhoon_0
            IslandIconScene.THUNDER -> if(active) R.drawable.island_thunder_1 else R.drawable.island_thunder_0
            IslandIconScene.HOT -> if(active) R.drawable.island_hot_1 else R.drawable.island_hot_0
            IslandIconScene.COLD -> if(active) R.drawable.island_cold_1 else R.drawable.island_cold_0
            IslandIconScene.AIR -> if(active) R.drawable.island_air_1 else R.drawable.island_air_0
            IslandIconScene.UV -> if(active) R.drawable.island_uv_1 else R.drawable.island_uv_0
            IslandIconScene.NOTE -> if(active) R.drawable.island_note_1 else R.drawable.island_note_0
            IslandIconScene.CALENDAR -> if(active) R.drawable.island_calendar_1 else R.drawable.island_calendar_0
            IslandIconScene.GENERIC -> if(active) R.drawable.island_generic_1 else R.drawable.island_generic_0
        }
    }
}
