package io.atmos.weather.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import io.atmos.weather.domain.model.WeatherCondition

/**
 * iOS 27 Home Screen widget appearances, rebuilt for thinn-khì.
 *
 * The five colour slots of every widget family map onto the appearances iOS offers:
 *  0 預設  – the live sky of the current weather, as the Weather app widget paints it;
 *  1 深色  – the same sky dimmed into the dark Home Screen;
 *  2 清透  – clear glass over the wallpaper (light or dark from the wallpaper colours);
 *  3 染色  – monochrome glass tinted with the app theme;
 *  4 清透深色 – clear glass that always stays smoked.
 * The sky follows condition and day/night, so the card itself tells the weather at a glance.
 */
object IOSWidgetSky {
    enum class Style { SKY, DARK, CLEAR, TINTED, CLEAR_DARK;
        companion object { fun of(index:Int)=entries[Math.floorMod(index,entries.size)] }
    }

    /** Top and bottom of the sky, iOS Weather style. */
    fun sky(condition:WeatherCondition,day:Boolean):Pair<Int,Int> = if(day) when(condition) {
        WeatherCondition.CLEAR->0xFF2F7FD8.toInt() to 0xFF6DB3EE.toInt()
        WeatherCondition.PARTLY_CLOUDY->0xFF3F77B4.toInt() to 0xFF7DA6CF.toInt()
        WeatherCondition.CLOUDY->0xFF5F7489.toInt() to 0xFF8C9DAE.toInt()
        WeatherCondition.RAIN,WeatherCondition.FREEZING_RAIN,WeatherCondition.SLEET->0xFF46576A.toInt() to 0xFF6E7D8C.toInt()
        WeatherCondition.THUNDERSTORM,WeatherCondition.HAIL->0xFF343B48.toInt() to 0xFF575E6C.toInt()
        WeatherCondition.SNOW->0xFF7F95AC.toInt() to 0xFFAFBFCE.toInt()
        WeatherCondition.FOG,WeatherCondition.HAZE,WeatherCondition.DUST,WeatherCondition.DUST_STORM->0xFF7E838A.toInt() to 0xFFA7AAAF.toInt()
        else->0xFF3C5068.toInt() to 0xFF5E7187.toInt()
    } else when(condition) {
        WeatherCondition.CLEAR,WeatherCondition.PARTLY_CLOUDY->0xFF0A1530.toInt() to 0xFF243561.toInt()
        WeatherCondition.CLOUDY,WeatherCondition.FOG,WeatherCondition.HAZE,WeatherCondition.DUST,WeatherCondition.DUST_STORM->0xFF1B222D.toInt() to 0xFF353E4C.toInt()
        WeatherCondition.RAIN,WeatherCondition.FREEZING_RAIN,WeatherCondition.SLEET,WeatherCondition.THUNDERSTORM,WeatherCondition.HAIL->0xFF131A24.toInt() to 0xFF2A3341.toInt()
        WeatherCondition.SNOW->0xFF1E2A3C.toInt() to 0xFF3D4B60.toInt()
        else->0xFF141C2A.toInt() to 0xFF2C3646.toInt()
    }

    /** Ink on the sky and dark styles is always white, as on iOS. */
    fun palette(style:Style,condition:WeatherCondition,day:Boolean,themeAccent:Int):WidgetGlassRenderer.Palette {
        val (top,bottom)=sky(condition,day)
        return when(style) {
            Style.SKY->WidgetGlassRenderer.Palette(top,bottom,android.graphics.Color.WHITE,0xD9FFFFFF.toInt(),
                if(day)0xFFFFD60A.toInt() else 0xFFB9C8FF.toInt())
            Style.DARK->WidgetGlassRenderer.Palette(mix(0xFF1C1C1E.toInt(),top,.22f),mix(0xFF1C1C1E.toInt(),bottom,.12f),
                android.graphics.Color.WHITE,0xB3FFFFFF.toInt(),mix(themeAccent,android.graphics.Color.WHITE,.35f))
            Style.TINTED->WidgetGlassRenderer.Palette(mix(0xFF121416.toInt(),themeAccent,.38f),mix(0xFF121416.toInt(),themeAccent,.20f),
                mix(android.graphics.Color.WHITE,themeAccent,.12f),mix(0xCCFFFFFF.toInt(),themeAccent,.25f),mix(themeAccent,android.graphics.Color.WHITE,.45f))
            else->error("Clear styles use the glass renderer")
        }
    }

