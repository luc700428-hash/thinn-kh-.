package io.atmos.weather

import android.content.Context
import android.util.Log
import android.app.ActivityManager
import android.app.ApplicationExitInfo

/** Local-only crash diagnostics, including system-recorded native process exits. */
object CrashDiagnostics {
    private var installed=false
    fun install(ctx:Context) {
        if(installed) return
        installed=true
        val app=ctx.applicationContext
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            try {
                val scrubbed=redact(buildLabel()+"\n"+Log.getStackTraceString(error))
                app.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE).edit()
                    .putString("last_java_crash",scrubbed).putLong("last_java_crash_at",System.currentTimeMillis()).commit()
            } catch (_:Exception) { }
            if(previous!=null) previous.uncaughtException(thread,error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    private fun buildLabel()="thinn-khì ${BuildConfig.VERSION_NAME} · ${BuildConfig.THINN_BUILD_SHA.take(12)}"
    private fun redact(value:String)=value.take(9000)
        .replace(Regex("CWA-[0-9A-Fa-f-]{20,}"),"[REDACTED_CWA_KEY]")
        .replace(Regex("(?i)(authorization|api[_-]?key|token)=[^\\s&]+"),"$1=[REDACTED]")

    fun collectPreviousExit(ctx:Context) {
        try {
            val prefs=ctx.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE)
            val checked=prefs.getLong("checked_exit_at",0L)
            val manager=ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits=manager.getHistoricalProcessExitReasons(ctx.packageName,0,8)
            val latest=exits.filter {it.processName==ctx.packageName && it.timestamp>checked}.maxByOrNull {it.timestamp} ?: return
            val reason=when(latest.reason) {
                ApplicationExitInfo.REASON_CRASH -> "Java／Kotlin 異常"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "原生程式異常"
                ApplicationExitInfo.REASON_ANR -> "應用程式無回應"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "系統記憶體不足"
                else -> null
            }
            val editor=prefs.edit().putLong("checked_exit_at",latest.timestamp)
            if(reason!=null) editor.putLong("last_process_exit_at",latest.timestamp)
                .putString("last_process_exit",redact("Android 系統紀錄：$reason\n"+
                    "${latest.description.orEmpty()}\n時間：${java.time.Instant.ofEpochMilli(latest.timestamp)}"))
            editor.apply()
        } catch(_:RuntimeException) { }
    }
    fun graphicsFailure(ctx:Context,stage:String,error:RuntimeException) {
        Log.w("ThinnBackdrop","Glass rendering fallback: $stage",error)
        ctx.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE).edit()
            .putString("last_graphics_failure",redact(buildLabel()+"\n$stage\n"+Log.getStackTraceString(error)))
            .putLong("last_graphics_failure_at",System.currentTimeMillis()).apply()
    }
    fun graphics(ctx:Context):String?=ctx.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE)
        .getString("last_graphics_failure",null)
    fun last(ctx:Context):String? {
        val prefs=ctx.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE)
        val java=prefs.getString("last_java_crash",null)
        val exit=prefs.getString("last_process_exit",null)
        val delta=prefs.getLong("last_process_exit_at",0L)-prefs.getLong("last_java_crash_at",0L)
        return if(java!=null && delta<=10_000L) java else exit ?: java
    }
}
