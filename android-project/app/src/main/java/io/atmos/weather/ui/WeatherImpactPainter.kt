package io.atmos.weather.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*
import kotlin.random.Random

/** Vector drawing of the physics state, never prerendered video or generated weather imagery. */
internal fun DrawScope.drawWeatherSurfaceEffects(particles:WeatherSurfacePhysics,rain:RainCollisionWorld,
 density:Float,windKmh:Float,light:Boolean) {
 fun pos(x:Float,y:Float)=Offset(x*density,y*density)
 particles.caps.forEach{(id,bins)->
  val s=rain.surfaces[id]?:return@forEach
  if(!s.active)return@forEach
  val crest=ArrayList<Offset>();val roofline=ArrayList<Offset>()
  val half=s.width/bins.size*.5f*density
  fun drawMound(){
   if(crest.isEmpty())return
   val first=crest.first();val last=crest.last();val baseFirst=roofline.first();val baseLast=roofline.last()
   val path=Path().apply{
    moveTo(first.x-half,baseFirst.y)
    cubicTo(first.x-half,first.y,first.x-half*.55f,first.y,first.x,first.y)
    for(i in 1 until crest.size){val a=crest[i-1];val b=crest[i];val mid=(a.x+b.x)/2
     cubicTo(mid,a.y,mid,b.y,b.x,b.y)}
    cubicTo(last.x+half*.55f,last.y,last.x+half,last.y,last.x+half,baseLast.y)
    lineTo(baseLast.x,baseLast.y+.6f*density)
    for(i in roofline.lastIndex-1 downTo 0)lineTo(roofline[i].x,roofline[i].y+.6f*density)
    close()
   }
   drawPath(path,Color(0xFFEDF5F7))
   drawPath(path,Color(0xFF8EA8B0).copy(alpha=if(light).30f else .16f),style=Stroke(.4f*density))
   crest.clear();roofline.clear()
  }
  bins.forEachIndexed{i,mass->
   val x=s.left+(i+.5f)/bins.size*s.width;val roof=rain.roofY(s,x)
   if(mass<.03f||!roof.isFinite()||roof>s.clipBottom)drawMound()
   else {crest+=pos(x,roof-(mass*.85f).coerceAtMost(9f));roofline+=pos(x,roof)}
  }
  drawMound()
 }
 particles.bodies.forEach{p->
  val center=pos(p.x,p.y);val r=p.radius*density
  if(p.kind==WeatherSurfacePhysics.Kind.SNOW){
   if(p.mass>.8f){drawOval(Color(0xEEF4FAFC),center-Offset(r,r*.6f),Size(r*2,r*1.2f))}
   else for(arm in 0..2){val a=p.angle*.0174533f+arm*PI.toFloat()/3
    val delta=Offset(cos(a)*r,sin(a)*r)
    drawLine(if(light)Color(0xDDA9BFC7) else Color(0xDDF1F9FC),center-delta,center+delta,.65f*density,StrokeCap.Round)
   }
  }else{
   val angle=if(p.owner!=null)sin(particles.time*17f+p.u*31f)*24f+sin(particles.time*6f)*10f else p.angle
   rotate(angle,center){
    val leaf=Path().apply{moveTo(center.x-r,center.y)
     cubicTo(center.x-r*.25f,center.y-r,center.x+r*.9f,center.y-r*.6f,center.x+r*1.15f,center.y-r*.1f)
     cubicTo(center.x+r*.5f,center.y+r*.8f,center.x-r*.4f,center.y+r*.7f,center.x-r,center.y);close()}
    drawPath(leaf,if(p.u>.5f)Color(0xFF9D7A42) else Color(0xFFAA9354))
    drawLine(Color(0xFF665634),center-Offset(r*.85f,0f),center+Offset(r*.95f,-r*.05f),.45f*density,StrokeCap.Round)
   }
  }
 }
 particles.damage.forEach{d->
  val s=rain.surfaces[d.owner]?:return@forEach
  val point=particles.strikePoint(d,s);val center=pos(point.x,point.y)
  val fade=(1f-(d.age-5f).coerceAtLeast(0f)/5f).coerceIn(0f,1f)
  val radius=(8f+sin(d.seed.toFloat())*2f)*density
  drawCircle(Brush.radialGradient(listOf(Color(0xFF352721).copy(alpha=.48f*fade),Color(0xFF765641).copy(alpha=.20f*fade),Color.Transparent),center,radius),radius,center)
  val rng=Random(d.seed);val inward=if(d.right)-1f else 1f
  repeat(3){branch->
   val crack=Path().apply{moveTo(center.x,center.y)
    var x=center.x;var y=center.y
    repeat(5){x+=inward*(1f+rng.nextFloat()*2.5f)*density;y+=(.8f+rng.nextFloat()*2f)*density
     lineTo(x+(branch-1)*density,y)}
   }
   drawPath(crack,Color(0xFF4D5F65).copy(alpha=.45f*fade),style=Stroke(.8f*density,cap=StrokeCap.Round))
   drawPath(crack,Color.White.copy(alpha=.55f*fade),style=Stroke(.28f*density,cap=StrokeCap.Round))
  }
  if(d.age<.6f){val smoke=center+Offset(windKmh*.08f*d.age*density,-d.age*18*density)
   drawCircle(Color(0xFF6C6763).copy(alpha=(.06f*(1-d.age/.6f)).coerceAtLeast(0f)),(4+d.age*10)*density,smoke)}
 }
 particles.bolt?.let{d->
  val s=rain.surfaces[d.owner]?:return@let
  val target=particles.strikePoint(d,s);val rng=Random(d.seed)
  val end=pos(target.x,target.y);val start=Offset((end.x+(rng.nextFloat()-.5f)*size.width*.5f).coerceIn(0f,size.width),0f)
  val points=(0..15).map{i->val t=i/15f
   Offset(start.x+(end.x-start.x)*t+(if(i in 1..14)(rng.nextFloat()-.5f)*28*density else 0f),start.y+(end.y-start.y)*t)}
  val path=Path().apply{moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)}}
  val pulse=when{d.age<.06f->1f;d.age<.14f->.12f;d.age<.23f->.85f;else->0f}
  if(pulse>0){
   drawPath(path,Color(0xFF98BEDE).copy(alpha=.25f*pulse),style=Stroke(6*density,cap=StrokeCap.Round,join=StrokeJoin.Round))
   drawPath(path,Color(0xFFF4FBFF).copy(alpha=pulse),style=Stroke(1.4f*density,cap=StrokeCap.Round,join=StrokeJoin.Round))
   for(index in listOf(6,10)){val p=points[index];val branch=Path().apply{moveTo(p.x,p.y);lineTo(p.x+18*density,p.y+9*density);lineTo(p.x+14*density,p.y+23*density);lineTo(p.x+28*density,p.y+34*density)}
    drawPath(branch,Color(0xFFD5EDFF).copy(alpha=.65f*pulse),style=Stroke(.7f*density))}
  }
 }
}
