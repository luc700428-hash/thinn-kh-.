package io.atmos.weather.widgets

import android.graphics.*
import android.util.LruCache
import io.atmos.weather.domain.model.WeatherCondition
import kotlin.math.*
import kotlin.random.Random

/** Closed, deterministic motion curves; a fresh seed changes the next widget presentation. */
object BuddyMotion {
    data class Pose(val lookX:Float,val lookY:Float,val openness:Float)
    fun pose(phase:Float,seed:Int):Pose {
        val t=((phase%1f)+1f)%1f
        val rng=Random(seed)
        val blinkAt=.35f+rng.nextFloat()*.35f
        val distance=abs(t-blinkAt)
        val blink=(distance/.065f).coerceIn(0f,1f)
        val look=sin(t*2*PI).toFloat()
        return Pose(look*(1.2f+rng.nextFloat()*1.5f),sin(t*4*PI).toFloat()*.65f,blink)
    }
}

/** Original vector character art, rasterised at a bounded size for RemoteViews. */
object WeatherBuddyArt {
    enum class Kind { SUN, MOON, CLOUD, RAIN, SNOW, STORM, MIST, UNKNOWN }
    fun kind(condition:WeatherCondition,day:Boolean)=when(condition) {
        WeatherCondition.CLEAR->if(day)Kind.SUN else Kind.MOON
        WeatherCondition.PARTLY_CLOUDY,WeatherCondition.CLOUDY->Kind.CLOUD
        WeatherCondition.RAIN,WeatherCondition.FREEZING_RAIN,WeatherCondition.SLEET->Kind.RAIN
        WeatherCondition.THUNDERSTORM,WeatherCondition.HAIL->Kind.STORM
        WeatherCondition.SNOW->Kind.SNOW
        WeatherCondition.FOG,WeatherCondition.HAZE,WeatherCondition.DUST,WeatherCondition.DUST_STORM->Kind.MIST
        else->Kind.UNKNOWN
    }
    private val cache=object:LruCache<String,Bitmap>(8*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    private fun image(key:String,size:Int,draw:(Canvas,Paint)->Unit):Bitmap = synchronized(cache) {
        cache.get(key)?:Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also {b->
            val canvas=Canvas(b);canvas.scale(size/100f,size/100f)
            draw(canvas,Paint(Paint.ANTI_ALIAS_FLAG));cache.put(key,b)
        }
    }
    fun body(kind:Kind,day:Boolean,pixels:Int):Bitmap {
        val px=pixels.coerceIn(48,144)
        return image("body:$kind:$day:$px",px){c,p->
            fun oval(l:Float,t:Float,r:Float,b:Float,color:Int){p.shader=null;p.color=color;c.drawOval(l,t,r,b,p)}
            val warm=kind==Kind.SUN;val light=if(warm)0xFFFFDA72.toInt() else if(day)0xFFB7E6FF.toInt() else 0xFFAFBDF7.toInt()
            p.shader=RadialGradient(50f,47f,47f,intArrayOf((light and 0xFFFFFF) or 0x48000000,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            c.drawCircle(50f,47f,47f,p);p.shader=null
            oval(22f,88f,78f,94f,0x180E2549)
            p.color=Color.WHITE
            when(kind) {
                Kind.SUN->{
                    p.strokeWidth=3.6f;p.strokeCap=Paint.Cap.ROUND;p.color=0xFFFFD16A.toInt()
                    for(i in 0..11){val a=i*PI/6;c.drawLine((50+29*cos(a)).toFloat(),(45+29*sin(a)).toFloat(),(50+35*cos(a)).toFloat(),(45+35*sin(a)).toFloat(),p)}
                    p.shader=RadialGradient(40f,32f,40f,intArrayOf(0xFFFFF7CC.toInt(),0xFFFFC955.toInt(),0xFFF4944D.toInt()),floatArrayOf(0f,.66f,1f),Shader.TileMode.CLAMP)
                    c.drawCircle(50f,45f,24f,p)
                }
                Kind.MOON->{
                    val path=Path().apply{moveTo(58f,16f);cubicTo(26f,13f,19f,66f,52f,73f);cubicTo(67f,77f,78f,67f,82f,57f);cubicTo(49f,68f,39f,34f,58f,16f);close()}
                    p.shader=LinearGradient(30f,15f,75f,78f,intArrayOf(0xFFFFF6C5.toInt(),0xFFB9D5FF.toInt()),null,Shader.TileMode.CLAMP);c.drawPath(path,p)
                    oval(28f,34f,35f,41f,0x387D99C2);oval(43f,61f,48f,66f,0x387D99C2)
                    p.color=0xFFF5E8B1.toInt();c.drawCircle(75f,24f,2f,p);c.drawCircle(19f,60f,1.2f,p)
                }
                Kind.UNKNOWN->{p.color=0xFFE4EAF1.toInt();c.drawRoundRect(21f,26f,79f,68f,21f,21f,p);p.color=0xFF667B91.toInt();p.textAlign=Paint.Align.CENTER;p.textSize=23f;c.drawText("?",50f,57f,p)}
                else->{
                    if(!day){p.color=0xFFF0DDAC.toInt();c.drawCircle(71f,27f,13f,p)}
                    val dark=kind==Kind.RAIN||kind==Kind.STORM
                    val path=Path().apply{moveTo(24f,67f);cubicTo(7f,66f,7f,43f,24f,42f);cubicTo(23f,20f,51f,15f,61f,34f);cubicTo(78f,25f,90f,41f,84f,51f);cubicTo(99f,64f,80f,73f,68f,69f);lineTo(24f,69f);close()}
                    val colors=if(dark)intArrayOf(0xFFB9C8E3.toInt(),0xFF5F789E.toInt(),0xFF3A5379.toInt()) else intArrayOf(Color.WHITE,0xFFDAF0FF.toInt(),0xFF96BCD9.toInt())
                    p.shader=LinearGradient(30f,26f,70f,77f,colors,floatArrayOf(0f,.57f,1f),Shader.TileMode.CLAMP);c.drawPath(path,p);p.shader=null
                    if(kind==Kind.MIST){p.color=0x889EBED4.toInt();p.strokeWidth=2f;for(y in listOf(76f,82f))c.drawLine(20f,y,80f,y,p)}
                    if(kind==Kind.STORM){p.color=0xFFFFD97B.toInt();c.drawPath(Path().apply{moveTo(65f,69f);lineTo(56f,81f);lineTo(64f,80f);lineTo(60f,90f);lineTo(74f,75f);lineTo(65f,76f);close()},p)}
                }
            }
            p.shader=null
            if(kind!=Kind.UNKNOWN){
                p.color=0x39FFFFFF;p.strokeWidth=1.3f;p.style=Paint.Style.STROKE;c.drawArc(28f,25f,68f,60f,205f,57f,false,p);p.style=Paint.Style.FILL
                val y=if(kind==Kind.SUN)52f else if(kind==Kind.MOON)51f else 59f
                oval(31f,y-1,40f,y+3,0x4DE8A7B4);oval(62f,y-1,71f,y+3,0x4DE8A7B4)
            }
        }
    }
    fun eyes(kind:Kind,pixels:Int,phase:Float,seed:Int):Bitmap {
        val px=pixels.coerceIn(48,144);val frame=(phase*10000).roundToInt()
        return image("eyes:$kind:$px:$frame:$seed",px){c,p->
            if(kind==Kind.UNKNOWN)return@image
            val pose=BuddyMotion.pose(phase,seed);val y=if(kind==Kind.SUN)44f else if(kind==Kind.MOON)46f else 52f
            val left=if(kind==Kind.MOON)37f else 40f;val right=if(kind==Kind.MOON)52f else 62f
            p.color=0xFF283E5C.toInt();p.strokeWidth=2.3f;p.strokeCap=Paint.Cap.ROUND
            for(x in listOf(left,right)) {
                if(pose.openness<.22f)c.drawLine(x-2.2f,y,x+2.2f,y,p)
                else {c.drawOval(x-2.6f+pose.lookX,y-3.8f*pose.openness+pose.lookY,x+2.6f+pose.lookX,y+3.8f*pose.openness+pose.lookY,p)
                    p.color=0xFFF8FCFF.toInt();c.drawCircle(x-.5f+pose.lookX,y-1.6f+pose.lookY,.9f*pose.openness,p);p.color=0xFF283E5C.toInt()}
            }
            p.style=Paint.Style.STROKE;p.strokeWidth=1.5f;c.drawArc(left+5f,y+3f,right-5f,y+11f,15f,150f,false,p);p.style=Paint.Style.FILL
        }
    }
    fun rain(kind:Kind,pixels:Int,frame:Int,ground:Boolean):Bitmap {
        val px=pixels.coerceIn(48,144);val f=Math.floorMod(frame,24)
        return image("rain:$kind:$px:$f:$ground",px){c,p->
            if(kind !in listOf(Kind.RAIN,Kind.STORM,Kind.SNOW))return@image
            val t=f/24f;p.strokeCap=Paint.Cap.ROUND
            for(i in 0..5){
                val x=23f+i*10f;val phase=(t+i*.167f)%1f
                if(ground){
                    if(kind==Kind.SNOW)continue
                    val age=(phase-.82f)/.18f
                    if(age in 0f..1f){p.color=Color.argb(((1-age)*155).toInt(),130,205,255);p.style=Paint.Style.STROKE;p.strokeWidth=.8f;c.drawOval(x-6*age,91-2*age,x+6*age,91+2*age,p);p.style=Paint.Style.FILL
                        for(sign in listOf(-1,1))c.drawCircle(x+sign*age*5,91-sin(age*PI).toFloat()*4,.6f,p)}
                } else if(phase<.82f){
                    val y=69+phase/.82f*22;p.color=if(kind==Kind.SNOW)0xF2EAF8FF.toInt() else 0xCC9BDFFF.toInt();p.strokeWidth=1.5f
                    if(kind==Kind.SNOW)c.drawCircle(x+sin(phase*PI*2).toFloat()*2,y,1.4f,p) else c.drawLine(x+1,y,x,y+3,p)
                }
            }
        }
    }
    fun still(kind:Kind,day:Boolean,pixels:Int):Bitmap {
        val px=pixels.coerceIn(48,144)
        return image("still:$kind:$day:$px",px){c,_->c.save();c.scale(100f/px,100f/px)
            c.drawBitmap(body(kind,day,px),0f,0f,null);c.drawBitmap(eyes(kind,px,0f,0),0f,0f,null)
            c.drawBitmap(rain(kind,px,5,false),0f,0f,null);c.drawBitmap(rain(kind,px,10,true),0f,0f,null);c.restore()}
    }
}
