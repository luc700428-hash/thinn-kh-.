package io.atmos.weather.ui
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
class SeismicSettingsRenderingTest:WeatherInformationRenderingFixture(){
    @Test fun allFourTabsRemainUnclippedSelectableAndScrubbableAtBothReadingSizesAndAppearances(){
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().clear().putBoolean("auto_start_realtime_on_app_open",false).commit()
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)){
            var visible by mutableStateOf(true)
            render(dark,scale,"seismic-settings-host",{if(visible)EarthquakeSettingsPage{visible=false}}){_,p->
                val dialog=ShadowDialog.getLatestDialog();val sheet=dialog.window!!.decorView;pump(sheet).recycle()
                for(label in listOf("一般","警報","音效","關於")){
                    val tab=nodes(sheet).single{text(it,label)&&it.config.contains(SemanticsActions.OnClick)}
                    assertFalse("Dock text clipped $label at $scale",layout(tab).hasVisualOverflow)
                    contrast(tab,p.surface)
                    click(sheet,label)
                    val chosen=nodes(sheet).single{text(it,label)&&it.config.contains(SemanticsProperties.Selected)}
                    assertTrue(chosen.config[SemanticsProperties.Selected])
                    save(sheet,"seismic-$label-$dark-${(scale*100).toInt()}")
                    if(label=="警報"){
                        assertTrue(nodes(sheet).any{text(it,"已選擇：3 級以上")})
                        assertTrue(nodes(sheet).any{text(it,"最低 1 級 · 預設 3 級。震度與規模 M 不同。")})
                    }
                    if(label=="音效")assertTrue(nodes(sheet).any{text(it,"固定音高的三聲電子提示，沒有音樂旋律。")})
                }
                val start=nodes(sheet).single{text(it,"一般")&&it.config.contains(SemanticsActions.OnClick)}.boundsInRoot.center
                val end=nodes(sheet).single{text(it,"音效")&&it.config.contains(SemanticsActions.OnClick)}.boundsInRoot.center
                val time=android.os.SystemClock.uptimeMillis()
                fun send(action:Int,x:Float,at:Long){val e=MotionEvent.obtain(time,at,action,x,start.y,0);sheet.dispatchTouchEvent(e);e.recycle()}
                send(MotionEvent.ACTION_DOWN,start.x,time)
                for(i in 1..10)send(MotionEvent.ACTION_MOVE,start.x+(end.x-start.x)*i/10,time+i*20)
                send(MotionEvent.ACTION_UP,end.x,time+220);pump(sheet).recycle()
                assertTrue(nodes(sheet).single{text(it,"音效")&&it.config.contains(SemanticsProperties.Selected)}.config[SemanticsProperties.Selected])
                dialog.dismiss()
            }
        }
    }
}
