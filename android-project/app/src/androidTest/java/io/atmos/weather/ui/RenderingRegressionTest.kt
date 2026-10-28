package io.atmos.weather.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
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
            .putBoolean("sound_enabled",false).putFloat("v4_fontscale",1f).putFloat("v4_fontweight",400f).commit()
        context.getSharedPreferences("thinn_crash_diagnostics",Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun frame() {compose.mainClock.advanceTimeBy(650);compose.waitForIdle()}
    private fun checkpoint(stage:String) {
        Log.i("ThinnUiRegression",stage)
        val dir=File(context.getExternalFilesDir(null),"ui-regression").apply {mkdirs()}
        File(dir,"progress.txt").appendText("${Instant.now()} $stage\n")
    }

    @Test(timeout=120_000) fun preferenceChoicesExposeOneSelectionInBothAppearances() {
        val selected=mutableIntStateOf(1)
        val dark=mutableStateOf(false)
        val weekday=mutableStateOf(true)
        compose.setContent {
            ThinnAppTheme {
                MaterialTheme(colorScheme=if(dark.value)darkColorScheme() else lightColorScheme()) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
                        Column(Modifier.width(320.dp)) {
                            GlassChoiceGroup(listOf("怕冷","一般","怕熱"),selected.intValue,{selected.intValue=it})
                            GlassToggleChoice("星期一",weekday.value,{weekday.value=!weekday.value})
                        }
                    }
                }
            }
        }
        for(appearance in listOf(false,true)) {
            compose.runOnIdle {dark.value=appearance;selected.intValue=1;weekday.value=true};frame()
            compose.onNodeWithText("星期一").assertIsOn().performClick();frame()
            compose.onNodeWithText("星期一").assertIsOff()
            compose.onNodeWithText("一般").assertIsSelected()
            compose.onNodeWithText("怕冷").assertIsNotSelected()
            compose.onNodeWithText("怕熱").performClick();frame()
            compose.onNodeWithText("怕熱").assertIsSelected()
            compose.onAllNodes(isSelected()).assertCountEquals(1)
            val active=compose.onNodeWithText("怕熱").captureToImage().toPixelMap()
            val inactive=compose.onNodeWithText("一般").captureToImage().toPixelMap()
            fun blueFraction(p:androidx.compose.ui.graphics.PixelMap):Float {
                var blue=0
                for(y in 0 until p.height)for(x in 0 until p.width) {
                    val c=p[x,y];if(c.blue>c.red+.25f&&c.blue>c.green+.1f)blue++
                }
                return blue.toFloat()/(p.width*p.height)
            }
            assertTrue("Selected choice lost its clear blue fill",blueFraction(active)>.35f)
            assertTrue("Unselected choice still looked selected",blueFraction(inactive)<.08f)
        }
        checkpoint("preference choices: passed light and dark, 150% text")
    }

    @Test(timeout=120_000) fun bubbleSliderIsTransparentAndKeepsDragAndAccessibilityValues() {
        checkpoint("bubble: compose transparent lens")
        val value=mutableFloatStateOf(50f)
        compose.setContent {
            ThinnAppTheme {
                Box(Modifier.width(320.dp).height(60.dp).testTag("bubble-background")) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Color.Magenta,size=Size(size.width/2,size.height))
                        drawRect(Color.Green,topLeft=Offset(size.width/2,0f),size=Size(size.width/2,size.height))
                    }
                    GlassSlider(value.floatValue,{value.floatValue=it},Modifier.align(Alignment.Center).testTag("bubble-slider"),
                        valueRange=0f..100f,steps=99)
                }
            }
        }
        frame()
        val slider=compose.onNodeWithTag("bubble-slider")
        slider.performSemanticsAction(SemanticsActions.SetProgress){assertTrue(it(37.4f))}
        frame();assertEquals(37f,value.floatValue,0f)
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width*.96f,centerY),delayMillis=300)
            up()
        }
        frame();assertTrue("Slider drag did not reach the end of its range",value.floatValue>=90f)
        slider.performSemanticsAction(SemanticsActions.SetProgress){it(50f)}
        frame()
        val image=compose.onNodeWithTag("bubble-background").captureToImage()
        val pixels=image.toPixelMap()
        val left=pixels[(pixels.width*152/320),(pixels.height*22/60)]
        val right=pixels[(pixels.width*168/320),(pixels.height*22/60)]
        assertTrue("Bubble hid the magenta scene behind an opaque fill",left.red>left.green+.25f)
        assertTrue("Bubble hid the green scene behind an opaque fill",right.green>right.red+.25f)
        val directory=File(context.getExternalFilesDir(null),"ui-regression").apply {mkdirs()}
        File(directory,"bubble-transparency.png").outputStream().use {image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        assertNull(CrashDiagnostics.graphics(context))
        checkpoint("bubble: passed")
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

    @Test(timeout=120_000) fun allToolsGroupsAndFilteredLastGroupRenderInBothAppearances() {
        val dark=mutableStateOf(false)
        compose.setContent {
            ThinnAppTheme {
                MaterialTheme(colorScheme=if(dark.value)darkColorScheme() else lightColorScheme()) {
                    AppGlassToolsPage(onBriefings={},onUpdates={},onDiagnostics={},onSeismic={})
                }
            }
        }
        for(appearance in listOf(false,true)) {
            compose.runOnIdle{dark.value=appearance};frame()
            // All groups compose, including the fourth group beyond the initial viewport.
            for(title in listOf("日常生活","天氣與防災","海洋與天文","時間與換算")) {
                compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            }
            compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("世界時鐘")
            frame()
            compose.onNode(hasSetTextAction()).assertTextEquals("世界時鐘")
            compose.onNodeWithText("時間與換算").assertIsDisplayed()
            // The same text is present in the editor; select the actionable result, not its input.
            val clockResult=hasText("世界時鐘") and hasClickAction() and !hasSetTextAction()
            compose.onAllNodes(clockResult).assertCountEquals(1)
            compose.onNode(clockResult).assertIsDisplayed()
            compose.onNodeWithText("海洋與天文").assertDoesNotExist()
            compose.onNode(clockResult).performClick();frame()
            compose.onNodeWithText("臺北").assertIsDisplayed()
            compose.onNodeWithText("東京").assertIsDisplayed()
            compose.onNodeWithText("×").performClick();frame()
            compose.onNodeWithText("臺北").assertDoesNotExist()
            compose.onNode(hasSetTextAction()).performTextClearance();frame()
            compose.onNodeWithText("海洋與天文").assertExists()
        }
        checkpoint("tools: all four groups and last-group search passed light and dark")
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
            // Switching the root Dock must also dismiss an open settings detail.
            if(iteration%2==1){compose.onNodeWithContentDescription("返回").performClick();frame()}
            compose.onNodeWithContentDescription("天氣").performClick()
            frame()
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeBy(32)
                compose.onAllNodesWithText("深色").fetchSemanticsNodes().isEmpty()
            }
            // Rapid requests must settle on the last destination.
            compose.onNodeWithContentDescription("工具").performClick()
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

    @Test(timeout=120_000) fun rapidAlternatingSliderDragsSurviveRecomposition() {
        val values=List(3){mutableFloatStateOf(.5f)}
        compose.setContent {ThinnAppTheme {Column(Modifier.width(320.dp)) {
            values.forEachIndexed {i,v->GlassSlider(v.floatValue,{v.floatValue=it},Modifier.testTag("stress-$i"))}
            AppText("值 ${values[0].floatValue}")
        }}}
        frame()
        repeat(60){i->
            val slider=compose.onNodeWithTag("stress-${i%3}")
            slider.performTouchInput {
                down(Offset(width*(if(i%2==0).02f else .98f),centerY))
                moveTo(center,delayMillis=80)
            }
            // Let the pressed lens actually lift before release; a frozen clock misses rebound bugs.
            compose.mainClock.advanceTimeBy(80);compose.waitForIdle()
            slider.performTouchInput {
                moveTo(Offset(width*(if(i%2==0).98f else .02f),centerY),delayMillis=80)
                up()
            }
            compose.mainClock.advanceTimeBy(32)
            if(i%6==0) {compose.mainClock.advanceTimeBy(240);compose.waitForIdle()}
            assertTrue(values.all{it.floatValue.isFinite()&&it.floatValue in 0f..1f})
        }
        frame();assertNull(CrashDiagnostics.graphics(context))
        checkpoint("sliders: 60 endpoint drags and release rebound passed")
    }

    @Test(timeout=120_000) fun textSizeAndWeightApplyOnceAcrossNestedThemes() {
        var result:androidx.compose.ui.text.TextLayoutResult?=null
        compose.setContent {ThinnAppTheme {GlassAppTheme {
            AppText("字體設定真的會生效",fontSize=20.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Normal,
                modifier=Modifier.testTag("reading-sample"),onTextLayout={result=it})
        }}}
        frame();val baseline=result!!.size.height
        compose.runOnIdle {context.getSharedPreferences("thinn_settings",0).edit().putFloat("v4_fontscale",1.5f).putFloat("v4_fontweight",800f).commit()}
        frame()
        val enlarged=result!!
        assertEquals(800,enlarged.layoutInput.style.fontWeight!!.weight)
        assertTrue("Font scale was ignored",enlarged.size.height>baseline*1.3f)
        assertTrue("Nested themes applied font scale twice",enlarged.size.height<baseline*1.7f)
        compose.onNodeWithTag("reading-sample").assertIsDisplayed()
        compose.runOnIdle {context.getSharedPreferences("thinn_settings",0).edit().putFloat("v4_fontscale",1f).putFloat("v4_fontweight",400f).commit()}
        frame();assertEquals(baseline,result!!.size.height)
        checkpoint("reading: global size and weight passed nested theme regression")
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
