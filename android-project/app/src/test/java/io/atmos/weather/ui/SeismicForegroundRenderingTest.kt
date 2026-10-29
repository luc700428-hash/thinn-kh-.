package io.atmos.weather.ui
import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
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
class SeismicForegroundRenderingTest:WeatherInformationRenderingFixture(){
    @Test fun qualifyingLiveIncidentInterruptsAnotherPageUsingRealCountyDataAndDismissesCleanly(){
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        SeismicUserLocation.choose(ctx,"臺北市")
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)){
            ctx.getSharedPreferences("thinn_seismic_delivery_v27",0).edit().clear().commit();SeismicAlertDelivery.active.value=null
            val incident=EewIncident("live-ui-$dark-$scale",4,"ExpTech EEW",System.currentTimeMillis(),"花蓮縣近海",6.2,18.0,23.9,121.6,false,false,
                originAtMs=System.currentTimeMillis()-3000,localIntensityByCounty=mapOf("臺北市" to "3","花蓮縣" to "5弱"),maximumIntensity="5弱")
            render(dark,scale,"eew-normal-host",{Column{AppText("天氣主頁");SeismicForegroundAlertHost()}}){view,p->
                assertTrue(nodes(view).any{text(it,"天氣主頁")})
                assertNotNull(SeismicAlertDelivery.publish(ctx,incident))
                settle(view){ShadowDialog.getLatestDialog()?.isShowing==true}
                val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
                for(label in listOf("緊急地震速報","收到地震速報 · 慎防強烈搖晃","3 級","M 6.2","18 km")){
                    val node=nodes(sheet).filter{text(it,label)}.minBy{it.boundsInRoot.top};assertFalse("Clipped $label at $scale",layout(node).hasVisualOverflow)
                    contrast(node,if(label in listOf("3 級","M 6.2","18 km"))p.surface else p.background)
                }
                assertTrue(nodes(sheet).any{text(it,"待來源提供")})
                assertTrue(nodes(sheet).any{text(it,"花蓮縣近海")})
                settle(sheet){nodes(sheet).any{text(it,"© 國土測繪中心 · 灰：未提供")}}
                save(sheet,"live-eew-$dark-${(scale*100).toInt()}")
                // Source-supplied past arrival changes the tile without inferring an ETA from wave rings.
                assertNotNull(SeismicAlertDelivery.publish(ctx,incident.copy(serial=5,arrivalAtMsByCounty=mapOf("臺北市" to System.currentTimeMillis()-1000))))
                settle(view){
                    val latest=ShadowDialog.getLatestDialog().window!!.decorView
                    pump(latest).recycle();nodes(latest).any{text(it,"已抵達")}
                }
                val updatedSheet=ShadowDialog.getLatestDialog().window!!.decorView
                val arrived=nodes(updatedSheet).single{text(it,"已抵達")}
                assertFalse(layout(arrived).hasVisualOverflow)
                assertTrue(nodes(updatedSheet).any{text(it,"第 5 報")})
                save(updatedSheet,"live-arrived-$dark-${(scale*100).toInt()}")
                click(updatedSheet,"我已採取避難措施");pump(view).recycle()
                assertNull(SeismicAlertDelivery.active.value)
                assertTrue(nodes(view).any{text(it,"天氣主頁")})
            }
        }
    }
}
