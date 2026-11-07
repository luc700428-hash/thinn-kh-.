package io.atmos.weather.ui
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
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

internal fun unmergedTag(view:android.view.View,tag:String):SemanticsNode {
 fun walk(n:SemanticsNode):List<SemanticsNode> =listOf(n)+n.children.flatMap{walk(it)}
 fun collect(v:android.view.View):List<SemanticsNode> =if(v is androidx.compose.ui.node.RootForTest)walk(v.semanticsOwner.unmergedRootSemanticsNode)
  else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{collect(v.getChildAt(it))} else emptyList()
 return collect(view).single{it.config.contains(SemanticsProperties.TestTag)&&it.config[SemanticsProperties.TestTag]==tag}
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PhoneAppearanceRenderingTest:IPadFixture() {
 override val reduceTransparency=false
 @Test fun realPhoneShowsSquarePastelsAndReadableWeatherAboveItsDock() {
  installOfflineWorkspaceNetwork();val context=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(context)
  val repo=object:WeatherDataRepository{override suspend fun load(city:WeatherCity,forceRefresh:Boolean)=WeatherLoadResult.Success(weather().copy(city=city,condition=WeatherCondition.CLEAR,isDay=true),false,false)}
  val store=ViewModelStore();val vm=WeatherViewModel(repo,context);store.put("weather",vm)
  windowWidth=412;windowHeight=915
  try{for(dark in listOf(false,true))render(dark,1f,"phone-theme",{AdaptiveWindowProvider{WeatherHomeScreen(vm,{})}}){view,_->
   settle(view){nodes(view).any{it.config.contains(SemanticsProperties.Text)&&it.config[SemanticsProperties.Text].any{t->t.text.contains("現在的天氣")}}}
   val dock=nodes(view).single{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab&&text(it,"天氣")}.boundsInRoot
   nodes(view).filter{it.config.contains(SemanticsProperties.Text)&&it.boundsInRoot.height>0&&it.boundsInRoot.width>100}.forEach{assertTrue(it.boundsInRoot.bottom<=dock.top-4)}
   save(view,"phone-weather-pastel-$dark")
   tapVisible(view,"設定");tapVisible(view,"外觀與主題顏色")
   val square=unmergedTag(view,"theme-swatch-forest").boundsInRoot
   assertEquals(square.width,square.height,.1f)
   assertEquals(12,nodes(view).count{it.config.contains(SemanticsProperties.ContentDescription)&&it.config[SemanticsProperties.ContentDescription].any{s->s.startsWith("主題顏色：")}})
   save(view,"phone-appearance-pastel-$dark")
   tapVisible(view,"天氣")
  }}finally{store.clear()}
 }
}
