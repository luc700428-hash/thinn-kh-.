package io.atmos.weather.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AppThemeColor(val id:String,val title:String,val primaryArgb:Long) {
    FOREST("forest","墨綠",0xFF2F6858),
    OCEAN("ocean","海藍",0xFF366F95),
    LAVENDER("lavender","霧紫",0xFF76588F),
    ROSE("rose","玫瑰",0xFF96576B),
    AMBER("amber","琥珀",0xFF856238);

    companion object {
        fun fromId(value:String?)=entries.firstOrNull{it.id==value}?:FOREST
    }
}

/** One persisted setting for Compose, drawing canvases and headless widget workers. */
object AppThemeColors {
    const val KEY="app_theme_color_v1"
    var value by mutableStateOf(AppThemeColor.FOREST)
        private set
    private var bound:SharedPreferences?=null
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener {prefs,key->
        if(key==KEY||key==null)value=AppThemeColor.fromId(prefs.getString(KEY,null))
    }
    fun initialize(context:Context) {
        val prefs=context.applicationContext.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        if(bound!==prefs) {
            bound?.unregisterOnSharedPreferenceChangeListener(listener)
            bound=prefs
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }
        value=AppThemeColor.fromId(prefs.getString(KEY,null))
    }
    fun update(context:Context,selection:AppThemeColor) {
        initialize(context)
        value=selection
        bound!!.edit().putString(KEY,selection.id).apply()
    }
}

/** Only explicitly branded colors pass here; warning/observation colors never do. */
fun brandColor(greenArgb:Long)=Color(brandArgb(greenArgb))
fun brandArgb(greenArgb:Long)=ThemeColorTint.argb(greenArgb.toInt(),AppThemeColors.value)

/** Preserve each material's luminance and alpha, including very pale glass highlights. */
internal object ThemeColorTint {
    private val cache=AppThemeColor.entries.associateWith{ConcurrentHashMap<Int,Int>()}
    fun argb(base:Int,theme:AppThemeColor):Int {
        if(theme==AppThemeColor.FOREST)return base
        return cache.getValue(theme).getOrPut(base){convert(base,theme)}
    }
    private fun hue(r:Double,g:Double,b:Double):Double {
        val high=max(r,max(g,b));val low=min(r,min(g,b));val delta=high-low
        if(delta<.00001)return 0.0
        val sector=when(high){r->(g-b)/delta;g->(b-r)/delta+2;else->(r-g)/delta+4}
        return ((sector*60)%360+360)%360
    }
    private fun linear(v:Double)=if(v<=.04045)v/12.92 else Math.pow((v+.055)/1.055,2.4)
    private fun luminance(v:DoubleArray)=.2126*linear(v[0])+.7152*linear(v[1])+.0722*linear(v[2])
    private fun rgb(h:Double,s:Double,l:Double):DoubleArray {
        val c=(1-abs(2*l-1))*s;val x=c*(1-abs((h/60)%2-1));val m=l-c/2
        val a=when {h<60->doubleArrayOf(c,x,0.0);h<120->doubleArrayOf(x,c,0.0)
            h<180->doubleArrayOf(0.0,c,x);h<240->doubleArrayOf(0.0,x,c)
            h<300->doubleArrayOf(x,0.0,c);else->doubleArrayOf(c,0.0,x)}
        return DoubleArray(3){(a[it]+m).coerceIn(0.0,1.0)}
    }
    private fun convert(base:Int,theme:AppThemeColor):Int {
        if(base==0xFF2F6858.toInt())return theme.primaryArgb.toInt()
        val r=(base ushr 16 and 255)/255.0;val g=(base ushr 8 and 255)/255.0;val b=(base and 255)/255.0
        val high=max(r,max(g,b));val low=min(r,min(g,b));val delta=high-low
        if(delta<.00001)return base
        val l=(high+low)/2
        val saturation=(delta/(1-abs(2*l-1))).coerceIn(0.0,.55)
        val target=theme.primaryArgb.toInt()
        val targetHue=hue((target ushr 16 and 255)/255.0,(target ushr 8 and 255)/255.0,(target and 255)/255.0)
        val offset=(hue(r,g,b)-163.16).coerceIn(-8.0,8.0)
        val h=(targetHue+offset+360)%360
        val y=luminance(doubleArrayOf(r,g,b));var a=0.0;var z=1.0
        repeat(25){val m=(a+z)/2;if(luminance(rgb(h,saturation,m))<y)a=m else z=m}
        val result=rgb(h,saturation,(a+z)/2).map{(it*255).roundToInt().coerceIn(0,255)}
        return (base and 0xFF000000.toInt()) or (result[0] shl 16) or (result[1] shl 8) or result[2]
    }
}
