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
    private var restored=false
    fun restore(context:android.content.Context) {
        if(restored) return
        restored=true
        val p=context.getSharedPreferences("thinn_ota_transfer",0)
        val release=runCatching { org.json.JSONObject(p.getString("release",null) ?: return@runCatching null).let {
            ReleaseInfo(it.getString("version"),it.optString("notes"),it.getString("url"),it.getString("sha256"),it.optLong("apk_size",0L).takeIf {v -> v>0L})
        }}.getOrNull()?.takeIf { runCatching {AppUpdater.validateRelease(it)}.isSuccess }
        pending.value=release
        downloaded.longValue=p.getLong("downloaded",0L)
        totalBytes.value=p.getLong("total",0L).takeIf {it>0L}
        ready.value=release!=null && p.getBoolean("ready",false) && AppUpdater.downloadFile(context,release).isFile
        if(release!=null) status.value=if(ready.value) "安裝檔已準備好，請按立即安裝" else "下載已暫停，可繼續下載"
    }
    fun persist(context:android.content.Context) {
        val release=pending.value ?: run {
            context.getSharedPreferences("thinn_ota_transfer",0).edit().clear().apply();return
        }
        val json=org.json.JSONObject().put("version",release.version).put("notes",release.notes)
            .put("url",release.url).put("sha256",release.sha256).put("apk_size",release.sizeBytes ?: 0L)
        context.getSharedPreferences("thinn_ota_transfer",0).edit().putString("release",json.toString())
            .putLong("downloaded",downloaded.longValue).putLong("total",totalBytes.value ?: 0L)
            .putBoolean("ready",ready.value).apply()
    }
}
