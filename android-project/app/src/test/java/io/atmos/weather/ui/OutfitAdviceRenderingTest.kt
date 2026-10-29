package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import io.atmos.weather.lifestyle.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OutfitAdviceRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun adviceHasOneSelectedModeAndPracticalClothesWithoutServerSetupInBothThemesAndReadingSizes() {
        val ctx=RuntimeEnvironment.getApplication()
        val at=Instant.now();val w=weather().copy(observedAt=at,fetchedAt=at,
            hourly=weather().hourly.mapIndexed{i,h->h.copy(at=at.plusSeconds((i+1)*3600L))})
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            LifestylePreferenceStore.mode(ctx,OutingMode.DAILY)
            render(dark,scale,"outfit-advice",{
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    LifestyleAdviceCard(w,false,{})
                }
            }){view,palette->
                val title=nodes(view).single{text(it,"出門穿搭清單")};contrast(title,palette.background)
                assertFalse(layout(title).hasVisualOverflow)
                for(label in listOf("上衣","褲子","外套","鞋子","雨具")) {
                    val n=nodes(view).single{text(it,label)}
                    assertFalse("Clipped $label at $scale",layout(n).hasVisualOverflow)
                }
                click(view,"通勤")
                assertEquals(1,nodes(view).count{it.config.contains(SemanticsProperties.Selected)&&it.config[SemanticsProperties.Selected]})
                assertTrue(nodes(view).any{text(it,"包覆腿部的透氣長褲，避免短褲騎車")})
                assertTrue(nodes(view).none{n->n.config.contains(SemanticsProperties.Text)&&n.config[SemanticsProperties.Text].any{
                    it.text.contains("Ollama")||it.text.contains("AI 設定")||it.text.contains("伺服器")}})
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OutfitSettingsRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun preferencesAreSavedAndOldInferenceConfigurationIsRemovedWithoutNetwork() {
        val ctx=RuntimeEnvironment.getApplication()
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            ctx.getSharedPreferences("thinn_lifestyle_ai",0).edit().putString("endpoint","https://removed.example")
                .putString("model","removed").putBoolean("enabled",true).commit()
            var closed=false
            render(dark,scale,"outfit-settings",{LifestyleSettingsPage{closed=true}}){view,_->
                assertFalse(ctx.getSharedPreferences("thinn_lifestyle_ai",0).contains("endpoint"))
                assertTrue(nodes(view).none{it.config.contains(SemanticsProperties.EditableText)})
                click(view,"怕冷");click(view,"通勤正式")
                assertEquals(2,nodes(view).count{it.config.contains(SemanticsProperties.Selected)&&it.config[SemanticsProperties.Selected]})
                click(view,"儲存穿搭偏好");assertTrue(closed)
                assertEquals(TemperatureComfort.FEELS_COLD,LifestylePreferenceStore.load(ctx).comfort)
                assertEquals(ClothingStyle.OFFICE,LifestylePreferenceStore.load(ctx).style)
            }
        }
    }
}
