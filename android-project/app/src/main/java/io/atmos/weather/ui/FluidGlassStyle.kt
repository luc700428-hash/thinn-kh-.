package io.atmos.weather.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.min

/** Independently tuned Android motion; Apple's public guidance does not publish private spring constants. */
internal object FluidGlassStyle {
 const val POSITION_DAMPING=.78f
 const val POSITION_STIFFNESS=420f
 const val TAIL_DAMPING=.76f
 const val TAIL_STIFFNESS=145f
 const val CONTACT_DAMPING=.60f
 const val CONTACT_STIFFNESS=360f
 data class Navigation(val underlay:Color,val selection:Color,val selectedInk:Color,val ink:Color,val rim:Color)
 fun navigation(theme:AppThemeColor,dark:Boolean)=navigation(BrandPalette.scheme(theme,dark),dark)
 fun navigation(p:androidx.compose.material3.ColorScheme,dark:Boolean):Navigation {
  return Navigation(p.surface.copy(alpha=if(dark).72f else .66f),p.primary.copy(alpha=if(dark).22f else .14f),
   p.primary,p.onSurface,
   if(dark)Color.White.copy(alpha=.30f) else Color(0xFF24362F).copy(alpha=.18f))
 }
}

/** One continuous contour. The resting shape is exactly the original rounded thumb. */
internal fun liquidCapsulePath(head:Float,tail:Float,width:Float,height:Float,y:Float,neck:Float):Path {
 val a=width/2;val r=min(width,height)/2
 val left=minOf(head,tail);val right=maxOf(head,tail)
 if(abs(right-left)<.35f)return Path().apply{
  addRoundRect(RoundRect(Rect(head-a,y-height/2,head+a,y+height/2),CornerRadius(r)))
 }
 val half=height/2;val mid=(left+right)/2;val waist=neck.coerceIn(half*.72f,half)
 val l=left-a;val z=right+a;val shoulder=(a-r).coerceAtLeast(0f)
 return Path().apply{
  moveTo(l+r,y-half)
  cubicTo(left+shoulder,y-half,mid-(right-left)*.12f,y-waist,mid,y-waist)
  cubicTo(mid+(right-left)*.12f,y-waist,right-shoulder,y-half,z-r,y-half)
  cubicTo(z-r*.45f,y-half,z,y-half*.55f,z,y)
  cubicTo(z,y+half*.55f,z-r*.45f,y+half,z-r,y+half)
  cubicTo(right-shoulder,y+half,mid+(right-left)*.12f,y+waist,mid,y+waist)
  cubicTo(mid-(right-left)*.12f,y+waist,left+shoulder,y+half,l+r,y+half)
  cubicTo(l+r*.45f,y+half,l,y+half*.55f,l,y)
  cubicTo(l,y-half*.55f,l+r*.45f,y-half,l+r,y-half);close()
 }
}
