package io.atmos.weather.ui

import kotlin.math.*
import kotlin.random.Random

/** Native deterministic surface physics; bounded transient visuals never change weather data. */
internal class WeatherSurfacePhysics(seed:Int=97123) {
 enum class Kind { SNOW,LEAF }
 data class Body(val kind:Kind,var x:Float,var y:Float,var vx:Float,var vy:Float,val radius:Float,
  var angle:Float,var spin:Float,val mass:Float=1f,var age:Float=0f,var owner:Int?=null,
  var u:Float=0f,var v:Float=0f,var hold:Float=0f,
  val noiseSeed:Int=angle.toBits() xor x.toBits() xor y.toBits(),var restAngle:Float=angle,var flex:Float=0f)
 data class Hit(val id:Int,val x:Float,val y:Float,val nx:Float,val ny:Float,val fraction:Float)
 data class Damage(val owner:Int,val right:Boolean,val seed:Int,var age:Float=0f,
  val profile:LightningProfile=LightningProfile.fromSeed(seed),var geometry:LightningGeometry?=null)
 val bodies=ArrayList<Body>(180)
 val caps=LinkedHashMap<Int,FloatArray>()
 val damage=ArrayList<Damage>(4)
 var bolt:Damage?=null;private set
 var time=0f;private set
 var snowDeposits=0;private set
 var snowEjections=0;private set
 var leafContacts=0;private set
 var leafReleases=0;private set
 private val random=Random(seed)
 private var snowBudget=0f;private var leafBudget=0f;private var remaining=0.0
 private val positions=HashMap<Int,Pair<Float,Float>>()
 val hasMotion get()=bodies.isNotEmpty()||damage.isNotEmpty()||caps.values.any{a->a.any{it>.005f}}
 fun clearAir(){bodies.removeAll{it.owner==null};damage.clear();bolt=null}
 fun remove(id:Int){caps.remove(id);positions.remove(id);bodies.removeAll{it.owner==id};damage.removeAll{it.owner==id}}
 fun strike(surfaces:Collection<RainCollisionWorld.Surface>,width:Float,height:Float,eventSeed:Int?=null):Damage? {
  val targets=surfaces.filter{it.active&&it.top+it.r*.3f<it.clipBottom&&it.contour==null&&it.top>=12f&&it.top<height-40f&&it.bottom>0&&it.right>0&&it.left<width}
  if(targets.isEmpty())return null
  val s=targets[random.nextInt(targets.size)]
  val event=Damage(s.id,random.nextBoolean(),eventSeed?:random.nextInt())
  val at=strikePoint(event,s);event.geometry=LightningGeometry.create(event.seed,width,height,at.x,at.y)
  damage.removeAll{it.owner==s.id};if(damage.size>=4)damage.removeAt(0)
  damage+=event;bolt=event;return event
 }
 fun strikePoint(d:Damage,s:RainCollisionWorld.Surface):RainCollisionWorld.Point {
  val inset=s.r*(1f-.7071068f)
  return RainCollisionWorld.Point(if(d.right)s.right-inset else s.left+inset,s.top+inset)
 }
 fun step(seconds:Float,style:WeatherPhysicsStyle,windKmh:Float,strongWind:Boolean,width:Float,height:Float,
          surfaces:Collection<RainCollisionWorld.Surface>) {
  if(!seconds.isFinite()||seconds<=0f||width<=0||height<=0)return
  val dt=seconds.coerceAtMost(.05f)
  val visible=surfaces.filter{it.active&&it.top<it.clipBottom&&it.bottom>0&&it.top<height&&it.right>0&&it.left<width}
  val live=surfaces.map{it.id}.toSet()
  (caps.keys+positions.keys).toSet().filter{it !in live}.forEach{remove(it)}
  visible.forEach{s->
   val old=positions.put(s.id,s.left to s.top)
   if(old!=null){val dx=s.left-old.first;val dy=s.top-old.second
    if(abs(dx)+abs(dy)>1.2f) {
     val cap=caps[s.id]
     if(cap!=null)for(i in cap.indices) {
      val amount=cap[i]*((abs(dy)+abs(dx))/32f).coerceIn(.12f,.9f)
      if(amount>.03f&&bodies.size<180){cap[i]-=amount;snowEjections++
       bodies+=Body(Kind.SNOW,s.left+(i+.5f)/cap.size*s.width,s.top-cap[i]-2f,
        (dx/dt*.35f+random.nextFloat()*110-55).coerceIn(-500f,500f),
        (dy/dt*.65f-70f-random.nextFloat()*90f).coerceIn(-850f,650f),
        (sqrt(amount)*1.5f).coerceIn(.6f,3.5f),0f,random.nextFloat()*200-100,amount)
      }
     }
     if(abs(dy)+abs(dx)>36f)bodies.filter{it.owner==s.id}.forEach{releaseLeaf(it,windKmh*3.2f,dy/dt,s)}
    }
   }
  }
  remaining+=dt.toDouble()
  // Float display deltas (notably 1/166 s) must not lose a physics step through rounding.
  val fixedStep=1.0/240.0
  while(remaining+1e-6>=fixedStep){integrate(fixedStep.toFloat(),style,windKmh,strongWind,width,height,visible);remaining-=fixedStep}
 }
 private fun integrate(dt:Float,style:WeatherPhysicsStyle,windKmh:Float,strong:Boolean,w:Float,h:Float,
                       surfaces:List<RainCollisionWorld.Surface>) {
  time+=dt
  val wind=WeatherPhysicsStyle.windPixels(windKmh,strong,time)
  if(style.snow>0f){snowBudget+=dt*(5+style.snow*35)*(w/400).coerceIn(.5f,2f)
   while(snowBudget>=1){snowBudget--
    if(bodies.size<180)bodies+=Body(Kind.SNOW,random.nextFloat()*(w+100)-50,-8f,wind*.15f,
     24f+random.nextFloat()*35f+style.snow*25f,.7f+random.nextFloat()*1.6f,random.nextFloat()*360,random.nextFloat()*90-45,.22f+random.nextFloat()*.4f)
   }
  }
  if(strong){leafBudget+=dt*.25f*(w/400).coerceIn(.5f,2f)
   while(leafBudget>=1){leafBudget--
    if(bodies.count{it.kind==Kind.LEAF}<18&&bodies.size<180)bodies+=Body(Kind.LEAF,
     if(wind>=0)-20f else w+20,random.nextFloat()*h*.8f,wind,20f+random.nextFloat()*55f,
     3f+random.nextFloat()*2.5f,random.nextFloat()*360,120f+random.nextFloat()*200f)
   }
  }
  for(i in bodies.lastIndex downTo 0){val p=bodies[i];p.age+=dt
   if(p.owner!=null){
    val s=surfaces.firstOrNull{it.id==p.owner}
    if(s==null){bodies.removeAt(i);continue}
    p.x=s.left+p.u*s.width;p.y=s.top+p.v*s.height
    val strength=(abs(wind)/190f).coerceIn(.15f,1.6f)
    val target=p.restAngle+strength*(MotionNoise.value(time,p.noiseSeed,.75f)*17f+MotionNoise.value(time,p.noiseSeed xor 347,6.5f)*10f)
    p.spin+=((target-p.angle)*70f-p.spin*12f)*dt
    p.angle+=p.spin*dt
    p.flex+=(MotionNoise.value(time,p.noiseSeed xor 911,4.1f)*strength*.32f-p.flex)*dt*14f
    if(p.age>=p.hold)releaseLeaf(p,wind,-25f,s)
    continue
   }
   if(p.kind==Kind.LEAF){
    val spinTarget=MotionNoise.value(time,p.noiseSeed,.65f)*240f+wind*.32f
    p.spin+=(spinTarget-p.spin)*dt*2.6f
    p.flex=MotionNoise.value(time,p.noiseSeed xor 733,2.7f)*.22f
   }
   p.angle+=p.spin*dt
   val x=p.x;val y=p.y
   val drag=if(p.kind==Kind.LEAF)2.3f else .85f
   p.vx+=(wind-p.vx)*drag*dt
   if(p.kind==Kind.LEAF){p.vy+=(42f+MotionNoise.value(time,p.noiseSeed,1.9f)*48f-p.vy)*dt*1.3f}
   else p.vy=(p.vy+150f*dt).coerceAtMost(if(p.mass>.7f)440f else 100f+style.snow*55f)
   p.x+=(p.vx+if(p.kind==Kind.SNOW)sin(time*2.1f+p.angle)*12f else 0f)*dt;p.y+=p.vy*dt
   var hit:Hit?=null
   for(surface in surfaces){val candidate=sweep(surface,x,y,p.x,p.y)
    if(candidate!=null&&(hit==null||candidate.fraction<hit!!.fraction))hit=candidate}
   if(hit!=null){
    val s=surfaces.first{it.id==hit.id}
    if(p.kind==Kind.SNOW&&hit.ny<-.3f){
     val pile=caps.getOrPut(s.id){FloatArray(16)};val bin=((hit.x-s.left)/s.width*16).toInt().coerceIn(0,15)
     pile[bin]+=p.mass;snowDeposits++;bodies.removeAt(i)
     if(pile[bin]>9f){val mass=pile[bin]*.55f;pile[bin]-=mass;snowEjections++
      if(bodies.size<180)bodies+=Body(Kind.SNOW,hit.x,hit.y-pile[bin]-3f,
       (wind*.3f+random.nextFloat()*150-75),-70f-random.nextFloat()*80f,2.3f,0f,120f,mass)
     }
    }else if(p.kind==Kind.LEAF){
     p.owner=s.id;p.u=((hit.x-s.left)/s.width).coerceIn(0f,1f);p.v=((hit.y-s.top)/s.height).coerceIn(0f,1f)
     p.x=hit.x;p.y=hit.y;p.vx=0f;p.vy=0f;p.age=0f;p.hold=120f+random.nextFloat()*120f;val desired=(if(wind>=0)0f else 180f)+random.nextFloat()*24f-12f
     p.restAngle=p.angle+(((desired-p.angle+180f)%360f+360f)%360f-180f);p.spin*=.3f;leafContacts++
    }else{
     val dot=p.vx*hit.nx+p.vy*hit.ny
     p.vx-=1.18f*dot*hit.nx;p.vy-=1.18f*dot*hit.ny
     p.x=hit.x+hit.nx*(p.radius+1);p.y=hit.y+hit.ny*(p.radius+1)
    }
   }
   if(p.owner==null&&(p.y>h+50||p.x< -250||p.x>w+250||p.age>25))bodies.remove(p)
  }
  caps.forEach{(_,pile)->for(i in pile.indices)pile[i]=(pile[i]-dt*(if(style.snow>0).004f else .055f)).coerceAtLeast(0f)}
  damage.forEach{it.age+=dt};damage.removeAll{it.age>10f}
  if(bolt?.let{it.age>it.profile.duration}==true)bolt=null
 }
 private fun releaseLeaf(p:Body,wind:Float,vertical:Float,s:RainCollisionWorld.Surface){
  p.x=if(wind>=0)s.right+p.radius+2 else s.left-p.radius-2;p.y=minOf(p.y,s.top-3f)
  p.owner=null;p.vx=wind*1.35f+if(wind>=0)70f else -70f;p.vy=vertical.coerceIn(-300f,300f)-40f
  p.x+=if(wind>=0)8f else -8f;p.y-=9f;p.age=0f;p.spin=if(wind>=0)480f else -480f;leafReleases++
 }
 companion object {
  /** Continuous swept collision against the four flats and circular corners. */
  fun sweep(s:RainCollisionWorld.Surface,x:Float,y:Float,toX:Float,toY:Float):Hit? {
   val dx=toX-x;val dy=toY-y;val r=s.r
   if(max(x,toX)<s.left||min(x,toX)>s.right||max(y,toY)<s.top||min(y,toY)>s.bottom)return null
   val contour=s.contour
   if(contour!=null) {
    if(max(x,toX)<s.left||min(x,toX)>s.right||max(y,toY)<s.top||min(y,toY)>s.bottom)return null
    val steps=ceil(max(abs(dx),abs(dy))/.5f).toInt().coerceIn(1,512)
    var outside=!contour.contains(x-s.left,y-s.top)
    for(i in 1..steps){val t=i.toFloat()/steps;val px=x+dx*t;val py=y+dy*t
     val inside=contour.contains(px-s.left,py-s.top)
     if(outside&&inside){val d=contour.nearest(px-s.left,py-s.top);val a=contour.point(d-.5f);val b=contour.point(d+.5f)
      val len=hypot(b.x-a.x,b.y-a.y).coerceAtLeast(.001f);var nx=(b.y-a.y)/len;var ny=-(b.x-a.x)/len
      if(dx*nx+dy*ny>0){nx=-nx;ny=-ny};return Hit(s.id,px,py,nx,ny,t).takeIf{it.y<s.clipBottom}
     }
     outside=!inside
    }
    return null
   }
   if(!listOf(x,y,toX,toY).all{it.isFinite()}||abs(dx)+abs(dy)<.00001f)return null
   var best:Hit?=null
   fun offer(t:Float,nx:Float,ny:Float){if(t in 0f..1f&&(best==null||t<best!!.fraction)&&dx*nx+dy*ny<0)
    best=Hit(s.id,x+dx*t,y+dy*t,nx,ny,t)}
   if(abs(dy)>.00001f){
    var t=(s.top-y)/dy;var px=x+dx*t;if(px in s.left+r..s.right-r)offer(t,0f,-1f)
    t=(s.bottom-y)/dy;px=x+dx*t;if(px in s.left+r..s.right-r)offer(t,0f,1f)
   }
   if(abs(dx)>.00001f){
    var t=(s.left-x)/dx;var py=y+dy*t;if(py in s.top+r..s.bottom-r)offer(t,-1f,0f)
    t=(s.right-x)/dx;py=y+dy*t;if(py in s.top+r..s.bottom-r)offer(t,1f,0f)
   }
   if(r>.001f)for(right in listOf(false,true))for(bottom in listOf(false,true)) {
    val cx=if(right)s.right-r else s.left+r;val cy=if(bottom)s.bottom-r else s.top+r
    val ox=x-cx;val oy=y-cy;val a=dx*dx+dy*dy;val b=2*(ox*dx+oy*dy);val c=ox*ox+oy*oy-r*r
    val d=b*b-4*a*c
    if(d>=0){val t=(-b-sqrt(d))/(2*a);val px=x+dx*t;val py=y+dy*t
     if((if(right)px>=cx else px<=cx)&&(if(bottom)py>=cy else py<=cy))offer(t,(px-cx)/r,(py-cy)/r)
    }
   }
   return best?.takeIf{it.y<s.clipBottom}
  }
 }
}
