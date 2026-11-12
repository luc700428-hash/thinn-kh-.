package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.*
import io.atmos.weather.presentation.home.*
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
class ThemeColorPickerRenderingTest:TabletDockFixture() {
    @Test fun everyColorAppliesAndPersistsWithoutResettingFormState() {
        val context=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(context)
        for(dark in listOf(false,true)) {
            var primary=0
            var showing by mutableStateOf(true)
            windowWidth=768;windowHeight=1024
            render(dark,1.5f,"theme-color-picker",{
                AppSystemBars(dark)
                primary=MaterialTheme.colorScheme.primary.toArgb()
                DockHost(3,{}) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        var query by remember{mutableStateOf("嘉義")}
                        BasicTextField(query,{query=it},Modifier.testTag("theme-query"))
                        if(showing)ThemeColorPage(Modifier.weight(1f)){showing=false}
                    }
                }
            }){view,_->
                val original=AppThemeColors.saved
                for(theme in AppThemeColor.entries) {
                    val gallery=tagged(view,"theme-color-gallery")
                    assertTrue(gallery.config[SemanticsActions.ScrollToIndex].action!!.invoke(theme.ordinal))
                    pump(view).recycle()
                    val option=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&
                        "主題顏色：${theme.title}" in it.config[SemanticsProperties.ContentDescription]}
                    assertTrue(option.config[SemanticsActions.OnClick].action!!.invoke())
                    val expected=BrandPalette.scheme(theme,dark).primary.toArgb()
                    settle(view){primary==expected}
                    assertEquals(theme,AppThemeColors.value)
                    val window=(view.context as android.app.Activity).window
                    val background=window.decorView.background as android.graphics.drawable.ColorDrawable
                    assertEquals(BrandPalette.scheme(theme,dark).background.toArgb(),background.color)
                    assertEquals(android.graphics.Color.TRANSPARENT,window.navigationBarColor)
                    assertEquals(original,AppThemeColors.saved)
                    assertEquals(original.id,context.getSharedPreferences("thinn_settings",0).getString(AppThemeColors.KEY,null)?:AppThemeColor.FOREST.id)
                    assertEquals("嘉義",tagged(view,"theme-query").config[SemanticsProperties.EditableText].text)
                    val options=nodes(view).filter{it.config.contains(SemanticsProperties.ContentDescription)&&
                        it.config[SemanticsProperties.ContentDescription].any{s->s.startsWith("主題顏色：")}}
                    assertEquals(1,options.count{it.config[SemanticsProperties.Selected]})
                    visible(option.boundsInRoot)
                    val widget=io.atmos.weather.widgets.WidgetGlassRenderer.palette(0)
                    assertEquals(io.atmos.weather.widgets.WidgetGlassRenderer.paletteFor(original,0).top,widget.top)
                    save(view,"theme-picker-${theme.id}-$dark")
                }
                val confirm=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&"確認" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
                assertTrue(confirm.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
                assertFalse(showing)
                AppThemeColors.initialize(context)
                assertEquals(AppThemeColor.entries.last(),AppThemeColors.value)
                assertEquals(AppThemeColor.entries.last(),AppThemeColors.saved)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeWorkspaceRenderingTest:TransparentDockFixture() {
    @Test fun weatherToolsSettingsAndQuakesFollowTheSelectedTheme() {
        installOfflineWorkspaceNetwork()
        val context=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(context)
        val repo=object:WeatherDataRepository {
            override suspend fun load(city:WeatherCity,forceRefresh:Boolean)=WeatherLoadResult.Success(
                weather().copy(city=city,condition=WeatherCondition.CLEAR,isDay=true,weatherCode=0,windKmh=0.0),false,false)
        }
        for(dark in listOf(false,true)) {
            val store=ViewModelStore();val vm=WeatherViewModel(repo,context);store.put("weather",vm)
            windowWidth=1280;windowHeight=800
            try {
                render(dark,1f,"theme-workspace",{
                    CompositionLocalProvider(LocalDevicePosture provides DevicePosture(DeviceKind.TABLET)) {
                        AdaptiveWindowProvider{WeatherHomeScreen(vm,{})}
                    }
                }){view,_->
                    settle(view){nodes(view).any{it.config.contains(SemanticsProperties.Text)&&
                        it.config[SemanticsProperties.Text].any{t->t.text.contains("現在的天氣")}}}
                    for(theme in AppThemeColor.entries) {
                        AppThemeColors.update(context,theme);pump(view).recycle();pump(view).recycle()
                        for((index,label) in listOf("天氣","地震","工具","設定").withIndex()) {
                            val tab=tabs(view).single{it.config[SemanticsProperties.ContentDescription].contains(label)}
                            assertTrue(tab.config[SemanticsActions.OnClick].action!!.invoke())
                            pump(view).recycle();assertDockLayout(view)
                            assertEquals(1,tabs(view).count{it.config[SemanticsProperties.Selected]})
                            assertTrue(tabs(view)[index].config[SemanticsProperties.Selected])
                            save(view,"theme-${theme.id}-${index}-$dark")
                        }
                    }
                    assertNull(io.atmos.weather.CrashDiagnostics.graphics(context))
                }
            }finally{store.clear()}
        }
    }
}
