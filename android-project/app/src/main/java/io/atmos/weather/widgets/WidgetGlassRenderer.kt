package io.atmos.weather.widgets

import io.atmos.weather.ui.AppThemeColors
import io.atmos.weather.ui.BrandPalette
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import android.graphics.*
/**
 * Widget colour roles and the launcher card behind them.
 * Launcher wallpaper cannot be sampled by an ordinary widget; see [WidgetLiquidGlass].
 */
object WidgetGlassRenderer {
 data class Palette(val top:Int,val bottom:Int,val ink:Int,val secondary:Int,val accent:Int)
 fun palette(index:Int,alert:Boolean=false,dark:Boolean=false)=paletteFor(AppThemeColors.saved,index,alert,dark)
 internal fun paletteFor(theme:io.atmos.weather.ui.AppThemeColor,index:Int,alert:Boolean=false,dark:Boolean=false):Palette {
  if(alert)return Palette(0xFF96323D.toInt(),0xFF541E32.toInt(),Color.WHITE,0xFFFFE3E7.toInt(),0xFFFFD3A3.toInt())
  val p=BrandPalette.scheme(theme,false);val pastel=ComposeColor(theme.swatchArgb)
  val strength=listOf(.24f,.38f,.14f,.52f,.30f)[Math.floorMod(index,5)]
  if(dark) {
   // Smoked glass over a dark wallpaper: white ink, with the theme only tinting the body.
   val smoke=ComposeColor(0xFF101618)
   return Palette(lerp(smoke,pastel,.10f+strength*.10f).toArgb(),lerp(smoke,pastel,.03f+strength*.06f).toArgb(),
    Color.WHITE,0xD6FFFFFF.toInt(),lerp(pastel,ComposeColor.White,.25f).toArgb())
  }
  return Palette(lerp(ComposeColor.White,pastel,strength*.45f).toArgb(),lerp(ComposeColor.White,pastel,strength).toArgb(),
   p.onSurface.toArgb(),p.onSurfaceVariant.toArgb(),p.primary.toArgb())
 }

 fun background(width:Int,height:Int,index:Int,edge:Float,opacity:Float,alert:Boolean=false,cornerRadiusPx:Float=minOf(width,height)*.16f,
  tone:WidgetGlassTone.Tone=WidgetGlassTone.FALLBACK):Bitmap{
  val scale=minOf(1f,640f/width.coerceAtLeast(1),640f/height.coerceAtLeast(1))
  val w=(width*scale).toInt().coerceIn(1,640);val h=(height*scale).toInt().coerceIn(1,640)
  val palette=palette(index,alert,tone.dark)
  val radius=(cornerRadiusPx.takeIf{it.isFinite()}?:28f).coerceAtLeast(0f)*scale
  // A recent-earthquake card stays a solid warning; every other card is translucent glass.
  return WidgetLiquidGlass.render(w,h,radius,palette.top,palette.bottom,palette.accent,alert||tone.dark,
   alert||tone.knownBackdrop,if(alert)1f else opacity,edge)
 }
}
