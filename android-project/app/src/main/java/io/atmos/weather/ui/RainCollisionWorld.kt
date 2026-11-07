package io.atmos.weather.ui

import kotlin.math.*
import kotlin.random.Random

/** One viewport-space water simulation. Units are dp / seconds, never frames.
 * Rain, roof contact, splash return, rim flow, pendant neck and falling drip share mass/positions.
 */
internal class RainCollisionWorld(seed:Int=7349) {
    data class Surface(val id:Int,var left:Float,var top:Float,var right:Float,var bottom:Float,var radius:Float) {
        val width get()=right-left
        val height get()=bottom-top
        val r get()=radius.coerceIn(0f,min(width,height)/2)
        var contour:CollisionContour?=null
        var active=true;var clipBottom=Float.POSITIVE_INFINITY
        var shiftX=0f;var shiftY=0f
        var wet=0f
        val beads=ArrayList<Bead>(28)
    }
    data class Air(var x:Float,var y:Float,var vx:Float,var vy:Float,val radius:Float,val drip:Boolean=false,val mass:Float=radius*radius)
    data class Spark(var x:Float,var y:Float,var vx:Float,var vy:Float,var life:Float,val owner:Int,val mass:Float)
    /** s<0 is on the flat roof; then corner, vertical edge, lower corner, pendant. */
    data class Bead(val rightEdge:Boolean,var s:Float,var mass:Float,var speed:Float,var neckAge:Float=0f)
    data class Point(val x:Float,val y:Float)
    val surfaces=LinkedHashMap<Int,Surface>()
    val air=ArrayList<Air>(144)
    val sparks=ArrayList<Spark>(144)
    var width=0f;var height=0f
    var speedScale=1f;var radiusScale=1f;var gusts=false
    private var elapsed=0f
    var impacts=0;private set
    var emittedDrips=0;private set
    private var budget=0f;private var remainder=0f
    private val random=Random(seed)
    val hasMotion get()=air.isNotEmpty() || sparks.isNotEmpty() || surfaces.values.any {it.beads.isNotEmpty() || it.wet>.005f}
    fun surface(id:Int,left:Float,top:Float,right:Float,bottom:Float,radius:Float) {
        if(!listOf(left,top,right,bottom,radius).all {it.isFinite()} || right<=left || bottom<=top)return
        val old=surfaces[id]
        if(old==null)surfaces[id]=Surface(id,left,top,right,bottom,radius)
        else {old.shiftX+=left-old.left;old.shiftY+=top-old.top;old.left=left;old.top=top;old.right=right;old.bottom=bottom;old.radius=radius}
    }
    fun remove(id:Int){surfaces.remove(id)}
    fun clearAir(){air.clear();sparks.clear()}
    private fun visible(s:Surface)=s.active&&s.top<s.clipBottom&&s.bottom>0 && s.top<height && s.right>0 && s.left<width
    fun step(seconds:Float,intensity:Float,windDpPerSecond:Float) {
        if(!seconds.isFinite() || seconds<=0 || width<=0 || height<=0)return
        val rain=if(intensity.isFinite())intensity.coerceIn(0f,1f) else 0f
        val wind=if(windDpPerSecond.isFinite())windDpPerSecond.coerceIn(-620f,620f) else 0f
        val frameDt=seconds.coerceAtMost(.05f)
        surfaces.values.forEach {s->
            val dx=s.shiftX;val dy=s.shiftY;s.shiftX=0f;s.shiftY=0f
            val speed=hypot(dx,dy)/frameDt
            if(speed>130f&&s.beads.isNotEmpty()) {
                val count=ceil(s.beads.size*((speed-60)/600).coerceIn(.15f,1f)).toInt()
                repeat(count.coerceAtMost(s.beads.size)) {
                    val bead=s.beads.removeAt(s.beads.lastIndex);val at=beadPoint(s,bead)
                    if(air.size<138) {
                        val mass=bead.mass/2;val r=sqrt(mass)*1.55f
                        for(sign in listOf(-1,1))air+=Air(at.x-dx*.3f+sign*r,at.y-(if(dy>0)dy else 0f)-r*1.8f,
                            (dx/frameDt*.4f+sign*35).coerceIn(-650f,650f),(dy/frameDt*.35f-90).coerceIn(-850f,650f),r,true,mass)
                        emittedDrips+=2
                    }
                }
            }
        }
        remainder+=frameDt
        val dt=1f/240f
        var guard=0
        while(remainder+1e-7f>=dt && guard++<13) {integrate(dt,rain,wind);remainder-=dt}
    }
    private fun integrate(dt:Float,intensity:Float,baseWind:Float) {
        elapsed+=dt
        val wind=if(gusts)WeatherPhysicsStyle.windPixels(baseWind/3.2f,true,elapsed) else baseWind
        if(intensity>0) {
            budget+=dt*(10f+intensity*58f)*(width/400f).coerceIn(.5f,2f)
            while(budget>=1f) {
                budget-=1f
                if(air.size<128) {
                    val x=random.nextFloat()*(width+100f)-50f
                    air+=Air(x,-25f-random.nextFloat()*20f,wind*(.8f+random.nextFloat()*.4f),
                        (410f+intensity*390f+random.nextFloat()*110f)*speedScale,(.65f+random.nextFloat()*.85f)*radiusScale)
                }
            }
        }
        for(i in air.lastIndex downTo 0) {
            val drop=air[i];val ox=drop.x;val oy=drop.y
            drop.vx+=(wind*(if(drop.drip).25f else 1f)-drop.vx)*dt*2.3f
            drop.vy=(drop.vy+820f*dt).coerceAtMost((if(drop.drip)1000f else 1150f)*speedScale)
            drop.x+=drop.vx*dt;drop.y+=drop.vy*dt
            var hit:Surface?=null;var fraction=2f;var hitX=0f;var hitY=0f;var normalX=0f;var normalY= -1f
            surfaces.values.forEach {s ->
                if(!visible(s))return@forEach
                val contact=WeatherSurfacePhysics.sweep(s,ox,oy,drop.x,drop.y)
                if(contact!=null&&contact.fraction<fraction) {
                    hit=s;fraction=contact.fraction;hitX=contact.x;hitY=contact.y;normalX=contact.nx;normalY=contact.ny
                }
            }
            if(hit!=null) {
                impact(hit!!,hitX,hitY,drop,normalX,normalY);air.removeAt(i)
            } else if(surfaces.values.any {s ->visible(s) && drop.x in s.left..s.right && (s.contour?.contains(drop.x-s.left,drop.y-s.top) ?: (drop.y>roofY(s,drop.x) && drop.y<s.bottom))}) {
                // A scrolling card may move under an existing airborne drop; don't draw through its face.
                air.removeAt(i)
            } else if(drop.y>height+60 || drop.x< -180 || drop.x>width+180)air.removeAt(i)
        }
        for(i in sparks.lastIndex downTo 0) {
            val spark=sparks[i];val oldY=spark.y
            spark.life-=dt;spark.vy+=760f*dt;spark.x+=spark.vx*dt;spark.y+=spark.vy*dt
            val home=surfaces[spark.owner]
            val landed=home!=null && spark.vy>0 && spark.x in home.left..home.right &&
                oldY<=roofY(home,spark.x) && spark.y>=roofY(home,spark.x)
            if(landed) {addBead(home!!,spark.x,spark.mass,9f);sparks.removeAt(i)}
            else if(spark.life<=0 || spark.y>height+40)sparks.removeAt(i)
        }
        surfaces.values.forEach {s ->
            if(!visible(s)) {s.beads.clear();s.wet=0f;return@forEach}
            s.wet=(s.wet-dt*.035f).coerceAtLeast(0f)
            val bottom=PI.toFloat()*s.r+(s.height-2*s.r).coerceAtLeast(0f)
            for(i in s.beads.lastIndex downTo 0) {
                val bead=s.beads[i]
                val outline=s.contour
                if(outline!=null) {
                    val p=outline.point(bead.s);val sign=if(bead.rightEdge)1 else -1
                    val next=outline.point(bead.s+sign*1.2f)
                    if(next.y<p.y && p.y>outline.height*.60f) {
                        bead.neckAge+=dt
                        if(bead.neckAge>.27f) {
                            if(air.size<140)air+=Air(s.left+p.x,s.top+p.y+radius(bead)*1.8f,wind*.16f,65f,radius(bead),true,bead.mass)
                            emittedDrips++;s.beads.removeAt(i)
                        }
                    }else{
                        bead.speed=(bead.speed+dt*(18f+120f*abs(next.y-p.y))).coerceAtMost(155f)
                        bead.s+=sign*bead.speed*dt
                    }
                    continue
                }
                // Impact starts a thin surface film; gravity accelerates it down the curved rim.
                val onSide=bead.s>=s.r*PI.toFloat()/2
                bead.speed=(bead.speed+dt*(if(onSide)100f+bead.mass*14f else 13f+bead.mass*13f)).coerceAtMost(220f)
                bead.s=(bead.s+bead.speed*dt).coerceAtMost(bottom)
                bead.mass=(bead.mass-dt*.025f).coerceAtLeast(.15f)
                if(bead.s>=bottom-.001f) {
                    bead.neckAge+=dt
                    if(bead.neckAge>.23f+.18f/(1f+bead.mass)) {
                        val p=beadPoint(s,bead)
                        if(air.size<140)air+=Air(p.x,p.y+radius(bead)*1.7f,wind*.15f,75f,radius(bead),true,bead.mass)
                        emittedDrips++;s.beads.removeAt(i)
                    }
                }
            }
            // Same-edge coalescence conserves water mass and momentum.
            for(i in s.beads.lastIndex downTo 1) {
                val a=s.beads[i]
                for(j in 0 until i) {
                    val b=s.beads[j]
                    if(a.rightEdge==b.rightEdge && abs(a.s-b.s)<(radius(a)+radius(b))*.8f) {
                        val sum=a.mass+b.mass
                        b.speed=(a.speed*a.mass+b.speed*b.mass)/sum;b.s=max(a.s,b.s)
                        b.mass=sum.coerceAtMost(28f);s.beads.removeAt(i);break
                    }
                }
            }
        }
    }
    private fun impact(s:Surface,x:Float,y:Float,drop:Air,nx:Float,ny:Float) {
        impacts++;s.wet=(s.wet+.13f).coerceAtMost(1f)
        val mass=drop.mass
        addBead(s,x,mass*.68f,18f+abs(drop.vx)*.3f,y)
        val count=if(drop.drip)5 else 3
        repeat(count) {n ->
            if(sparks.size<128) {
                val sign=if(n%2==0)1 else -1
                val tangent=sign*(28f+random.nextFloat()*90f)
                val rebound=75f+random.nextFloat()*115f+if(drop.drip)30f else 0f
                sparks+=Spark(x+nx*.5f,y+ny*.5f,nx*rebound-ny*tangent+drop.vx*.18f,
                    ny*rebound+nx*tangent,.6f,s.id,mass*.32f/count)
            }
        }
    }
    private fun addBead(s:Surface,x:Float,mass:Float,speed:Float,hitY:Float=roofY(s,x)) {
        s.contour?.let {shape->
            val at=shape.nearest(x-s.left,hitY-s.top)
            val forward=shape.point(at+1f);val backward=shape.point(at-1f)
            if(s.beads.size<24)s.beads+=Bead(forward.y>backward.y,at,mass.coerceAtLeast(.2f),speed)
            return
        }
        val right=x>(s.left+s.right)/2
        val roofDistance=if(right)s.right-s.r-x else x-(s.left+s.r)
        val point=if(hitY>s.top+s.r)PI.toFloat()*s.r/2+(hitY-s.top-s.r).coerceAtMost((s.height-2*s.r).coerceAtLeast(0f))
            else if(roofDistance>=0) -roofDistance else asin((-roofDistance/s.r.coerceAtLeast(.1f)).coerceIn(0f,1f))*s.r
        val nearby=s.beads.firstOrNull {it.rightEdge==right && abs(it.s-point)<5f}
        if(nearby!=null)nearby.mass=(nearby.mass+mass).coerceAtMost(28f)
        else if(s.beads.size<24)s.beads+=Bead(right,point,mass.coerceAtLeast(.2f),speed)
    }
    fun roofY(s:Surface,x:Float):Float {
        s.contour?.let{return s.top+it.roof(x-s.left)}
        val r=s.r
        val dx=when {x<s.left+r->x-(s.left+r);x>s.right-r->x-(s.right-r);else->0f}
        return if(dx==0f || r<=0f)s.top else s.top+r-sqrt((r*r-dx*dx).coerceAtLeast(0f))
    }
    fun radius(b:Bead)=sqrt(b.mass)*1.55f
    fun beadPoint(s:Surface,b:Bead):Point {
        s.contour?.let{val p=it.point(b.s);return Point(s.left+p.x,s.top+p.y)}
        val r=s.r.coerceAtLeast(.1f);val sign=if(b.rightEdge)1f else -1f
        val anchor=if(b.rightEdge)s.right-r else s.left+r
        val q=(PI/2).toFloat()*r;val side=(s.height-2*r).coerceAtLeast(0f)
        if(b.s<0)return Point(anchor+sign*b.s,s.top)
        if(b.s<q) {val a= -(PI/2).toFloat()+sign*b.s/r;return Point(anchor+r*cos(a),s.top+r+r*sin(a))}
        if(b.s<q+side)return Point(if(b.rightEdge)s.right else s.left,s.top+r+b.s-q)
        val a=if(b.rightEdge)(b.s-q-side)/r else PI.toFloat()-(b.s-q-side)/r
        return Point(anchor+r*cos(a),s.bottom-r+r*sin(a))
    }
}
