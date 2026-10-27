package io.atmos.weather.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.IdlingPolicies
import androidx.test.platform.app.InstrumentationRegistry
import io.atmos.weather.CrashDiagnostics
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.WeatherDataRepository
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.presentation.home.WeatherHomeScreen
import io.atmos.weather.presentation.home.WeatherViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RenderingRegressionTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        compose.mainClock.autoAdvance=false
        IdlingPolicies.setMasterPolicyTimeout(30,TimeUnit.SECONDS)
        IdlingPolicies.setIdlingResourceTimeout(30,TimeUnit.SECONDS)
        context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).edit()
            .putBoolean("reduce_motion",false).putBoolean("reduce_transparency",false)
            .putBoolean("sound_enabled",false).commit()
        context.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun frame() {compose.mainClock.advanceTimeBy(650);compose.waitForIdle()}
    private fun checkpoint(stage:String) {
        Log.i("ThinnUiRegression",stage)
        val dir=File(context.getExternalFilesDir(null),"ui-regression").apply {mkdirs()}
        File(dir,"progress.txt").appendText("${Instant.now()} $stage\n")
    }

    @Test(timeout=120_000) fun glassSamplingCannotPaintOverItsForegroundSiblings() {
        checkpoint("glass: set content")
        compose.setContent {
            ThinnAppTheme {
                val source=rememberThinnBackdrop()
                CompositionLocalProvider(LocalThinnBackdrop provides source) {
                    Box(Modifier.size(300.dp,480.dp).testTag("viewport")) {
                        ThinnBackdropCapture(source,Modifier.matchParentSize()) {
                            Box(Modifier.matchParentSize().background(Color(0xFF14365F)))
                        }
                        Box(Modifier.offset(20.dp,20.dp).size(80.dp,40.dp).background(Color.Red))
                        Box(Modifier.offset(20.dp,360.dp).size(260.dp,90.dp)
                            .clip(RoundedCornerShape(16.dp)).thinnBackdrop(false,16.dp)
                            .background(Color.White.copy(alpha=.2f))) {Text("24 小時預報",color=Color.White)}
                    }
                }
            }
        }
        frame()
        val pixels=compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val marker=pixels[(pixels.width*.15f).toInt(),(pixels.height*.07f).toInt()]
        assertTrue("Backdrop covered an earlier foreground sibling",marker.red>.9f && marker.green<.1f)
        val sky=pixels[pixels.width/2,pixels.height/2]
        assertEquals(0x14/255f,sky.red,.04f)
        assertNull(CrashDiagnostics.graphics(context))
    }

    @Test(timeout=120_000) fun deferredChildRecordingCannotSampleItsOwnAncestor() {
        checkpoint("ancestor: set content")
        val page=mutableIntStateOf(0)
        compose.setContent {
            ThinnAppTheme {
                val source=rememberThinnBackdrop()
                CompositionLocalProvider(LocalThinnBackdrop provides source) {
                    ThinnBackdropCapture(source,Modifier.size(300.dp,400.dp).testTag("own-source")) {
                        Box(Modifier.matchParentSize().graphicsLayer {alpha=.94f}
                            .thinnBackdrop(false).background(if(page.intValue%2==0)Color.Red else Color.Green))
                    }
                }
            }
        }
        repeat(12) {index ->
            compose.runOnIdle {page.intValue=index}
            frame()
            val pixels=compose.onNodeWithTag("own-source").captureToImage().toPixelMap()
            val color=pixels[pixels.width/2,pixels.height/2]
            assertTrue("Captured child became blank after a page transition",
                if(index%2==0)color.red>.8f else color.green>.8f)
        }
        assertNull(CrashDiagnostics.graphics(context))
    }

    @Test(timeout=120_000) fun weatherScrollAndRepeatedSettingsNavigationKeepVisibleText() {
        checkpoint("weather: prepare fixture")
        val now=Instant.now()
        val city=SampleCities.all.first()
        val fixture=WeatherSnapshot(city,now,now,"instrumentation-fixture",22.0,23.0,70,12.0,1012.0,
            WeatherCondition.PARTLY_CLOUDY,true,null,null,
            (0..23).map {HourlyWeather(now.plusSeconds(it*3600L),22.0+it%4,20,2.0,windKmh=12.0,weatherCode=2)},
            maxTempC=26.0,minTempC=20.0,weatherCode=2,timeZoneId="Asia/Taipei")
        val repository=object:WeatherDataRepository {
            override suspend fun load(city:WeatherCity,forceRefresh:Boolean)=WeatherLoadResult.Success(fixture,false,false)
        }
        checkpoint("weather: set content")
        compose.setContent {
            val ctx=LocalContext.current
            val vm:WeatherViewModel=viewModel(factory=WeatherViewModel.Factory(repository,ctx))
            ThinnAppTheme {WeatherHomeScreen(vm,onLocate={})}
        }
        checkpoint("weather: await fixture")
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodesWithText("24 小時預報").fetchSemanticsNodes().isNotEmpty()
        }
        repeat(4) {iteration ->
            checkpoint("weather: round $iteration forecast")
            centerWeatherText("24 小時預報")
            assertPaintedText("24 小時預報")
            checkpoint("weather: round $iteration settings")
            compose.onNodeWithContentDescription("設定").performClick()
            frame()
            checkpoint("weather: round $iteration appearance")
            compose.onNodeWithText("外觀與亮度").performClick()
            frame();compose.onNodeWithText("深色").assertIsDisplayed()
            checkpoint("weather: round $iteration return")
            compose.onNodeWithContentDescription("返回").performClick()
            frame()
            compose.onNodeWithContentDescription("天氣").performClick()
            frame()
            centerWeatherText("現在最重要的天氣")
            assertPaintedText("現在最重要的天氣")
        }
        checkpoint("weather: capture final screen")
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        val dir=File(context.getExternalFilesDir(null),"ui-regression").apply {mkdirs()}
        File(dir,"weather-after-scroll-and-settings.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        assertNull(CrashDiagnostics.graphics(context))
        checkpoint("weather: passed")
    }

    private fun centerWeatherText(text:String) {
        frame()
        val node=compose.onNodeWithText(text)
        val scroll=compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            hasAnyDescendant(hasText(text)))
        // performScrollTo loops until the target is visible, without advancing a
        // paused animation clock. Drive each asynchronous ScrollBy explicitly.
        repeat(8) {attempt ->
            val target=node.fetchSemanticsNode()
            val container=scroll.fetchSemanticsNode()
            val range=container.config[SemanticsProperties.VerticalScrollAxisRange]
            val before=range.value()
            val viewport=container.boundsInRoot
            val delta=(target.positionInRoot.y-viewport.top-viewport.height*.25f)
                .coerceIn(-before,(range.maxValue()-before).coerceAtLeast(0f))
            if(abs(delta)<1f) {node.assertIsDisplayed();return}
            checkpoint("scroll: $text attempt $attempt from $before by $delta")
            scroll.performSemanticsAction(SemanticsActions.ScrollBy) {
                assertTrue("ScrollBy rejected $text",it(0f,delta))
            }
            frame()
            val after=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("Scrolling $text stalled with the manual clock: $before -> $after",abs(after-before)>.5f)
        }
        fail("Could not center $text after 8 clock-driven scrolls")
    }
    private fun assertPaintedText(text:String) {
        val node=compose.onNodeWithText(text).assertIsDisplayed()
        val pixels=node.captureToImage().toPixelMap()
        var white=0
        for(y in 0 until pixels.height)for(x in 0 until pixels.width) {
            val c=pixels[x,y]
            if(c.red>.8f && c.green>.8f && c.blue>.8f)white++
        }
        assertTrue("$text exists in semantics but was covered by a drawn backdrop",white>pixels.width*pixels.height*.01f)
    }
}
