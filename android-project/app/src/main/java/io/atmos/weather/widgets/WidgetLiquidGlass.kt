package io.atmos.weather.widgets

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.pow

/**
 * Layered glass for launcher widgets.
 *
 * An ordinary widget can neither blur nor sample the wallpaper behind it, so this is not a
 * copy of a system shader. The material is built from what a widget is able to draw: a
 * translucent tinted body the wallpaper shows through, light entering from the upper-left,
 * depth along the inner edge, and a specular rim with a second catch-light lower-right.
 */
internal object WidgetLiquidGlass {
    private const val SOLID=.94f

    /**
     * How much of the wallpaper the body hides. With a known backdrop the user's opacity
     * preference may go nearly clear; with an unknown one the floor stays high enough for
     * primary text on any wallpaper.
     */
    fun bodyAlpha(dark:Boolean,knownBackdrop:Boolean,opacity:Float):Float {
        val o=(opacity.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.66f).pow(1.6f)
        val low=if(!knownBackdrop).60f else if(dark).26f else .36f
        return low+(SOLID-low)*o
    }

    private fun tint(color:Int,alpha:Float)=(color and 0x00FFFFFF) or (((alpha.coerceIn(0f,1f)*255f+.5f).toInt()) shl 24)
    private fun white(alpha:Float)=tint(Color.WHITE,alpha)
    private fun black(alpha:Float)=tint(Color.BLACK,alpha)

    /** [top], [bottom] and [accent] are read as opaque RGB; transparency belongs to this renderer. */
    fun render(width:Int,height:Int,cornerRadiusPx:Float,top:Int,bottom:Int,accent:Int,dark:Boolean,
        knownBackdrop:Boolean,opacity:Float,edge:Float):Bitmap {
        val w=width.coerceIn(1,640);val h=height.coerceIn(1,640)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap)
        val wf=w.toFloat();val hf=h.toFloat();val inset=1f
        val rect=RectF(inset,inset,wf-inset,hf-inset)
        val radius=(cornerRadiusPx.takeIf{it.isFinite()}?:0f).coerceIn(0f,minOf(wf,hf)/2f)
        // About one dp on a two-by-two cell; keeps edge thickness proportional when bitmaps are capped.
        val unit=maxOf(1f,minOf(wf,hf)/160f)
        val e=edge.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.65f
        val body=bodyAlpha(dark,knownBackdrop,opacity)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Body: slightly denser toward the lower-right, like a thick lens.
        p.shader=LinearGradient(0f,0f,wf,hf,tint(top,body*.86f),tint(bottom,body*1.06f),Shader.TileMode.CLAMP)
        c.drawRoundRect(rect,radius,radius,p)

        c.save();c.clipPath(Path().apply{addRoundRect(rect,radius,radius,Path.Direction.CW)})
        // 2. Theme colour pooling in the lower-right.
        p.shader=RadialGradient(wf*.96f,hf*1.02f,maxOf(wf,hf)*.78f,
            intArrayOf(tint(accent,(if(dark)0x30 else 0x26)/255f),tint(accent,0f)),null,Shader.TileMode.CLAMP)
        c.drawRect(rect,p)
        // 3. Sheen: light entering the glass from the upper-left.
        p.shader=RadialGradient(wf*.18f,-hf*.10f,maxOf(wf,hf)*.95f,
            intArrayOf(white((if(dark)0x2E else 0x66)/255f*(.6f+.4f*e)),white(0x08/255f),white(0f)),
            floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP)
        c.drawRect(rect,p)
        // 4. Inner depth: a soft shade hugging the lower-right edge and a soft light upper-left.
        val depth=Paint(Paint.ANTI_ALIAS_FLAG).apply{
            style=Paint.Style.STROKE;strokeWidth=3.2f*unit
            maskFilter=BlurMaskFilter(3.8f*unit,BlurMaskFilter.Blur.NORMAL)
        }
        val shifted=RectF(rect).apply{offset(-1.4f*unit,-1.4f*unit)}
        depth.color=black((if(dark)0x38 else 0x1C)/255f*(.5f+.5f*e));c.drawRoundRect(shifted,radius,radius,depth)
        shifted.set(rect);shifted.offset(1.4f*unit,1.4f*unit)
        depth.color=white((if(dark)0x30 else 0x70)/255f*(.5f+.5f*e));c.drawRoundRect(shifted,radius,radius,depth)
        c.restore()

        // 5. Specular rim: bright upper-left, quiet middle, second catch-light lower-right.
        val k=.45f+.55f*e
        val stroke=maxOf(1f,1.15f*unit);val half=stroke/2f
        val rim=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=stroke}
        rim.shader=LinearGradient(0f,0f,wf,hf,
            intArrayOf(white(0xF0/255f*k),white(0x38/255f*k),white(0x14/255f*k),white(0x9A/255f*k)),
            floatArrayOf(0f,.30f,.68f,1f),Shader.TileMode.CLAMP)
        val rimRadius=(radius-half).coerceAtLeast(0f)
        c.drawRoundRect(RectF(inset+half,inset+half,wf-inset-half,hf-inset-half),rimRadius,rimRadius,rim)
        // 6. A hairline just inside the rim gives the edge its thickness.
        val gap=stroke+.8f*unit
        if(wf-2f*(inset+gap)>1f&&hf-2f*(inset+gap)>1f) {
            rim.shader=null;rim.strokeWidth=maxOf(1f,.7f*unit);rim.color=white((if(dark)0x1E else 0x40)/255f*k)
            val innerRadius=(radius-gap).coerceAtLeast(0f)
            c.drawRoundRect(RectF(inset+gap,inset+gap,wf-inset-gap,hf-inset-gap),innerRadius,innerRadius,rim)
        }
        return bitmap
    }
}
