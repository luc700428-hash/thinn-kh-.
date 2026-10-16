package io.atmos.weather

import android.content.Context
import android.util.Log

/** Record the last managed Java/Kotlin crash for on-device troubleshooting.
 * Native GPU crashes may bypass this handler; no telemetry is uploaded.
 */
object CrashDiagnostics {
    private var installed=false
    fun install(ctx:Context) {
        if(installed) return
        installed=true
        val app=ctx.applicationContext
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            try {
                val scrubbed=Log.getStackTraceString(error).take(9000)
                    .replace(Regex("CWA-[0-9A-Fa-f-]{20,}"),"[REDACTED_CWA_KEY]")
                    .replace(Regex("(?i)(authorization|api[_-]?key|token)=[^\\s&]+"),"$1=[REDACTED]")
                app.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE).edit()
                    .putString("last_java_crash",scrubbed).putLong("last_java_crash_at",System.currentTimeMillis()).commit()
            } catch (_:Exception) { }
            if(previous!=null) previous.uncaughtException(thread,error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    fun last(ctx:Context):String?=ctx.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE)
        .getString("last_java_crash",null)
}
