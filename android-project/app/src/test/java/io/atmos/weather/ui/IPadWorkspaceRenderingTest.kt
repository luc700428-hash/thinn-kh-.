package io.atmos.weather.ui

import android.app.Application
import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.work.*
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.*
import io.atmos.weather.presentation.home.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

private class FixtureWorker(c:Context,p:WorkerParameters):Worker(c,p){override fun doWork()=Result.success()}
internal fun initializeWorkspaceWorkers(context:Context) {
    WorkManager.initialize(context,Configuration.Builder().setWorkerFactory(object:WorkerFactory(){
        override fun createWorker(appContext:Context,workerClassName:String,workerParameters:WorkerParameters)=FixtureWorker(appContext,workerParameters)
    }).build())
}
abstract class IPadFixture:TabletRenderingFixture() {
    protected fun tapVisible(view:View,label:String) {
        repeat(10) {
            val targets=nodes(view).filter{text(it,label)&&it.config.contains(SemanticsActions.OnClick)}
            val hit=targets.firstOrNull{n->val b=n.boundsInRoot;b.width>0&&b.height>0&&b.left>=0&&b.right<=windowWidth+1&&b.top>=0&&b.bottom<=windowHeight+1}
            if(hit!=null){assertTrue(hit.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle();return}
            val target=targets.firstOrNull()?:error("Missing $label")
            val x=target.positionInRoot.x+8f
            val scroller=nodes(view).firstOrNull{it.config.contains(SemanticsActions.ScrollBy)&&x>=it.boundsInRoot.left&&x<=it.boundsInRoot.right}
                ?:error("Unreachable $label: ${target.boundsInRoot}")
            scroller.config[SemanticsActions.ScrollBy].action!!.invoke(0f,target.positionInRoot.y-windowHeight*.5f)
            pump(view).recycle()
        }
        error("Cannot bring $label into view")
    }
    protected fun noTextOnHinge(view:View,hinge:Rect) {
        nodes(view).filter{it.config.contains(SemanticsProperties.Text)}.forEach {
            val b=it.boundsInRoot
            if(b.width>0&&b.height>0&&b.left>=0&&b.top>=0&&b.bottom<=windowHeight&&b.right<=windowWidth)
                assertFalse("Text intersects physical hinge: ${it.config}",b.overlaps(hinge))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class IPadFoldPostureRenderingTest:IPadFixture() {
    @Test fun bookTabletopAndOuterScreenPreserveLiveControlsAndAvoidTheHinge() {
        var posture by mutableStateOf(DevicePosture(DeviceKind.TABLET));var tab by mutableIntStateOf(0);var starts=0
        render(false,1.5f,"ipad-postures",{
            CompositionLocalProvider(LocalDevicePosture provides posture) {
                TabletWorkspaceShell(tab,{tab=it},{},SampleCities.all.take(2),SampleCities.all.first(),{},"24° · 小雨") {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        LaunchedEffect(Unit){starts++}
                        var query by remember{mutableStateOf("嘉義")};var level by remember{mutableFloatStateOf(.5f)}
                        BasicTextField(query,{query=it},Modifier.fillMaxWidth().testTag("preserved-query"))
                        GlassSlider(level,{level=it},Modifier.testTag("preserved-slider"))
                        AppText("操作內容",Modifier.testTag("workspace-content"))
                    }
                }
            }
        }){view,_->
            assertTrue(tagged(view,"preserved-query").config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("阿里山")))
            assertTrue(tagged(view,"preserved-slider").config[SemanticsActions.SetProgress].action!!.invoke(.8f));pump(view).recycle()
            val cases=listOf(Triple(1000,900,WindowFold(Rect(480f,0f,520f,900f),true,true,true)),
                Triple(1000,1000,WindowFold(Rect(0f,460f,1000f,500f),false,true,true)),
                Triple(1000,900,WindowFold(Rect(820f,0f,860f,900f),true,true,true)),
                Triple(1000,900,WindowFold(Rect(100f,0f,140f,900f),true,true,true)),
                Triple(1000,900,WindowFold(Rect(0f,740f,1000f,780f),false,true,true)),
                Triple(400,900,null),Triple(768,1024,null),Triple(1280,800,null))
            for((w,h,fold) in cases) {
                posture=DevicePosture(DeviceKind.FOLDABLE,fold);resize(view,w,h)
                val bounds=tagged(view,"workspace-content").boundsInRoot;visible(bounds)
                if(fold!=null){noTextOnHinge(view,fold.bounds);if(fold.vertical)assertTrue(bounds.left>=fold.bounds.right||bounds.right<=fold.bounds.left) else assertTrue(bounds.top>=fold.bounds.bottom||bounds.bottom<=fold.bounds.top)}
                assertEquals("阿里山",tagged(view,"preserved-query").config[SemanticsProperties.EditableText].text)
                assertEquals(.8f,tagged(view,"preserved-slider").config[SemanticsProperties.ProgressBarRangeInfo].current,.01f)
                if(w==768) {
                    val menu=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&"開啟側邊欄" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
                    assertTrue(menu.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
                    tapVisible(view,"設定");assertEquals(3,tab)
                    settle(view){nodes(view).none{text(it,"我的城市")}}
                }
                save(view,"ipad-posture-${w}x$h")
            }
            assertEquals(1,starts)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class IPadRealAppNavigationRenderingTest:IPadFixture() {
    @Test fun realAppKeepsNavigationAndDetailsUsableThroughResizeAndFoldChanges() {
        installOfflineWorkspaceNetwork()
        val context=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(context)
        context.getSharedPreferences("thinn_settings",0).edit().putBoolean("check_updates_automatically",false).apply()
        val repo=object:WeatherDataRepository{override suspend fun load(city:WeatherCity,forceRefresh:Boolean)=WeatherLoadResult.Success(weather().copy(city=city),false,false)}
        val store=ViewModelStore();val vm=WeatherViewModel(repo,context);store.put("weather",vm)
        var posture by mutableStateOf(DevicePosture(DeviceKind.TABLET))
        try {
            render(false,1f,"ipad-real-app",{CompositionLocalProvider(LocalDevicePosture provides posture){
                AdaptiveWindowProvider{WeatherHomeScreen(vm,{})}
            }}){view,_->
                settle(view){nodes(view).any{it.config.contains(SemanticsProperties.Text)&&it.config[SemanticsProperties.Text].any{t->t.text.contains("現在的天氣")}}}
                save(view,"ipad-full-weather")
                tapVisible(view,"工具");tapVisible(view,"世界時鐘");save(view,"ipad-full-world-clock")
                tapVisible(view,"×");tapVisible(view,"海象與浪況");save(view,"ipad-full-marine")
                tapVisible(view,"日月與月相");save(view,"ipad-full-sky")
                assertFalse("Selecting the next detail must close the previous page",nodes(view).any{text(it,"海象與浪況")&&it.boundsInRoot.left>600&&it.boundsInRoot.top<100})
                tapVisible(view,"我的事項");assertTrue(nodes(view).any{text(it,"我的事項")});save(view,"ipad-full-tasks")
                tapVisible(view,"設定");tapVisible(view,"外觀與亮度");assertTrue(nodes(view).any{text(it,"外觀")});save(view,"ipad-full-appearance")
                tapVisible(view,"字體與閱讀");assertTrue(nodes(view).any{text(it,"字體大小")});save(view,"ipad-full-fonts")
                for((w,h) in listOf(768 to 1024,480 to 800,1280 to 800)) {
                    resize(view,w,h);assertTrue(nodes(view).any{text(it,"字體大小")});save(view,"ipad-full-resize-${w}x$h")
                }
                tapVisible(view,"天氣");save(view,"ipad-full-return-weather")
                posture=DevicePosture(DeviceKind.FOLDABLE,WindowFold(Rect(480f,0f,520f,900f),true,true,true));resize(view,1000,900)
                noTextOnHinge(view,posture.fold!!.bounds);save(view,"ipad-full-book")
                posture=DevicePosture(DeviceKind.FOLDABLE,WindowFold(Rect(0f,460f,1000f,500f),false,true,true));resize(view,1000,1000)
                noTextOnHinge(view,posture.fold!!.bounds);save(view,"ipad-full-tabletop")
                posture=DevicePosture(DeviceKind.FOLDABLE);resize(view,400,900);save(view,"ipad-full-outer-screen")
            }
        }finally{store.clear()}
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class IPadFoldModalRenderingTest:IPadFixture() {
    @Test fun editingSheetStaysInOnePhysicalPaneAndStillConfirms() {
        var posture by mutableStateOf(DevicePosture(DeviceKind.FOLDABLE,WindowFold(Rect(480f,0f,520f,900f),true,true,true)))
        windowWidth=1000;windowHeight=900;var confirmed=0
        render(true,1.5f,"ipad-fold-form",{CompositionLocalProvider(LocalDevicePosture provides posture){
            IOSFormSheet("時間與提醒",{},onDone={confirmed++}){AppText("提醒內容")}
        }}){_,_->
            var sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
            noTextOnHinge(sheet,posture.fold!!.bounds)
            assertTrue(nodes(sheet).single{text(it,"時間與提醒")}.boundsInRoot.left>=520)
            posture=DevicePosture(DeviceKind.FOLDABLE,WindowFold(Rect(0f,420f,1000f,460f),false,true,true));resize(sheet,1000,900)
            sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle();noTextOnHinge(sheet,posture.fold!!.bounds)
            val done=nodes(sheet).single{it.config.contains(SemanticsProperties.ContentDescription)&&"確認" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
            assertTrue(done.config[SemanticsActions.OnClick].action!!.invoke());settle(sheet){confirmed==1};assertEquals(1,confirmed);save(sheet,"ipad-fold-form-horizontal")
        }
    }
}
