package io.atmos.weather.ui

import kotlin.math.*
import kotlin.random.Random

/** Bounded, deterministic surface-water simulation in dp, independent of forecast data.
 * Small beads adhere; collection/merging releases larger drops under gravity.
 */
internal class WeatherSurfacePhysics(seed:Int=7349) {
    data class Drop(var x:Float,var y:Float,var radius:Float,var speed:Float,var age:Float=0f)
    data class Splash(var x:Float,var y:Float,var vx:Float,var vy:Float,var life:Float)
    val drops=ArrayList<Drop>(36)
    val splashes=ArrayList<Splash>(24)
    private val random=Random(seed)
    private var last=Float.NaN
    private var spawn=0f
    fun advance(time:Float,width:Float,height:Float,strength:Float,raining:Boolean) {
        if(width<=0f || height<=0f)return
        if(!last.isFinite()) {last=time
            repeat(12) {drops+=Drop(random.nextFloat()*width,random.nextFloat()*height,1.3f+random.nextFloat()*2.8f,0f)}
        }
        val dt=(time-last).coerceIn(0f,.05f);last=time
        if(dt<=0f)return
        spawn+=dt
        val interval=.26f-.18f*strength.coerceIn(0f,1f)
        if(raining && spawn>=interval) {
            spawn=0f
            if(drops.size<32)drops+=Drop(random.nextFloat()*width,random.nextFloat()*height*.12f,
                1.5f+random.nextFloat()*3.6f,random.nextFloat()*9f)
            if(splashes.size<18) {
                val x=12f+random.nextFloat()*(width-24f).coerceAtLeast(1f)
                repeat(3) {splashes+=Splash(x,1f,(random.nextFloat()-.5f)*55f,-12f-random.nextFloat()*30f,.45f)}
            }
        }
        drops.forEach {drop ->
            drop.age+=dt
            // Surface tension holds the bead until its mass exceeds adhesion.
            if(raining)drop.radius=(drop.radius+dt*.07f*strength).coerceAtMost(7.5f)
            else drop.radius-=dt*.14f
            if(drop.radius>3.5f) drop.speed=(drop.speed+dt*(25f+drop.radius*8f)).coerceAtMost(170f)
            else drop.speed*=exp(-dt*4f)
            drop.y+=drop.speed*dt
            drop.x+=sin(drop.y*.024f+drop.age*.7f)*dt*drop.speed*.025f
        }
        // Coalescence conserves area and vertical momentum; no unbounded allocations.
        for(i in drops.lastIndex downTo 1) {
            val a=drops[i]
            for(j in 0 until i) {
                val b=drops[j];val dx=a.x-b.x;val dy=a.y-b.y
                if(dx*dx+dy*dy<(a.radius+b.radius).pow(2)*.52f) {
                    val aa=a.radius*a.radius;val bb=b.radius*b.radius;val mass=aa+bb
                    b.x=(a.x*aa+b.x*bb)/mass;b.y=maxOf(a.y,b.y)
                    b.speed=(a.speed*aa+b.speed*bb)/mass
                    b.radius=sqrt(mass).coerceAtMost(8f);drops.removeAt(i);break
                }
            }
        }
        drops.removeAll {it.y>height+12f || it.radius<.5f}
        splashes.forEach {it.life-=dt;it.vy+=95f*dt;it.x+=it.vx*dt;it.y+=it.vy*dt}
        splashes.removeAll {it.life<=0f || it.y>12f}
    }
}
