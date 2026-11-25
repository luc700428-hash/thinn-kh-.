package io.atmos.weather.earthquake

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import io.atmos.weather.ui.IOS27AppearanceMode
import io.atmos.weather.ui.ThinnAppTheme
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
import java.io.File
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-xhdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicReportCardsRenderingTest {
    private val w=412*2; private val h=1600*2
    private fun nodes(view:View):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    private fun pump(view:View):Bitmap {
        repeat(4){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120))
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)}
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{view.draw(Canvas(it))}
    }
    private fun report(id:String,ago:Long,m:Double,loc:String,depth:Double,lat:Double,lon:Double,max:String,areas:List<Pair<String,String>>,number:String?=null)=
        EarthquakeReport(id,Instant.now().minusSeconds(ago),loc,m,depth,lat,lon,max+"級","",
            "https://scweb.cwa.gov.tw/zh-tw/earthquake/data",if(number==null)"小區域有感" else "顯著有感",areas.map{it.first to it.second+"級"},number=number)

    @Test fun latestReportLeadsAsACardAndEveryRowShowsItsCounties() {
        val reports=listOf(
            report("a",26*60,5.5,"臺東縣政府南南東方 101.9 公里 (位於臺灣東南部海域)",16.7,21.88,121.46,"4",
                listOf("臺東縣" to "4","屏東縣" to "2","高雄市" to "2","南投縣" to "1","嘉義市" to "1","嘉義縣" to "1","彰化縣" to "1","臺中市" to "1","臺南市" to "1","花蓮縣" to "1","雲林縣" to "1"),"115081"),
            report("b",18*3600,3.5,"臺東縣政府北方 20.1 公里 (位於臺東縣近海)",20.1,23.00,121.32,"2",listOf("臺東縣" to "2","花蓮縣" to "1")),
            report("c",25*3600,4.4,"宜蘭縣政府東方 60 公里 (位於臺灣東部海域)",12.6,24.75,122.32,"2",listOf("宜蘭縣" to "2","新北市" to "1","新竹縣" to "1","花蓮縣" to "1")),
            report("d",30*3600,3.4,"雲林縣政府東南方 30 公里 (位於雲林縣古坑鄉)",9.3,23.64,120.54,"3",listOf("雲林縣" to "3","南投縣" to "1","嘉義縣" to "1")))
        for(dark in listOf(true,false)) {
            val ctx=RuntimeEnvironment.getApplication()
            IOS27AppearanceMode.update(ctx,if(dark)"dark" else "light")
            val c=Robolectric.buildActivity(ComponentActivity::class.java).setup();val a=c.get();val view=ComposeView(a)
            try {
                view.setContent{ThinnAppTheme{Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)){
                    SeismicReportsPanel(reports,false,null,"中央氣象署",{},{})
                }}}
                val parent=FrameLayout(a).apply{addView(view,ViewGroup.LayoutParams(w,h))};a.setContentView(parent)
                val bmp=pump(parent)
                File("build/seismic-reports").apply{mkdirs()}.let{File(it,"reports-$dark.png").outputStream().use{s->bmp.compress(Bitmap.CompressFormat.PNG,100,s)}}
                val all=nodes(parent).filter{it.config.contains(SemanticsProperties.Text)}.joinToString("|"){it.config[SemanticsProperties.Text].joinToString("")}
                for(t in listOf("5.5","臺東縣政府南南東方","臺東 4","近期地震","雲林 3","分鐘前","第 115081 號","最大震度","✕ 震央 21.88, 121.46"))assertTrue(t,all.contains(t))
                nodes(parent).filter{it.config.contains(SemanticsProperties.Text)}.forEach{n->assertTrue("${n.config[SemanticsProperties.Text]} ${n.boundsInRoot}",n.boundsInRoot.right<=w+1f&&n.boundsInRoot.left>=-1f)}
            } finally {view.disposeComposition();c.pause().stop().destroy()}
        }
    }
}
