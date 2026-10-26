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
    var impacts=0;private set
    var emittedDrips=0;private set
    private var budget=0f;private var remainder=0f
    private val random=Random(seed)
    val hasMotion get()=air.isNotEmpty() || sparks.isNotEmpty() || surfaces.values.any {it.beads.isNotEmpty() || it.wet>.005f}
    fun surface(id:Int,left:Float,top:Float,right:Float,bottom:Float,radius:Float) {
        if(!listOf(left,top,right,bottom,radius).all {it.isFinite()} || right<=left || bottom<=top)return
        val old=surfaces[id]
        if(old==null)surfaces[id]=Surface(id,left,top,right,bottom,radius)
        else {old.left=left;old.top=top;old.right=right;old.bottom=bottom;old.radius=radius}
    }
    fun remove(id:Int){surfaces.remove(id)}
    fun clearAir(){air.clear();sparks.clear()}
    private fun visible(s:Surface)=s.bottom>0 && s.top<height && s.right>0 && s.left<width
    fun step(seconds:Float,intensity:Float,windDpPerSecond:Float) {
        if(!seconds.isFinite() || seconds<=0 || width<=0 || height<=0)return
        val rain=if(intensity.isFinite())intensity.coerceIn(0f,1f) else 0f
        val wind=if(windDpPerSecond.isFinite())windDpPerSecond.coerceIn(-140f,140f) else 0f
        remainder+=seconds.coerceAtMost(.05f)
        val dt=1f/240f
        var guard=0
        while(remainder+1e-7f>=dt && guard++<13) {integrate(dt,rain,wind);remainder-=dt}
    }
    private fun integrate(dt:Float,intensity:Float,wind:Float) {
        if(intensity>0) {
            budget+=dt*(10f+intensity*58f)*(width/400f).coerceIn(.5f,2f)
            while(budget>=1f) {
                budget-=1f
                if(air.size<128) {
                    val x=random.nextFloat()*(width+100f)-50f
                    air+=Air(x,-25f-random.nextFloat()*20f,wind*(.8f+random.nextFloat()*.4f),
                        410f+intensity*390f+random.nextFloat()*110f,.65f+random.nextFloat()*.85f)
                }
            }
        }
        for(i in air.lastIndex downTo 0) {
            val drop=air[i];val ox=drop.x;val oy=drop.y
            drop.vy=(drop.vy+820f*dt).coerceAtMost(if(drop.drip)1000f else 1150f)
            drop.x+=drop.vx*dt;drop.y+=drop.vy*dt
            var hit:Surface?=null;var fraction=2f;var hitX=0f;var hitY=0f
            surfaces.values.forEach {s ->
                if(!visible(s) || oy>s.top+s.r || drop.y<s.top || drop.vy<=0)return@forEach
                // Swept top-edge crossing: no tunnelling at high rain speed or low frame rate.
                val t=((s.top-oy)/(drop.y-oy).coerceAtLeast(.0001f)).coerceIn(0f,1f)
                val x=ox+(drop.x-ox)*t
                if(x<s.left+s.r*.35f || x>s.right-s.r*.35f)return@forEach
                val roof=roofY(s,x)
                if(oy<=roof && drop.y>=roof) {
                    val ft=(roof-oy)/(drop.y-oy).coerceAtLeast(.0001f)
                    if(ft<fraction) {hit=s;fraction=ft;hitX=ox+(drop.x-ox)*ft;hitY=roof}
                }
            }
            if(hit!=null) {
                impact(hit!!,hitX,hitY,drop);air.removeAt(i)
            } else if(surfaces.values.any {s ->visible(s) && drop.x in s.left..s.right && drop.y>roofY(s,drop.x) && drop.y<s.bottom}) {
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
    private fun impact(s:Surface,x:Float,y:Float,drop:Air) {
        impacts++;s.wet=(s.wet+.13f).coerceAtMost(1f)
        val mass=drop.mass
        addBead(s,x,mass*.68f,18f+abs(drop.vx)*.3f)
        val count=if(drop.drip)5 else 3
        repeat(count) {n ->
            if(sparks.size<128) {
                val sign=if(n%2==0)1 else -1
                sparks+=Spark(x,y-.5f,sign*(28f+random.nextFloat()*90f)+drop.vx*.18f,
                    -(75f+random.nextFloat()*115f+if(drop.drip)30f else 0f),.6f,s.id,mass*.32f/count)
            }
        }
    }
    private fun addBead(s:Surface,x:Float,mass:Float,speed:Float) {
        val right=x>(s.left+s.right)/2
        val roofDistance=if(right)s.right-s.r-x else x-(s.left+s.r)
        val point=if(roofDistance>=0) -roofDistance else asin((-roofDistance/s.r.coerceAtLeast(.1f)).coerceIn(0f,1f))*s.r
        val nearby=s.beads.firstOrNull {it.rightEdge==right && abs(it.s-point)<5f}
        if(nearby!=null)nearby.mass=(nearby.mass+mass).coerceAtMost(28f)
        else if(s.beads.size<24)s.beads+=Bead(right,point,mass.coerceAtLeast(.2f),speed)
    }
    fun roofY(s:Surface,x:Float):Float {
        val r=s.r
        val dx=when {x<s.left+r->x-(s.left+r);x>s.right-r->x-(s.right-r);else->0f}
        return if(dx==0f || r<=0f)s.top else s.top+r-sqrt((r*r-dx*dx).coerceAtLeast(0f))
    }
    fun radius(b:Bead)=sqrt(b.mass)*1.55f
    fun beadPoint(s:Surface,b:Bead):Point {
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
