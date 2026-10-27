package io.atmos.weather.widgets

import android.graphics.*
import android.util.LruCache
import io.atmos.weather.domain.model.WeatherCondition
import kotlin.math.*

/** Original layered weather artwork; two bounded frames, shared by all widget sizes. */
object WidgetWeatherArt {
    private val cache=object:LruCache<String,Bitmap>(2*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    fun condition(code:Int?):WeatherCondition=when(code) {
        0,1->WeatherCondition.CLEAR;2->WeatherCondition.PARTLY_CLOUDY;3->WeatherCondition.CLOUDY
        45,48->WeatherCondition.FOG;51,53,55,56,57,61,63,65,66,67,80,81,82->WeatherCondition.RAIN
        71,73,75,77,85,86->WeatherCondition.SNOW;95,96,99->WeatherCondition.THUNDERSTORM
        else->WeatherCondition.UNKNOWN
    }
    @Synchronized fun render(condition:WeatherCondition,day:Boolean,pixels:Int,phase:Int):Bitmap {
        val size=pixels.coerceIn(24,192);val frame=Math.floorMod(phase,2)
        val key="$condition:$day:$size:$frame"
        cache.get(key)?.let{return it}
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap);c.scale(size/100f,size/100f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        val rain=condition in setOf(WeatherCondition.RAIN,WeatherCondition.FREEZING_RAIN,WeatherCondition.SLEET,WeatherCondition.HAIL)
        val storm=condition==WeatherCondition.THUNDERSTORM
        val snow=condition==WeatherCondition.SNOW
        val fog=condition in setOf(WeatherCondition.FOG,WeatherCondition.HAZE)
        val dust=condition in setOf(WeatherCondition.DUST,WeatherCondition.DUST_STORM)
        val clear=condition==WeatherCondition.CLEAR
        val partly=condition==WeatherCondition.PARTLY_CLOUDY
        val floatY=if(frame==0)0f else -2f
        if(clear||partly) {
            val x=if(partly)37f else 50f;val y=(if(partly)34f else 45f)+floatY
            if(day) {
                p.shader=RadialGradient(x,y,36f,intArrayOf(0x55FFE29A,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
                c.drawCircle(x,y,36f,p);p.shader=null;p.color=0xFFFFC958.toInt();p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=3f
                for(i in 0..7) {val a=i*PI/4+(if(frame==0)0.0 else .10)
                    c.drawLine(x+cos(a).toFloat()*25,y+sin(a).toFloat()*25,x+cos(a).toFloat()*31,y+sin(a).toFloat()*31,p)}
                p.shader=LinearGradient(x-18,y-18,x+18,y+18,0xFFFFF1B7.toInt(),0xFFFFAB35.toInt(),Shader.TileMode.CLAMP)
                c.drawCircle(x,y,19f,p);p.shader=null;p.color=0x80FFFFFF.toInt();c.drawCircle(x-6,y-7,5f,p)
            } else {
                p.shader=LinearGradient(x-20,y-20,x+20,y+20,0xFFF9F5DD.toInt(),0xFF9CD8F1.toInt(),Shader.TileMode.CLAMP)
                val moon=Path().apply {addCircle(x,y,23f,Path.Direction.CW)}
                moon.op(Path().apply {addCircle(x+11,y-8,22f,Path.Direction.CW)},Path.Op.DIFFERENCE)
                c.drawPath(moon,p);p.shader=null;p.color=0xFFF4F5DA.toInt()
                c.drawCircle(x+24,y-20,2f,p);c.drawCircle(x+29,y+9,1.5f,p)
            }
        }
        if(!clear) {
            val cloud=Path().apply {
                addRoundRect(RectF(19f,44f+floatY,86f,68f+floatY),12f,12f,Path.Direction.CW)
                op(Path().apply {addCircle(37f,44f+floatY,16f,Path.Direction.CW)},Path.Op.UNION)
                op(Path().apply {addCircle(59f,39f+floatY,22f,Path.Direction.CW)},Path.Op.UNION)
            }
            p.shader=LinearGradient(20f,27f,76f,74f,if(storm)0xFFB8CAE0.toInt() else 0xFFFFFFFF.toInt(),
                if(storm)0xFF5B789F.toInt() else if(dust)0xFFCCAE82.toInt() else 0xFFB8D8ED.toInt(),Shader.TileMode.CLAMP)
            p.setShadowLayer(3f,0f,3f,0x25082B55);c.drawPath(cloud,p);p.clearShadowLayer();p.shader=null
            p.color=0x80FFFFFF.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=1f;c.drawPath(cloud,p);p.style=Paint.Style.FILL
        }
        p.strokeCap=Paint.Cap.ROUND
        if(rain) {
            p.shader=LinearGradient(30f,70f,65f,91f,0xFF8BE8FF.toInt(),0xFF2688E5.toInt(),Shader.TileMode.CLAMP);p.strokeWidth=4f
            for(i in 0..2) {val x=34f+i*16;val y=76f+if((i+frame)%2==0)0f else 5f;c.drawLine(x,y,x-4,y+9,p)}
        } else if(storm) {
            p.color=0xFFFFD361.toInt();p.shader=null
            c.drawPath(Path().apply {moveTo(53f,66f);lineTo(40f,83f);lineTo(50f,82f);lineTo(44f,97f);lineTo(65f,75f);lineTo(54f,76f);close()},p)
        } else if(snow) {
            p.color=0xFFBCEBFF.toInt();p.shader=null;p.strokeWidth=2f
            for(i in 0..2) {val x=32f+i*18;val y=80f+(i+frame)%2*5
                for(j in 0..2) {val a=j*PI/3;c.drawLine(x-cos(a).toFloat()*5,y-sin(a).toFloat()*5,x+cos(a).toFloat()*5,y+sin(a).toFloat()*5,p)}}
        } else if(fog||dust) {
            p.shader=null;p.color=if(dust)0xFFD2B27D.toInt() else 0xFFC1E2F3.toInt();p.strokeWidth=4f
            c.drawLine(24f+frame*3,76f,76f,76f,p);c.drawLine(34f,86f,81f-frame*3,86f,p)
        } else if(condition==WeatherCondition.UNKNOWN) {
            p.shader=null;p.color=0xFF5788AF.toInt();p.strokeWidth=3f;c.drawLine(44f,53f,61f,53f,p)
        }
        cache.put(key,bitmap);return bitmap
    }
}
