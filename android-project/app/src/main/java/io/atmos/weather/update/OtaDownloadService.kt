package io.atmos.weather.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.atmos.weather.MainActivity
import io.atmos.weather.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** User-started download. Android may defer restart; force-stop never auto-resumes. */
class OtaDownloadService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var lastNotice=0L
    private var timedOut=false
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate() {
        super.onCreate()
        OtaRetainedSession.restore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"App 更新下載",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==CANCEL) {
            OtaRetainedSession.downloadJob.value?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY
        }
        val release=runCatching {
            val request=requireNotNull(intent)
            ReleaseInfo(requireNotNull(request.getStringExtra("version")),request.getStringExtra("notes") ?: "",
                requireNotNull(request.getStringExtra("url")),requireNotNull(request.getStringExtra("sha256")),request.getLongExtra("apk_size",0L).takeIf {it>0L},request.getLongExtra("version_code",0L).takeIf {it>0L})
                .also(AppUpdater::validateRelease)
        }.getOrElse {stopSelf();return START_NOT_STICKY}
        if(!AppUpdater.isNewerThanInstalled(this,release)) {
            OtaRetainedSession.reconcileInstalled(this);stopSelf();return START_NOT_STICKY
        }
        // Must foreground promptly, even when Android redelivers after process death.
        try {
            ServiceCompat.startForeground(this,ID,notification("正在下載更新",true),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch(error:Exception) {
            OtaRetainedSession.installing.value=false
            OtaRetainedSession.lastError.value=error.localizedMessage ?: "系統目前不允許背景下載，請稍後重試"
            stopSelf();return START_NOT_STICKY
        }
        if(OtaRetainedSession.downloadJob.value?.isActive==true) return START_REDELIVER_INTENT
        OtaRetainedSession.pending.value=release
        OtaRetainedSession.installing.value=true
        OtaRetainedSession.ready.value=false
        OtaRetainedSession.lastError.value=null
        OtaRetainedSession.installerPrompted.value=false
        OtaRetainedSession.persist(this)
        OtaRetainedSession.downloadJob.value=scope.launch {
            try {
                AppUpdater.downloadAndInstall(this@OtaDownloadService,release,installWhenReady=false) { bytes,total ->
                    OtaRetainedSession.downloaded.longValue=bytes
                    OtaRetainedSession.totalBytes.value=total
                    if(SystemClock.elapsedRealtime()-lastNotice>750L) {
                        lastNotice=SystemClock.elapsedRealtime()
                        OtaRetainedSession.persist(this@OtaDownloadService)
                        getSystemService(NotificationManager::class.java).notify(ID,notification("正在下載更新",true))
                    }
                }
                OtaRetainedSession.ready.value=true
                OtaRetainedSession.status.value="下載與安全驗證完成，請按立即安裝"
            } catch(cancel:CancellationException) {
                OtaRetainedSession.status.value=if(timedOut) "系統下載時限已到，可稍後繼續" else "已暫停下載，可繼續下載"
            } catch(error:Exception) {
                OtaRetainedSession.lastError.value=error.localizedMessage ?: "下載失敗，請重試"
                OtaRetainedSession.status.value="更新下載失敗"
            } finally {
                OtaRetainedSession.installing.value=false
                OtaRetainedSession.downloadJob.value=null
                OtaRetainedSession.persist(this@OtaDownloadService)
                stopForeground(STOP_FOREGROUND_REMOVE)
                if(OtaRetainedSession.ready.value) {
                    runCatching { getSystemService(NotificationManager::class.java).notify(ID,notification("更新已準備好，開啟 App 安裝",false)) }
                }
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }
    override fun onTimeout(startId:Int,fgsType:Int) {
        timedOut=true
        OtaRetainedSession.downloadJob.value?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)
    }
    override fun onDestroy() {scope.cancel();super.onDestroy()}
    private fun notification(text:String,ongoing:Boolean):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java)
            .putExtra("open_update_center",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle("thinn-khì 軟體更新").setContentText(text).setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing)
        if(ongoing) {
            val total=OtaRetainedSession.totalBytes.value ?: 0L
            val bytes=OtaRetainedSession.downloaded.longValue
            builder.setProgress(1000,if(total>0L) ((bytes.toDouble()/total)*1000).toInt().coerceIn(0,1000) else 0,total<=0L)
            val stop=PendingIntent.getService(this,1,Intent(this,OtaDownloadService::class.java).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0,"暫停",stop)
        }
        return builder.build()
    }
    companion object {
        private const val CHANNEL="thinn_ota_download"
        private const val ID=250025
        private const val CANCEL="thinn.ota.PAUSE"
        fun start(context:Context,release:ReleaseInfo) {
            AppUpdater.validateRelease(release)
            require(AppUpdater.isNewerThanInstalled(context,release)) {"此版本已安裝，請重新檢查更新"}
            ContextCompat.startForegroundService(context,Intent(context,OtaDownloadService::class.java)
                .putExtra("version",release.version).putExtra("notes",release.notes)
                .putExtra("url",release.url).putExtra("sha256",release.sha256).putExtra("apk_size",release.sizeBytes ?: 0L).putExtra("version_code",release.versionCode ?: 0L))
        }
        fun cancel(context:Context) {
            if(OtaRetainedSession.installing.value) context.startService(Intent(context,OtaDownloadService::class.java).setAction(CANCEL))
        }
    }
}
