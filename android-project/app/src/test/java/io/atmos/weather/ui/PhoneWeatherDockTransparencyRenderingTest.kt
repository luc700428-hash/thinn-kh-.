package io.atmos.weather.ui
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.*
import androidx.core.graphics.Insets
import androidx.core.view.*
import androidx.lifecycle.ViewModelStore
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.*
import io.atmos.weather.presentation.home.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
abstract class PhoneWeatherDockTransparencyFixture:TransparentDockFixture(){
    @Suppress("DEPRECATION")
    protected fun verifyForecastDockInsets(){
        installOfflineWorkspaceNetwork();val ctx=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(ctx)
        windowWidth=412;windowHeight=892
        fun activity(c:android.content.Context):android.app.Activity=when(c){is android.app.Activity->c;is android.content.ContextWrapper->activity(c.baseContext);else->error("No activity")}
        for(day in listOf(true,false)){
            val repo=object:WeatherDataRepository{
                override suspend fun load(city:WeatherCity,forceRefresh:Boolean)=WeatherLoadResult.Success(weather().copy(city=city,condition=WeatherCondition.CLEAR,isDay=day,weatherCode=0,windKmh=0.0),false,false)
            }
            val store=ViewModelStore();val vm=WeatherViewModel(repo,ctx);store.put("weather",vm)
            try{render(!day,1f,"phone-weather-dock",{
                CompositionLocalProvider(LocalDevicePosture provides DevicePosture(DeviceKind.PHONE)){AdaptiveWindowProvider{WeatherHomeScreen(vm,{})}}
            }){view,_->
                settle(view){nodes(view).any{it.config.contains(SemanticsProperties.Text)&&it.config[SemanticsProperties.Text].any{t->t.text.contains("現在的天氣")}}}
                val owner=composeOwner(view)!!;val window=activity(owner.context).window;WindowCompat.setDecorFitsSystemWindows(window,false);pump(view).recycle()
                for(bottom in listOf(24,48)){
                    val insets=WindowInsetsCompat.Builder()
                        .setSystemWindowInsets(Insets.of(0,24,0,bottom)).setStableInsets(Insets.of(0,24,0,bottom))
                        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(),Insets.of(0,24,0,0))
                        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(),Insets.of(0,0,0,bottom))
                        .setInsets(WindowInsetsCompat.Type.statusBars(),Insets.of(0,24,0,0))
                        .setInsets(WindowInsetsCompat.Type.navigationBars(),Insets.of(0,0,0,bottom))
                        .setVisible(WindowInsetsCompat.Type.navigationBars(),true).setVisible(WindowInsetsCompat.Type.statusBars(),true).build()
                    ViewCompat.dispatchApplyWindowInsets(owner,insets);pump(view).recycle()
                    save(view,"phone-insets-$day-$bottom-api${android.os.Build.VERSION.SDK_INT}")
                    val viewport=tagged(view,"weather-scroll-viewport").boundsInRoot
                    assertEquals("Content must extend behind Dock",892f,viewport.bottom,1f)
                    val tabs=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab}
                    assertEquals(4,tabs.size);val dockBottom=tabs.maxOf{it.boundsInRoot.bottom}
                    assertTrue("Dock must clear system navigation: bottom=$dockBottom, inset=$bottom",dockBottom<=892-bottom+1)
                    assertEquals(android.graphics.Color.TRANSPARENT,window.navigationBarColor);assertFalse(window.isNavigationBarContrastEnforced)
                    val scroll=nodes(view).first{it.config.contains(SemanticsProperties.VerticalScrollAxisRange)&&it.config.contains(SemanticsActions.ScrollBy)}
                    assertTrue("Forecast viewport stopped above glass",scroll.boundsInRoot.bottom>=dockBottom)
                    scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f,scroll.config[SemanticsProperties.VerticalScrollAxisRange].maxValue());pump(view).recycle()
                    val image=pump(view);assertEquals(255,android.graphics.Color.alpha(image.getPixel(3,889)));image.recycle()
                    save(view,"phone-dock-$day-$bottom-api${android.os.Build.VERSION.SDK_INT}")
                }
            }}finally{store.clear()}
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PhoneWeatherDockTransparencyRenderingTest:PhoneWeatherDockTransparencyFixture(){
    @Test fun forecastCoversDockAndGestureAreaWithActualSystemInsets()=verifyForecastDockInsets()
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LegacyPhoneWeatherDockTransparencyRenderingTest:PhoneWeatherDockTransparencyFixture(){
    @Test fun forecastCoversDockAndGestureAreaWithActualSystemInsets()=verifyForecastDockInsets()
}
