package io.atmos.weather.ui
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
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
class ThemePreviewCancellationRenderingTest:TabletDockFixture(){
 @Test fun returningCancelsThePreviewWithoutChangingSavedThemeOrWidget() {
  val c=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(c)
  var showing by mutableStateOf(true)
  render(true,1f,"theme-preview-cancel",{if(showing)ThemeColorPage{showing=false}}){view,_->
   val saved=AppThemeColors.saved
   val target=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&"主題顏色：玫瑰粉" in it.config[SemanticsProperties.ContentDescription]}
   assertTrue(target.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
   AppThemeColors.initialize(c)
   assertEquals(AppThemeColor.ROSE,AppThemeColors.value);assertEquals(saved,AppThemeColors.saved)
   assertEquals(ThemeColorTint.argb(0xFF2F6858.toInt(),saved),brandArgb(0xFF2F6858))
   val back=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&"返回" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
   assertTrue(back.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
   assertFalse(showing);assertEquals(saved,AppThemeColors.value)
  }
 }
}
