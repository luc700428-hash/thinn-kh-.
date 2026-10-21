package io.atmos.weather.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.visual.WeatherScene

/** Draws weather, daylight, clouds and precipitation, never buildings or 3D landmarks.
 * Stateless bitmaps keep launcher Widget support with no Filament/OpenGL lifecycle.
 */
object WeatherArtRenderer {
    private fun rgb(hex: String) = AndroidColor.parseColor(hex)
    private fun brush(color: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color=color;this.alpha=alpha.coerceIn(0,255)
    }
    fun drawScene(canvas: Canvas, width: Float, height: Float, scene: WeatherScene?, phase: Float = 0f) {
        if(width<=0f || height<=0f) return
        val night=(scene?.nightLight ?: 0f)>.4f
        val rain=(scene?.wetness ?: 0f)>.2f
        val snow=(scene?.snowAccumulation ?: 0f)>.2f
        val storm=scene?.showLightning == true
        val save=canvas.save()
        canvas.scale(width/1000f,height/650f)
        val halo=Paint(Paint.ANTI_ALIAS_FLAG)
        halo.shader=RadialGradient(770f,160f,295f,
            intArrayOf(if(night) 0x60B2D1FC else 0x99FFE5AA.toInt(),AndroidColor.TRANSPARENT),
            null,Shader.TileMode.CLAMP)
        canvas.drawCircle(770f,160f,295f,halo);halo.shader=null
        if(night) for(i in 0..28) {
            val x=(i*191+37)%980f;val y=(i*91+19)%350f
            canvas.drawCircle(x,y,1.5f+(i%3),brush(AndroidColor.WHITE,105+(i%3)*20))
        }
        if(!storm) canvas.drawCircle(775f,163f,if(night) 29f else 59f,
            brush(if(night)rgb("#E9F1FF") else rgb("#FFF0C1"),if(night)180 else 230))
        // Low clouds respond to movement but never depict fictional places.
        val cloudPaint=brush(if(night)rgb("#6583A7") else rgb("#F0F8FF"),if(storm)115 else 140)
        for(i in 0..5) {
            val x=120f+i*195f+phase*10f
            val y=275f+(i%3)*47f
            canvas.drawOval(x-140f,y-26f,x+145f,y+52f,cloudPaint)
            canvas.drawOval(x-66f,y-69f,x+99f,y+43f,cloudPaint)
        }
        if(rain||snow){
            val shift=(phase*325f).toInt()
            for(i in 0 until 50){
                val x=((i*137+41)%1040).toFloat()
                val y=((i*71+shift)%680).toFloat()
                if(snow){canvas.drawCircle(x,y,3f+(i%3),brush(AndroidColor.WHITE,150))}
                else {val p=brush(rgb("#AEE6FF"),115).apply{strokeWidth=2.1f;strokeCap=Paint.Cap.ROUND}
                    canvas.drawLine(x,y,x-8f,y+24f,p)}
            }
        }
        if(storm) {
            val lightning=brush(rgb("#DAEFFF"),185).apply{strokeWidth=5f;strokeCap=Paint.Cap.ROUND}
            val path=android.graphics.Path().apply {
                moveTo(540f,280f);lineTo(505f,355f);lineTo(533f,357f);lineTo(482f,425f)
            }
            canvas.drawPath(path,lightning)
        }
        canvas.restoreToCount(save)
    }

    /** Existing Widget API retained; only weather art is rendered. */
    fun widgetBitmap(scene: WeatherScene, condition: WeatherCondition, variant: Int,
        width:Int=520,height:Int=320,glassOpacity:Float=.66f,edgeLight:Float=.65f,
        lightPhase:Int=0):Bitmap {
        val w=width.coerceIn(1,2048);val h=height.coerceIn(1,2048)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val night=scene.nightLight>.4f
        val storm=condition==WeatherCondition.THUNDERSTORM
        val skyTop=when {
            storm->rgb("#101E36");night->rgb("#0C1B34")
            condition==WeatherCondition.SNOW->rgb("#577C97")
            condition==WeatherCondition.RAIN->rgb("#285C78")
            else->rgb("#277DB5")
        }
        val skyBottom=when {
            storm->rgb("#455C7F");night->rgb("#2C537B")
            condition==WeatherCondition.SNOW->rgb("#BFDCEB")
            condition==WeatherCondition.RAIN->rgb("#76A2BA")
            else->rgb("#8BD3E8")
        }
        val variantHue=(variant%5-2)*3f
        fun tint(value:Int):Int {
            val hsv=FloatArray(3);AndroidColor.colorToHSV(value,hsv)
            hsv[0]=(hsv[0]+variantHue+360f)%360f
            return AndroidColor.HSVToColor(hsv)
        }
        val background=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(0f,0f,w.toFloat(),h.toFloat(),tint(skyTop),tint(skyBottom),Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(0f,0f,w.toFloat(),h.toFloat(),32f,32f,background)
        // Preserve V25 Widget's four-phase lighting cycle after removing 3D landmarks.
        val phase = ((lightPhase % 4) + 4) % 4
        val mx = w * (.63f + .06f * phase)
        val my = h * (.22f + .05f * (phase % 3))
        val glowRadius = maxOf(w,h) * .68f
        val glowCenter = if(night) 0x3893BCF4 else 0x5CFFE6B0
        val moodGlow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(mx,my,glowRadius,
                intArrayOf(glowCenter,AndroidColor.TRANSPARENT),null,Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(mx,my,glowRadius,moodGlow)
        canvas.save()
        canvas.translate(w*.21f,h*.12f)
        canvas.scale(w/1000f,h/750f)
        drawScene(canvas,1000f,650f,scene)
        canvas.restore()
        val overlay=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val dark=(55+(1f-glassOpacity.coerceIn(0f,1f))*95).toInt()
            shader=LinearGradient(0f,0f,w*.90f,0f,
                intArrayOf(AndroidColor.argb(dark,8,25,47),AndroidColor.argb(dark/2,15,43,68),AndroidColor.TRANSPARENT),
                null,Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(0f,0f,w.toFloat(),h.toFloat(),32f,32f,overlay)
        val rim=brush(AndroidColor.WHITE,(20+edgeLight.coerceIn(0f,1f)*100).toInt()).apply {
            style=Paint.Style.STROKE;strokeWidth=2f
        }
        canvas.drawRoundRect(2f,2f,w-2f,h-2f,32f,32f,rim)
        return bitmap
    }
}
