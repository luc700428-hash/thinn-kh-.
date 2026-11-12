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
   val anchor=center
   val c=if(p.owner!=null)center+Offset(r*.85f,0f) else center
   rotate(p.angle,anchor){
    val bend=p.flex*r
    val leaf=Path().apply{moveTo(c.x-r,c.y)
     cubicTo(c.x-r*.25f,c.y-r+bend*.4f,c.x+r*.9f,c.y-r*.6f+bend,c.x+r*1.15f,c.y-r*.1f+bend)
     cubicTo(c.x+r*.5f,c.y+r*.8f+bend,c.x-r*.4f,c.y+r*.7f,c.x-r,c.y);close()}
    val leafColor=when(p.noiseSeed ushr 3 and 3){0->Color(0xFF7C9162);1->Color(0xFF9D7A42);2->Color(0xFF938953);else->Color(0xFFAA9354)}
    drawPath(leaf,leafColor)
    drawLine(Color(0xFF665634),c-Offset(r*.85f,0f),c+Offset(r*.95f,-r*.05f+bend*.65f),.45f*density,StrokeCap.Round)
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
  val target=particles.strikePoint(d,s)
  val geometry=d.geometry?:LightningGeometry.create(d.seed,size.width/density,size.height/density,target.x,target.y)
  val pulse=d.profile.intensity(d.age).coerceIn(0f,1f)
  if(pulse>0f){
   fun stroke(points:List<RainCollisionWorld.Point>,branch:Boolean){
    val path=Path().apply{moveTo(points.first().x*density,points.first().y*density)
     for(i in 1 until points.size)lineTo(points[i].x*density,points[i].y*density)}
    drawPath(path,Color(0xFF2F536D).copy(alpha=.38f*pulse),style=Stroke((if(branch)1.9f else 4.2f)*density,cap=StrokeCap.Round,join=StrokeJoin.Round))
    drawPath(path,Color(0xFF9CCAF3).copy(alpha=.30f*pulse),style=Stroke((if(branch)3.2f else 10f)*density,cap=StrokeCap.Round,join=StrokeJoin.Round))
    drawPath(path,Color(0xFFD6EAFF).copy(alpha=.72f*pulse),style=Stroke((if(branch)1.1f else 3f)*density,cap=StrokeCap.Round,join=StrokeJoin.Round))
    drawPath(path,Color(0xFFFAFDFF).copy(alpha=pulse),style=Stroke((if(branch).55f else 1.4f)*density,cap=StrokeCap.Round,join=StrokeJoin.Round))
   }
   geometry.branches.forEach{stroke(it,true)};stroke(geometry.trunk,false)
  }
 }
}