    /** Opaque card with transparent rounded corners; HyperOS also needs a non-transparent root. */
    fun render(width:Int,height:Int,cornerRadiusPx:Float,style:Style,palette:WidgetGlassRenderer.Palette,
        condition:WeatherCondition,day:Boolean):Bitmap {
        val scale=minOf(1f,640f/width.coerceAtLeast(1),640f/height.coerceAtLeast(1))
        val w=(width*scale).toInt().coerceIn(1,640);val h=(height*scale).toInt().coerceIn(1,640)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap)
        val wf=w.toFloat();val hf=h.toFloat()
        val radius=((cornerRadiusPx.takeIf{it.isFinite()}?:28f)*scale).coerceIn(0f,minOf(wf,hf)/2f)
        val rect=RectF(0f,0f,wf,hf)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,0f,hf,palette.top,palette.bottom,Shader.TileMode.CLAMP)
        c.drawRoundRect(rect,radius,radius,p)
        c.save();c.clipPath(Path().apply{addRoundRect(rect,radius,radius,Path.Direction.CW)})
        if(style==Style.SKY) {
            when {
                day&&(condition==WeatherCondition.CLEAR||condition==WeatherCondition.PARTLY_CLOUDY)->{
                    // Sunlight pooling in the upper corner.
                    p.shader=RadialGradient(wf*.86f,hf*.02f,maxOf(wf,hf)*.75f,intArrayOf(0x55FFF6D5,0x18FFFFFF,0x00FFFFFF),
                        floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP);c.drawRect(rect,p)
                }
                !day&&(condition==WeatherCondition.CLEAR||condition==WeatherCondition.PARTLY_CLOUDY)->{
                    // A few fixed stars; seeded by size so a resize does not reshuffle them.
                    p.shader=null
                    val random=java.util.Random((w*31+h).toLong())
                    repeat((w*h/5200).coerceIn(6,40)) {
                        val x=random.nextFloat()*wf;val y=random.nextFloat()*hf*.62f
                        p.color=android.graphics.Color.argb(40+random.nextInt(90),255,255,255)
                        c.drawCircle(x,y,(.6f+random.nextFloat()*1.1f)*maxOf(1f,wf/320f),p)
                    }
                }
                condition==WeatherCondition.CLOUDY||condition==WeatherCondition.RAIN||condition==WeatherCondition.THUNDERSTORM->{
                    p.shader=RadialGradient(wf*.2f,-hf*.1f,maxOf(wf,hf)*.9f,intArrayOf(0x22FFFFFF,0x00FFFFFF),null,Shader.TileMode.CLAMP)
                    c.drawRect(rect,p)
                }
            }
        }
        // iOS cards carry a faint top sheen and a hairline rim so they hold their edge on any wallpaper.
        p.shader=LinearGradient(0f,0f,0f,hf*.5f,0x14FFFFFF,0x00FFFFFF,Shader.TileMode.CLAMP);c.drawRect(rect,p)
        c.restore()
        p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=maxOf(1f,minOf(wf,hf)/240f);p.color=0x24FFFFFF
        val half=p.strokeWidth/2
        c.drawRoundRect(RectF(half,half,wf-half,hf-half),(radius-half).coerceAtLeast(0f),(radius-half).coerceAtLeast(0f),p)
        return bitmap
    }

    private fun mix(a:Int,b:Int,t:Float):Int {
        fun ch(x:Int,s:Int)=(x shr s) and 0xFF
        fun l(s:Int)=(ch(a,s)+(ch(b,s)-ch(a,s))*t.coerceIn(0f,1f)).toInt().coerceIn(0,255)
        return (l(24) shl 24) or (l(16) shl 16) or (l(8) shl 8) or l(0)
    }
}
