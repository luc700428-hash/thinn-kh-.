package io.atmos.weather.update

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/** Persistent mirror of the foreground transfer; UI lifetime never owns the job. */
internal object OtaRetainedSession {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val installing=mutableStateOf(false)
    val downloadJob=mutableStateOf<Job?>(null)
    val pending=mutableStateOf<ReleaseInfo?>(null)
    val pendingDelta=mutableStateOf("")
    val status=mutableStateOf("版本與更新中心已開啟")
    val downloaded=mutableLongStateOf(0L)
    val totalBytes=mutableStateOf<Long?>(null)
    val lastError=mutableStateOf<String?>(null)
    val installerPrompted=mutableStateOf(false)
    val ready=mutableStateOf(false)
    val latestChecked=mutableStateOf<ReleaseInfo?>(null)
    val checkedSuccessfully=mutableStateOf(false)
    private var restored=false
    fun restore(context:android.content.Context) {
        if(restored) {reconcileInstalled(context);return}
        restored=true
        val p=context.getSharedPreferences("thinn_ota_transfer",0)
        val release=runCatching { org.json.JSONObject(p.getString("release",null) ?: return@runCatching null).let {
            ReleaseInfo(it.getString("version"),it.optString("notes"),it.getString("url"),it.getString("sha256"),it.optLong("apk_size",0L).takeIf {v -> v>0L},it.optLong("version_code",0L).takeIf {v ->v>0L})
        }}.getOrNull()?.takeIf { runCatching {AppUpdater.validateRelease(it)}.isSuccess }
        if(release!=null && !AppUpdater.isNewerThanInstalled(context,release)) {
            clearInstalled(context,release);return
        }
        pending.value=release
        downloaded.longValue=p.getLong("downloaded",0L)
        totalBytes.value=p.getLong("total",0L).takeIf {it>0L}
        ready.value=release!=null && p.getBoolean("ready",false) && AppUpdater.downloadFile(context,release).isFile
        if(release!=null) status.value=if(ready.value) "安裝檔已準備好，請按立即安裝" else "下載已暫停，可繼續下載"
    }
    fun reconcileInstalled(context:android.content.Context):Boolean {
        val release=pending.value ?: return false
        if(AppUpdater.isNewerThanInstalled(context,release))return false
        clearInstalled(context,release);return true
    }
    private fun clearInstalled(context:android.content.Context,release:ReleaseInfo) {
        downloadJob.value?.cancel();downloadJob.value=null;installing.value=false
        pending.value=null;pendingDelta.value="";ready.value=false;installerPrompted.value=false
        downloaded.longValue=0;totalBytes.value=null;lastError.value=null
        checkedSuccessfully.value=false;latestChecked.value=null
        status.value="已安裝 "+io.atmos.weather.BuildConfig.VERSION_NAME+"，正在重新核對最新版本"
        context.getSharedPreferences("thinn_ota_transfer",0).edit().clear().apply()
        // Only this obsolete OTA's files are removed; no alarms or app data are cleared.
        val target=AppUpdater.downloadFile(context,release)
        listOf(target,java.io.File(target.path+".part"),java.io.File(target.path+".etag")).forEach {it.delete()}
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(250025)
    }
    fun persist(context:android.content.Context) {
        val release=pending.value ?: run {
            context.getSharedPreferences("thinn_ota_transfer",0).edit().clear().apply();return
        }
        val json=org.json.JSONObject().put("version",release.version).put("notes",release.notes)
            .put("url",release.url).put("sha256",release.sha256).put("apk_size",release.sizeBytes ?: 0L).put("version_code",release.versionCode ?: 0L)
        context.getSharedPreferences("thinn_ota_transfer",0).edit().putString("release",json.toString())
            .putLong("downloaded",downloaded.longValue).putLong("total",totalBytes.value ?: 0L)
            .putBoolean("ready",ready.value).apply()
    }
}
