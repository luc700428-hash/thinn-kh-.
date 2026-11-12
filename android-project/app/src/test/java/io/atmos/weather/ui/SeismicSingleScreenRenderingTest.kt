package io.atmos.weather.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.semantics.*
import io.atmos.weather.earthquake.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicSingleScreenRenderingTest:TabletDockFixture(){
    @Test fun drillAndLiveEssentialsFitShortPhonesLargeTextAndLandscapeWithoutScrolling(){
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        SeismicUserLocation.choose(ctx,"嘉義市");SeismicAlertDelivery.active.value=null
        for((w,h) in listOf(360 to 740,412 to 900,900 to 412))for(scale in listOf(1f,1.5f))for(drill in listOf(false,true))for(place in listOf("嘉義市",null)){
            SeismicUserLocation.choose(ctx,place)
            windowWidth=w;windowHeight=h
            val now=System.currentTimeMillis()
            val incident=if(drill)SeismicSimulation.scenario(now,true) else EewIncident("single-screen",1,"cwa",now,"花蓮縣近海",6.2,18.0,23.9,121.6,false,false,
                localIntensityByCounty=mapOf("嘉義市" to "3"),arrivalAtMsByCounty=mapOf("嘉義市" to now+60_000))
            render(drill,scale,"single-screen-$w-$drill",{SeismicWarningOverlay(incident,drill,place,{},simulation=drill)}){_,_->
                val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
                val summary=tagged(sheet,"seismic-warning-summary").boundsInRoot
                assertTrue(nodes(sheet).none{it.config.contains(SemanticsProperties.VerticalScrollAxisRange)})
                val labels=listOf("預估震度",if(drill)"演練示意倒數" else "預估抵達","規模","深度","趴下 · 掩護 · 穩住",if(drill)"結束模擬演練" else "我已採取避難措施")
                save(sheet,"single-screen-before-${w}x$h-$scale-$drill-${place?:"unknown"}")
                for(label in labels){
                    val node=nodes(sheet).single{text(it,label)}
                    val bounds=node.boundsInRoot
                    assertFalse("$w/$h/$scale clipped $label: ${layout(node).size} ${layout(node).layoutInput.constraints}, font=${layout(node).layoutInput.style}",layout(node).hasVisualOverflow)
                    assertTrue("$w/$h/$scale hid $label: $bounds vs $summary",bounds.width>0&&bounds.height>0&&bounds.top>=summary.top&&bounds.bottom<=summary.bottom+1)
                }
                val end=nodes(sheet).single{text(it,if(drill)"結束模擬演練" else "我已採取避難措施")}.boundsInRoot
                val details=nodes(sheet).single{text(it,if(drill)"歷史觀測與詳細資訊" else "地圖與詳細資訊")}.boundsInRoot
                assertTrue("Summary must not overlap its footer: $details / $end",details.bottom<end.top)
                save(sheet,"single-screen-${w}x$h-$scale-$drill-${place?:"unknown"}")
                ShadowDialog.getLatestDialog().dismiss()
            }
        }
    }
}
