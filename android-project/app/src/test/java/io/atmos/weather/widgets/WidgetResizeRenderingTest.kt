package io.atmos.weather.widgets

import android.app.Application
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import io.atmos.weather.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetResizeRenderingTest {
 @Test fun photoSizedWidgetsKeepTemperatureTimeAndDateVisibleAfterResize(){
  val renderer=WidgetRenderingTest();renderer.prepare()
  val reading=OpticalWidgetData("新北市 · 秀景里","21°",io.atmos.weather.domain.model.WeatherCondition.PARTLY_CLOUDY,true,System.currentTimeMillis(),
   WidgetPresentationRules.Freshness.FRESH,"30%","最高 24°  最低 17°","68%","2.8 m/s","32","3.2",emptyList(),
   "尚未設定鬧鐘",null,"沒有事項","無警報","",false)
  for(hour in listOf("12","24")) {
  android.provider.Settings.System.putString(org.robolectric.RuntimeEnvironment.getApplication().contentResolver,android.provider.Settings.System.TIME_12_24,hour)
  for(variant in listOf(27,28))for((w,h) in listOf(285 to 300,350 to 380))for(scale in listOf(1f,1.5f)){
   renderer.render(variant,w,h,scale,"photo-resize-${variant}-${w}x${h}-$scale-${hour}h",reading){view->
    val texts=descendants(view).filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE}
    assertTrue(texts.any{it.text.toString().startsWith("21°")})
    val dates=texts.filterIsInstance<TextClock>().filter{it.id==R.id.widget_clock_date||it.id==R.id.buddy_clock_date}
    assertEquals(1,dates.size)
    assertDatePainted(view,dates.single(),"Widget $variant ${w}x${h} $hour-hour")
    assertEquals(0,dates.single().layout.getEllipsisCount(0))
   }
  }
  }
 }
 private fun descendants(v:View):List<View> =listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{descendants(v.getChildAt(it))} else emptyList()
 private fun assertDatePainted(view:View,date:TextClock,label:String) {
  assertTrue("$label has no month/day",date.text.toString().matches(Regex("[0-9]{1,2}月[0-9]{1,2}日")))
  val bounds=Rect(0,0,date.width,date.height);(view as ViewGroup).offsetDescendantRectToMyCoords(date,bounds)
  assertTrue("$label has no visible date bounds: $bounds",bounds.width()>0&&bounds.height()>0&&Rect(0,0,view.width,view.height).contains(bounds))
  val rendered=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
  try {
   view.draw(android.graphics.Canvas(rendered))
   // TextClock can contain a valid date while horizontal text alignment paints it outside its clip.
   val ink=(bounds.top until bounds.bottom).sumOf{y->(bounds.left until bounds.right).count{x->android.graphics.Color.red(rendered.getPixel(x,y))<130}}
   assertTrue("$label date has no drawn glyphs: ${date.text}, bounds=$bounds, glyphs=$ink",ink>20)
  } finally {rendered.recycle()}
 }
 @Test fun enlargedWidgetsGrowTheirTypeAndTheSquareBuddyUsesBothColumns(){
  val renderer=WidgetRenderingTest();renderer.prepare()
  for(variant in listOf(0,5,10,15,26,27,28)){
   var base=0f;var baseClock=0f
   for(size in listOf(320,480))renderer.render(variant,size,size,1f,"resize-growth-${variant}-$size"){view->
    val children=descendants(view)
    val temperature=children.filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE&&(it.text.toString()=="24°"||it.id==R.id.buddy_clock_temp)}.maxOfOrNull{it.textSize}
    val time=children.filterIsInstance<TextClock>().filter{it.id==R.id.widget_clock||it.id==R.id.buddy_clock_time}.maxOfOrNull{it.textSize}
    if(size==320){base=temperature?:0f;baseClock=time?:0f}
    else{
     if(base>0f)assertTrue("Widget $variant kept its temperature at the old size",temperature!!>base*1.15f)
     if(baseClock>0f)assertTrue("Widget $variant kept its clock at the old size",time!!>baseClock*1.15f)
    }
    if(variant==27){
     val date=children.filterIsInstance<TextClock>().single{it.id==R.id.widget_clock_date}
     assertDatePainted(view,date,"Square buddy $size")
     val art=children.filterIsInstance<ImageView>().single{it.id==R.id.buddy_still}
     val bounds=Rect(0,0,art.width,art.height);(view as ViewGroup).offsetDescendantRectToMyCoords(art,bounds)
     assertTrue("Large square buddy left the right half empty",bounds.centerX()>view.width*.60f)
    }
   }
  }
 }
}
