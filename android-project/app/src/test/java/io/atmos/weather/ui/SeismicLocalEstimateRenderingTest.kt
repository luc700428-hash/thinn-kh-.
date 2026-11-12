package io.atmos.weather.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import io.atmos.weather.earthquake.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicLocalEstimateRenderingTest:WeatherInformationRenderingFixture() {
    private var width=400;private var height=900
    override val windowWidth get()=width
    override val windowHeight get()=height

    @Test fun actualSourceShapeShowsLocalEstimateAndCountdownBeforeMapOnPhonesAndTablets() {
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        SeismicAlertDelivery.active.value=null
        SeismicUserLocation.choose(ctx,"嘉義市")
        val fixture=javaClass.getResourceAsStream("/eew/cwa-20261101-serial4.json")!!.bufferedReader().readText()
        val original=ExpTechRealtimeParser.parseEew(fixture).single()
        // Historical shape only; the fixture itself never enters the live delivery pipeline.
        for(size in listOf(400 to 900,1200 to 900))for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            width=size.first;height=size.second
            val clock=System.currentTimeMillis()
            val incident=original.copy(id="render-${width}-$dark-$scale",issuedAtMs=clock,originAtMs=clock-30_000)
            render(dark,scale,"local-eew-host-$width",{SeismicWarningOverlay(incident,false,"嘉義市",{})}){_,p->
                val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
                for(label in listOf("預估震度","預估抵達","本機初估","本機走時估算")) {
                    val node=nodes(sheet).single{text(it,label)}
                    assertTrue("Hidden $label at $width/$scale",node.boundsInRoot.height>0f)
                    assertFalse("Clipped $label",layout(node).hasVisualOverflow)
                    contrast(node,p.surface)
                }
                val grade=nodes(sheet).first{it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)&&
                    it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any{t->t.text.matches(Regex("[0-4] 級"))}}
                val countdown=nodes(sheet).first{it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)&&
                    it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any{t->t.text.matches(Regex("約 \\d+ s"))}}
                assertTrue(grade.boundsInRoot.height>0);assertTrue(countdown.boundsInRoot.height>0)
                assertFalse(layout(grade).hasVisualOverflow);assertFalse(layout(countdown).hasVisualOverflow)
                for(label in listOf("M 4.7","40 km")) {
                    val n=nodes(sheet).single{text(it,label)}
                    assertFalse(layout(n).hasVisualOverflow)
                }
                save(sheet,"local-eew-$width-$dark-${(scale*100).toInt()}")
            }
        }
        width=400;height=900
        val clock=System.currentTimeMillis()
        val strong=original.copy(id="render-high-pga",issuedAtMs=clock,originAtMs=clock-2_000,
            magnitude=7.3,depthKm=8.0,latitude=23.85,longitude=120.82,epicenter="南投縣集集鎮",maximumIntensity="7")
        render(true,1.5f,"high-pga-host",{SeismicWarningOverlay(strong,false,"嘉義市",{})}){_,p->
            val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
            val range=nodes(sheet).single{text(it,"4 級以上")}
            assertTrue(range.boundsInRoot.height>0);assertFalse(layout(range).hasVisualOverflow);contrast(range,p.surface)
            assertTrue(nodes(sheet).any{text(it,"本機初估")})
            save(sheet,"high-pga-range-150")
        }
    }
}
