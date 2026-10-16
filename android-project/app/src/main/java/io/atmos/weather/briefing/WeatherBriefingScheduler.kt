package io.atmos.weather.briefing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.atmos.weather.domain.model.WeatherBriefingPlan
import io.atmos.weather.domain.model.WeatherBriefingSlot
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Persisted one-time WorkManager jobs. Android may delay execution for battery/Doze. */
object WeatherBriefingScheduler {
    private fun name(id:Int)="thinn_weather_briefing_v154_$id"
    fun reschedule(ctx:Context) {
        val app=ctx.applicationContext
        val now=Instant.now()
        val zone=ZoneId.systemDefault()
        val all=WeatherBriefingStore.load(app)
        val manager=WorkManager.getInstance(app)
        for(id in 1..5) {
            val slot=all.firstOrNull{it.id==id && it.enabled}
            if(slot==null) { manager.cancelUniqueWork(name(id));continue }
            val occurrence=WeatherBriefingPlan.upcoming(listOf(slot),now,zone)
            if(occurrence==null) {manager.cancelUniqueWork(name(id));continue}
            val planned=occurrence.time.toInstant()
            val input=Data.Builder().putInt("slot_id",id).putLong("planned_ms",planned.toEpochMilli()).build()
            val req=OneTimeWorkRequestBuilder<WeatherBriefingWorker>()
                .setInitialDelay((planned.toEpochMilli()-now.toEpochMilli()).coerceAtLeast(1000L),TimeUnit.MILLISECONDS)
                .setInputData(input).build()
            manager.enqueueUniqueWork(name(id),ExistingWorkPolicy.REPLACE,req)
        }
    }
    /** Called only from the currently running worker to avoid self-cancelling while processing. */
    fun scheduleNext(ctx:Context,slot:WeatherBriefingSlot) {
        val now=Instant.now()
        val future=WeatherBriefingPlan.upcoming(listOf(slot),now,ZoneId.systemDefault()) ?: return
        val ms=future.time.toInstant().toEpochMilli()
        val req=OneTimeWorkRequestBuilder<WeatherBriefingWorker>()
            .setInitialDelay((ms-now.toEpochMilli()).coerceAtLeast(1000L),TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putInt("slot_id",slot.id).putLong("planned_ms",ms).build())
            .build()
        // Append behind this running job; REPLACE would cancel the active worker itself.
        WorkManager.getInstance(ctx).enqueueUniqueWork(name(slot.id),ExistingWorkPolicy.APPEND_OR_REPLACE,req)
    }
}

class WeatherBriefingTimeReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent?) {
        if(intent?.action in setOf(Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_BOOT_COMPLETED))
            WeatherBriefingScheduler.reschedule(context)
    }
}
