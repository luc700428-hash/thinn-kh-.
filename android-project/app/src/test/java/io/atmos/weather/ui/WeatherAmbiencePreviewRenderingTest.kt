package io.atmos.weather.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.repository.WeatherDataRepository
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.presentation.home.WeatherHomeScreen
import io.atmos.weather.presentation.home.WeatherViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherAmbiencePreviewRenderingTest:IPadFixture() {
    @Test fun theRealSettingsPreviewStartsStopsAndRetriesWithoutTheUnavailableError() {
        installOfflineWorkspaceNetwork()
        val context=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(context)
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_MUSIC,5,0)
        val repo=object:WeatherDataRepository {
            override suspend fun load(city:WeatherCity,forceRefresh:Boolean)=WeatherLoadResult.Success(weather().copy(city=city),false,false)
        }
        val vm=WeatherViewModel(repo,context);val store=ViewModelStore().apply{put("weather",vm)}
        try {
            render(false,1f,"weather-audio-preview",{
                CompositionLocalProvider(LocalDevicePosture provides DevicePosture(DeviceKind.TABLET)) {
                    AdaptiveWindowProvider{WeatherHomeScreen(vm,{})}
                }
            }){view,_->
                tapVisible(view,"設定");tapVisible(view,"聲音與觸覺")
                repeat(2) {
                    tapVisible(view,"試聽目前天氣音效 · 8 秒")
                    settle(view){nodes(view).any{text(it,"正在播放目前天氣環境音")}}
                    assertFalse(nodes(view).any{text(it,"目前音效無法播放，請再試聽一次")})
                    save(view,"weather-audio-preview-playing-$it")
                    tapVisible(view,"停止試聽")
                    settle(view){nodes(view).any{text(it,"天氣環境音已關閉")}}
                }
            }
        }finally{store.clear()}
    }
}
