package io.atmos.weather.earthquake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/** Private expiring drill token is validated before requesting any screen wake. */
class SeismicCriticalTestActivity:ComponentActivity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val token=intent.getStringExtra("test_ticket")
        if(!SeismicCriticalTest.valid(this,token)){finish();return}
        val allowed=SeismicAlertDelivery.mayUseFullScreen(this)
        setShowWhenLocked(allowed);setTurnScreenOn(allowed)
        val incident=SeismicSimulation.scenario(System.currentTimeMillis(),true)
        setContent{io.atmos.weather.ui.ThinnAppTheme {
            val live by SeismicAlertDelivery.active.collectAsState()
            LaunchedEffect(live){if(live!=null){SeismicCriticalTest.cancel(this@SeismicCriticalTestActivity);finish()}}
            LaunchedEffect(token){while(true){delay(1000);if(!SeismicCriticalTest.valid(this@SeismicCriticalTestActivity,token)){finish();break}}}
            SeismicWarningOverlay(incident,true,SeismicUserLocation.current(this)?.name,
                onClose={SeismicCriticalTest.cancel(this);finish()},simulation=true)
        }}
    }
}
