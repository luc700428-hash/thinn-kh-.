package io.atmos.weather
import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.atmos.weather.ui.HighRefreshWindow
class ThinnApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        CrashDiagnostics.install(this)
        registerActivityLifecycleCallbacks(object:ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity:Activity,state:Bundle?) {HighRefreshWindow.apply(activity.window,activity.window.decorView)}
            override fun onActivityResumed(activity:Activity) {activity.window.decorView.post {HighRefreshWindow.apply(activity.window,activity.window.decorView)}}
            override fun onActivityStarted(activity:Activity)=Unit
            override fun onActivityPaused(activity:Activity)=Unit
            override fun onActivityStopped(activity:Activity)=Unit
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle)=Unit
            override fun onActivityDestroyed(activity:Activity)=Unit
        })
    }
}
