package io.atmos.weather.ui
import android.app.Application
import androidx.compose.ui.semantics.*
import io.atmos.weather.widgets.WidgetTypography
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
class WidgetTypographySettingsRenderingTest:WeatherInformationRenderingFixture(){
    @Test fun launcherFontControlPreviewsPersistsAndLeavesAppReadingSizeAlone(){
        initializeWorkspaceWorkers(RuntimeEnvironment.getApplication())
        for(dark in listOf(false,true))render(dark,1.5f,"widget-font-settings",{IOSDeviceSurfacesSettings{}}){view,_->
            val slider=nodes(view).single{it.config.contains(SemanticsActions.SetProgress)}
            assertTrue(slider.config[SemanticsActions.SetProgress].action!!.invoke(1.4f));pump(view).recycle()
            val ctx=RuntimeEnvironment.getApplication()
            assertEquals(1.4f,WidgetTypography.read(ctx),.001f)
            assertEquals(1.5f,ctx.getSharedPreferences("thinn_settings",0).getFloat("v4_fontscale",0f),.001f)
            assertTrue(nodes(view).any{text(it,"140%")})
            assertFalse(layout(nodes(view).single{text(it,"天氣夥伴  21°")} ).hasVisualOverflow)
            save(view,"widget-font-control-$dark")
        }
    }
}
