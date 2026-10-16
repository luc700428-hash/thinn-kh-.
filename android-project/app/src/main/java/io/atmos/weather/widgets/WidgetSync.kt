package io.atmos.weather.widgets

import android.content.Context
import androidx.work.*
import androidx.glance.appwidget.updateAll
import io.atmos.weather.AppContainer
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.WeatherLoadResult
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** WorkManager is not an exact timer. Android allows periodic work every >=15 minutes. */
object WidgetSync {
    private const val PERIODIC = "thinn-khi-widget-interval-v3"
    private const val NOW = "thinn-khi-widget-now"
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
        WorkManager.getInstance(context).enqueueUniqueWork(NOW,ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WeatherWidgetWorker>().build())
    }
    suspend fun refreshAll(context:Context) {
        val widgets=listOf(
            AtmosWidget00(), AtmosWidget01(), AtmosWidget02(), AtmosWidget03(), AtmosWidget04(),
            AtmosWidget05(), AtmosWidget06(), AtmosWidget07(), AtmosWidget08(), AtmosWidget09(),
            AtmosWidget10(), AtmosWidget11(), AtmosWidget12(), AtmosWidget13(), AtmosWidget14(),
            AtmosWidget15(), AtmosWidget16(), AtmosWidget17(), AtmosWidget18(), AtmosWidget19(),
            AtmosWidget20(), AtmosWidget21(), AtmosWidget22(), AtmosWidget23(), AtmosWidget24(),
            SafetyWidget()
        )
        for(widget in widgets) {
            try{ widget.updateAll(context) }
            catch (_:Exception){ /* some launchers reject updates while widget not placed */ }
        }
    }
    fun updateVisible(context:Context) { request(context) }
}
class WeatherWidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result {
        val prefs=applicationContext.getSharedPreferences("atmos_settings",Context.MODE_PRIVATE)
        val id=prefs.getString("city_id",null)
        val city=SampleCities.all.find { it.id == id } ?: if(id!=null && prefs.contains("lat")) {
            WeatherCity(id,prefs.getString("city_name","目前位置") ?: "目前位置",
                GeoPoint(Double.fromBits(prefs.getLong("lat",0L)),Double.fromBits(prefs.getLong("lon",0L))))
        } else SampleCities.all.first()
        return try {
            val result=AppContainer.repository(applicationContext).load(city,false)
            if(result is WeatherLoadResult.Success) {
                // Avoid a background request overwriting a newly selected city.
                if (prefs.getString("city_id",null)==id) WidgetSnapshotStore.publish(applicationContext,result.value)
            }
            WidgetSync.refreshAll(applicationContext)
            if(result is WeatherLoadResult.Failure) Result.retry() else Result.success()
        } catch (cancel:CancellationException){throw cancel}
        catch (_:Exception){Result.retry()}
    }
}
