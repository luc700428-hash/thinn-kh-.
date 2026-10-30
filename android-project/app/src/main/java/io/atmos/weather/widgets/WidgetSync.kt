package io.atmos.weather.widgets

import android.content.Context
import androidx.work.*
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.longPreferencesKey
import io.atmos.weather.AppContainer
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.WeatherLoadResult
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** WorkManager is not an exact timer. Android allows periodic work every >=15 minutes. */
object WidgetSync {
    private const val PERIODIC = "thinn-khi-widget-interval-v3"
    private const val NOW = "thinn-khi-widget-now"
    internal val VIEW_REVISION=longPreferencesKey("view_revision")
    fun schedule(context:Context) {
        val prefs=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        val requested=prefs.getInt("refresh_minutes",30).coerceIn(5,120)
        val backgroundMinutes=requested.coerceAtLeast(15)
        val request=PeriodicWorkRequestBuilder<WeatherWidgetWorker>(backgroundMinutes.toLong(),TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("weather-widget-hourly")
            enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,request)
        }
    }
    fun request(context:Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW,ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }
    suspend fun refreshAll(context:Context) {
        val widgets=listOf(
            AtmosWidget00(), AtmosWidget01(), AtmosWidget02(), AtmosWidget03(), AtmosWidget04(),
            AtmosWidget05(), AtmosWidget06(), AtmosWidget07(), AtmosWidget08(), AtmosWidget09(),
            AtmosWidget10(), AtmosWidget11(), AtmosWidget12(), AtmosWidget13(), AtmosWidget14(),
            AtmosWidget15(), AtmosWidget16(), AtmosWidget17(), AtmosWidget18(), AtmosWidget19(),
            AtmosWidget20(), AtmosWidget21(), AtmosWidget22(), AtmosWidget23(), AtmosWidget24(),
            SafetyWidget(),PrecisionClockWidget(),WeatherBuddyWidget(),BuddyClockWidget()
        )
        val manager=GlanceAppWidgetManager(context)
        var failure:Exception?=null
        for(widget in widgets) {
            try {
                for(id in manager.getGlanceIds(widget.javaClass)) {
                    updateAppWidgetState(context,id){p->p[VIEW_REVISION]=(p[VIEW_REVISION]?:0L)+1L}
                    widget.update(context,id)
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){failure=error}
        }
        failure?.let{throw it}
    }
    fun updateVisible(context:Context) { refreshCached(context) }
    fun refreshCached(context:Context){
        WorkManager.getInstance(context).enqueueUniqueWork("thinn-widget-cached-view",ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetCachedViewWorker>().build())
    }
}
class WeatherWidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result {
        val target=WidgetSnapshotStore.target(applicationContext)
        val city=target.city
        return try {
            val result=AppContainer.repository(applicationContext).load(city,true)
            if(WidgetSnapshotStore.target(applicationContext).revision!=target.revision)return Result.retry()
            if(result is WeatherLoadResult.Success) {
                // Avoid a background request overwriting a newly selected city.
                if (WidgetSnapshotStore.publish(applicationContext,result.value,target.revision)) {
                    io.atmos.weather.alarm.WeatherAlarmSpeechCache.save(applicationContext,result.value)
                }
            }
            WidgetSync.refreshAll(applicationContext)
            if(result is WeatherLoadResult.Failure) Result.retry() else Result.success()
        } catch (cancel:CancellationException){throw cancel}
        catch (_:Exception){Result.retry()}
    }
}

class WidgetCachedViewWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result=try{WidgetSync.refreshAll(applicationContext);Result.success()}catch(cancel:CancellationException){throw cancel}catch(_:Exception){Result.retry()}
}
