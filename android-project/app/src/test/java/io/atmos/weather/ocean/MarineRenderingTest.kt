package io.atmos.weather.ocean

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import io.atmos.weather.domain.model.SampleCities
import io.atmos.weather.ui.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MarineRenderingTest {
    private fun nodes(view:View):List<SemanticsNode> {
        fun walk(node:SemanticsNode):List<SemanticsNode> = listOf(node)+node.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    private fun text(node:SemanticsNode,value:String)=node.config.contains(SemanticsProperties.Text) &&
        node.config[SemanticsProperties.Text].any{it.text==value}
    private fun layout(node:SemanticsNode):TextLayoutResult {
        val layouts=mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
        return layouts.first()
    }
    private fun contrast(node:SemanticsNode,background:Color) {
        val ink=layout(node).layoutInput.style.color.compositeOver(background)
        val a=ink.luminance();val b=background.luminance()
        assertTrue("Low text contrast: ${node.config}",(maxOf(a,b)+.05f)/(minOf(a,b)+.05f)>=4.5f)
    }
    private fun pump(view:View):Bitmap {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
            view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(850,View.MeasureSpec.EXACTLY))
            view.layout(0,0,400,850)
        }
        return Bitmap.createBitmap(400,850,Bitmap.Config.ARGB_8888).also{view.draw(Canvas(it))}
    }
    private fun save(bitmap:Bitmap,name:String) {
        val dir=File("build/marine-renders").apply{mkdirs()}
        File(dir,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun readableCenteredMarineAndSelectableRegionsInLightDarkAndLargeText() {
        val context=RuntimeEnvironment.getApplication()
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            context.getSharedPreferences("thinn_settings",0).edit().clear()
                .putBoolean("reduce_motion",true).putBoolean("reduce_transparency",false)
                .putFloat("v4_fontscale",scale).putFloat("v4_fontweight",400f).commit()
            IOS27AppearanceMode.update(context,if(dark)"dark" else "light")
            val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity=controller.get();val view=ComposeView(activity)
            val loaded=mutableListOf<Pair<String,Boolean>>()
            var closed=false
            val load:suspend (SeaPlace,Boolean)->MarineSnapshot={place,force->
                loaded.add(place.id to force)
                val now=Instant.now()
                val hour=MarineHour(now,1.9,45.0,7.0,1.0,60.0,9.0,.4,90.0,24.0)
                MarineSnapshot(place,place.latitude,place.longitude,hour,listOf(hour.copy(at=now.plusSeconds(3600))),now)
            }
            try {
                view.setContent {ThinnAppTheme {
                    // A detail opened from Home must not inherit its blue weather-card material.
                    CompositionLocalProvider(LocalWeatherCardPalette provides WeatherCardPalettes.day) {
                        MarineScreen(SampleCities.all.first(),{closed=true},load)
                    }
                }}
                val parent=FrameLayout(activity).apply{addView(view,ViewGroup.LayoutParams(400,850))}
                activity.setContentView(parent)
                val label="${if(dark)"dark" else "light"}_${(scale*100).toInt()}"
                save(pump(parent),"marine_$label.png")
                assertEquals("tamsui",loaded.first().first)
                val page=nodes(parent)
                val title=page.single{text(it,"海象與浪況")}
                assertEquals("Title shifted away from screen center",200f,title.boundsInRoot.center.x,1f)
                assertEquals(scale,layout(title).layoutInput.density.fontScale,.001f)
                val surface=if(dark)Color(0xFF1C1C1E) else Color.White
                contrast(page.single{text(it,"近海模式預報 · 海域與陸地氣溫分開計算")},surface)
                val action=page.single{text(it,"淡水外海") && it.config.contains(SemanticsActions.OnClick)}
                contrast(action,surface)
                assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
                pump(parent)
                val dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing)
                val sheet=dialog.window!!.decorView
                val shot=pump(sheet);save(shot,"regions_$label.png")
                val options=nodes(sheet)
                val sheetTitle=options.single{text(it,"選擇海域")}
                contrast(sheetTitle,surface)
                assertEquals("Sheet ignored the app reading size",scale,layout(sheetTitle).layoutInput.density.fontScale,.001f)
                val rows=options.filter{it.config.contains(SemanticsProperties.Role) && it.config[SemanticsProperties.Role]==Role.RadioButton}
                assertTrue("Region list was empty",rows.size>=2)
                assertEquals(1,rows.count{it.config[SemanticsProperties.Selected]})
                for(row in rows)contrast(row,if(row.config[SemanticsProperties.Selected])
                    if(dark)Color(0xFF163C56) else Color(0xFFE2F0FF) else surface)
                val target=rows.single{text(it,"基隆外海")}
                assertTrue(target.config[SemanticsActions.OnClick].action!!.invoke())
                pump(parent);assertFalse(dialog.isShowing)
                assertEquals("keelung",loaded.last().first)
                assertTrue(nodes(parent).any{text(it,"基隆外海")})
                val refresh=nodes(parent).single{it.config.contains(SemanticsProperties.ContentDescription) &&
                    "更新海象預報" in it.config[SemanticsProperties.ContentDescription] && it.config.contains(SemanticsActions.OnClick)}
                assertTrue(refresh.config[SemanticsActions.OnClick].action!!.invoke())
                pump(parent);assertEquals("keelung" to true,loaded.last())
                val back=nodes(parent).single{it.config.contains(SemanticsProperties.ContentDescription) &&
                    "返回" in it.config[SemanticsProperties.ContentDescription] && it.config.contains(SemanticsActions.OnClick)}
                assertTrue(back.config[SemanticsActions.OnClick].action!!.invoke());assertTrue(closed)
            } finally {
                view.disposeComposition();controller.pause().stop().destroy()
            }
        }
    }
}
