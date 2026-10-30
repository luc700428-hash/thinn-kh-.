package io.atmos.weather.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.atmos.weather.alarm.WeatherAlarmPage
import io.atmos.weather.alerts.*
import io.atmos.weather.domain.model.*
import io.atmos.weather.insights.WeatherInsightsPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration
import java.time.Instant

// Robolectric recreates the Android main Looper between test methods. A separate
// class/process per interactive scenario prevents a cached Compose dispatcher from
// targeting a disposed Looper. All five scenarios remain active in the full suite.
abstract class WeatherInformationRenderingFixture {
    protected open val windowWidth:Int=400
    protected open val windowHeight:Int=1100
    protected val now=Instant.parse("2026-10-28T11:33:30Z")
    protected fun resource(name:String)=javaClass.getResourceAsStream("/weather-info/$name")!!.use{it.readBytes()}
    protected fun weather()=WeatherSnapshot(SampleCities.all.first(),now,now,"test",24.0,22.0,83,35.0,1012.0,WeatherCondition.RAIN,false,null,null,
        (1..24).map{HourlyWeather(now.plusSeconds(it*3600L),22.0,63,0.0,humidityPct=83,windKmh=35.0,weatherCode=51)},timeZoneId="Asia/Taipei")
    protected fun nodes(view:View):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    protected fun text(n:SemanticsNode,t:String)=n.config.contains(SemanticsProperties.Text)&&n.config[SemanticsProperties.Text].any{it.text==t}
    protected fun layout(n:SemanticsNode):TextLayoutResult {
        val results=mutableListOf<TextLayoutResult>();assertTrue(n.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results));return results.first()
    }
    protected fun contrast(node:SemanticsNode,background:Color) {
        val ink=layout(node).layoutInput.style.color.compositeOver(background).luminance();val bg=background.luminance()
        assertTrue("Unreadable ${node.config}",(maxOf(ink,bg)+.05f)/(minOf(ink,bg)+.05f)>=4.5f)
    }
    protected fun pump(view:View):Bitmap {
        repeat(3){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            view.measure(View.MeasureSpec.makeMeasureSpec(windowWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(windowHeight,View.MeasureSpec.EXACTLY));view.layout(0,0,windowWidth,windowHeight)}
        return Bitmap.createBitmap(windowWidth,windowHeight,Bitmap.Config.ARGB_8888).also{view.draw(Canvas(it))}
    }
    protected fun save(view:View,name:String) {
        val b=pump(view);val dir=File("build/weather-info-renders").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    protected fun settle(view:View,condition:()->Boolean) {
        repeat(100) {
            pump(view).recycle()
            if(condition())return
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000)
        }
        assertTrue("Native Compose state did not settle",condition())
    }
    protected fun click(view:View,label:String) {
        val n=nodes(view).single{text(it,label)&&it.config.contains(SemanticsActions.OnClick)}
        assertTrue(n.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
    }
    protected fun render(dark:Boolean,scale:Float,name:String,content:@Composable ()->Unit,check:(View,ColorScheme)->Unit) {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("thinn_settings",0).edit().clear().putBoolean("reduce_motion",true).putBoolean("reduce_transparency",true)
            .putBoolean("sound_enabled",false).putFloat("v4_fontscale",scale).putFloat("v4_fontweight",400f).commit()
        IOS27AppearanceMode.update(c,if(dark)"dark" else "light")
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup();val a=controller.get();val view=ComposeView(a)
        var palette:ColorScheme?=null
        try {
            view.setContent{ThinnAppTheme{val current=MaterialTheme.colorScheme;SideEffect{palette=current};content()}}
            val parent=FrameLayout(a).apply{addView(view,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))};a.setContentView(parent)
            pump(parent).recycle();check(parent,palette!!);save(parent,"$name-$dark-${(scale*100).toInt()}")
        }finally{view.disposeComposition();controller.pause().stop().destroy()}
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherInformationRadarRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun radarDisplaysActualOfficialImageAndRetryInBothWeatherMaterials() {
        val radar=RadarImage(CwaRadarObservation.decode(resource("cwa-radar-octet-stream.png"))!!,now.toEpochMilli(),now.toEpochMilli(),now.minusSeconds(810).toEpochMilli())
        for(dark in listOf(false,true))render(dark,1f,"radar",{
            CompositionLocalProvider(LocalWeatherCardPalette provides rememberWeatherCardPalette()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)){RadarThreeHourContent(weather(),radar,false,{}, {},now)}
            }
        }){view,_->
            assertTrue(nodes(view).any{text(it,"中央氣象署 · 10/28 19:20 觀測")})
            assertTrue(nodes(view).none{text(it,"暫時無法取得雷達圖資；預報仍可獨立使用。")})
            val image=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&it.config[SemanticsProperties.ContentDescription].any{s->s.startsWith("中央氣象署雷達回波圖")}}
            assertTrue(image.boundsInRoot.width>=320)
        }
        var retried=false;var history=false
        render(false,1.5f,"radar-retry",{RadarThreeHourContent(weather(),null,false,{retried=true},{history=true},now)}){view,_->
            click(view,"重新取得觀測圖");assertTrue(retried)
            click(view,"在 App 內操作降雨雷達");assertTrue(history)
        }
        radar.bitmap.recycle()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherInformationAlertsRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun completeAlertTextAreasAndActionsRemainReadableInBothThemesAndReadingSizes() {
        val b=CwaBulletins.parse(String(resource("cwa-bulletins.json"),Charsets.UTF_8),now).single()
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            var opened=false
            render(dark,scale,"warning",{CwaAlertsContent(CwaBulletinsResult(listOf(b),now),emptyList(),emptyList(),false,b.title,{}, {}, {},{opened=true},now)}){view,p->
                val title=nodes(view).single{text(it,b.title)};contrast(title,p.surface)
                val body=nodes(view).single{text(it,b.text)};contrast(body,p.surface)
                assertFalse(layout(body).hasVisualOverflow)
                assertEquals(scale,layout(title).layoutInput.density.fontScale,.001f)
                assertTrue(nodes(view).any{text(it,b.areas.joinToString("、"))})
                click(view,"在 App 內查看官方圖表與完整頁面");assertTrue(opened)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherInformationControlsRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun sharedChoicesAndTimeFormsExposeVisibleSelectionsAndReadableDayNightInk() {
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            val selected=mutableIntStateOf(1);val checked=mutableStateOf(true);val show=mutableStateOf(false);val minutes=mutableIntStateOf(15)
            render(dark,scale,"controls",{
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    GlassChoiceGroup(listOf("怕冷","一般","怕熱"),selected.intValue,{selected.intValue=it})
                    GlassWeekdayChoices(listOf("一","二","三","四","五","六","日"),if(checked.value)setOf(0) else emptySet(),{if(it==0)checked.value=!checked.value})
                    GlassButton({show.value=true}){AppText("選擇時間")}
                    if(show.value)IOSFormSheet("更新頻率",{show.value=false},onDone={show.value=false}){IOSDurationDrums(minutes.intValue,{minutes.intValue=it})}
                }
            }){view,p->
                val choices=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.RadioButton}
                assertEquals(1,choices.count{it.config[SemanticsProperties.Selected]})
                contrast(choices.single{it.config[SemanticsProperties.Selected]},Color(0xFF0866CC))
                choices.filterNot{it.config[SemanticsProperties.Selected]}.forEach{contrast(it,p.surfaceVariant)}
                click(view,"怕熱");assertEquals(2,selected.intValue)
                click(view,"一");assertFalse(checked.value)
                settle(view){nodes(view).any{text(it,"一") && it.config.contains(SemanticsProperties.ToggleableState) && it.config[SemanticsProperties.ToggleableState]==ToggleableState.Off}}
                val days=nodes(view).filter{it.config.contains(SemanticsProperties.ToggleableState)}
                assertEquals(7,days.size);assertTrue(days.all{it.boundsInRoot.left>=0 && it.boundsInRoot.right<=400})
                val check=days.single{text(it,"一")}
                assertEquals(ToggleableState.Off,check.config[SemanticsProperties.ToggleableState])
                val buttonText=nodes(view).single{text(it,"選擇時間")};contrast(buttonText,p.primary)
                click(view,"選擇時間")
                settle(view){ShadowDialog.getLatestDialog()?.isShowing==true}
                val dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing)
                val sheet=dialog.window!!.decorView;pump(sheet).recycle()
                contrast(nodes(sheet).single{text(it,"更新頻率")},p.background)
                assertEquals(scale,layout(nodes(sheet).single{text(it,"更新頻率")}).layoutInput.density.fontScale,.001f)
                val drum=nodes(sheet).single{it.config.contains(SemanticsActions.SetProgress)}
                assertTrue(drum.config[SemanticsActions.SetProgress].action!!.invoke(20f));pump(sheet).recycle();assertEquals(21,minutes.intValue)
                save(sheet,"time-form-$dark-${(scale*100).toInt()}")
                val done=nodes(sheet).single{it.config.contains(SemanticsProperties.ContentDescription)&&"確認" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
                assertTrue(done.config[SemanticsActions.OnClick].action!!.invoke());pump(sheet).recycle();assertFalse(show.value)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherInformationAlarmRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun alarmPageAndEditorFollowTheSelectedAppearance() {
        for(dark in listOf(false,true))render(dark,1.5f,"alarms",{WeatherAlarmPage{}}){view,p->
            contrast(nodes(view).single{text(it,"天氣鬧鐘")},p.background)
            val add=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&"新增鬧鐘" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
            assertTrue(add.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
            settle(view){ShadowDialog.getLatestDialog()?.isShowing==true}
            val editor=ShadowDialog.getLatestDialog();val sheet=editor.window!!.decorView;pump(sheet).recycle()
            contrast(nodes(sheet).single{text(it,"新增鬧鐘")},p.background)
            assertEquals(1.5f,layout(nodes(sheet).single{text(it,"新增鬧鐘")}).layoutInput.density.fontScale,.001f)
            contrast(nodes(sheet).single{text(it,"重複")},p.surface)
            save(sheet,"alarm-editor-$dark-150")
            editor.dismiss()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherInformationInsightsRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun insightTabsKeepSelectionAndShowActualWindAndPollutantUnits() {
        val at=Instant.now()
        val w=weather().copy(observedAt=at,fetchedAt=at,windKmh=12.0,windGustKmh=31.0,pm25=20.0,usAqi=95,
            hourly=(0..23).map{HourlyWeather(at.plusSeconds(it*3600L),22.0,20,1.0,humidityPct=70,windKmh=10.0,weatherCode=2,feelsLikeC=23.0)})
        for(dark in listOf(false,true))render(dark,1.5f,"insights",{WeatherInsightsPage(w,false){}}){view,p->
            assertTrue(nodes(view).any{text(it,"未來 24 小時 · 三小時時段比較")})
            click(view,"風與濕度")
            settle(view){nodes(view).any{text(it,"風況與能見度")}}
            assertEquals(1,nodes(view).count{it.config.contains(SemanticsProperties.Selected)&&it.config[SemanticsProperties.Selected]})
            contrast(nodes(view).single{text(it,"31.0 km/h")},p.surface)
            save(view,"insights-wind-$dark-150")
            click(view,"空氣成分")
            settle(view){nodes(view).any{text(it,"PM₂.₅")}}
            contrast(nodes(view).single{text(it,"20.0 μg/m³")},p.surface)
            assertTrue(nodes(view).any{text(it,"模式估算 · 非環境部官方測站 AQI")})
            save(view,"insights-air-$dark-150")
            click(view,"外出時段")
            settle(view){nodes(view).any{text(it,"晾衣")}}
            click(view,"晾衣")
            settle(view){nodes(view).any{text(it,"最高相對濕度")}}
        }
    }
}
