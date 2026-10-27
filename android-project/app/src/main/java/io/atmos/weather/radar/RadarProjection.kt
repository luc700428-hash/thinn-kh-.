package io.atmos.weather.radar
import kotlin.math.*
data class RadarCamera(val latitude:Double=23.75,val longitude:Double=120.95,val zoom:Double=6.5) {
    fun normalized()=RadarCamera((latitude.takeIf{it.isFinite()}?:23.75).coerceIn(-85.0,85.0),(((longitude.takeIf{it.isFinite()}?:120.95)+180)%360+360)%360-180,(zoom.takeIf{it.isFinite()}?:6.5).coerceIn(2.0,7.0))
    fun world(density:Float)=256.0*density*2.0.pow(zoom)
    fun pan(dx:Float,dy:Float,density:Float):RadarCamera {
        if(!dx.isFinite()||!dy.isFinite()||!density.isFinite()||density<=0)return this
        val size=world(density)
        return RadarCamera(RadarProjection.latitude((RadarProjection.y(latitude)-dy/size).coerceIn(0.0,1.0)),
            RadarProjection.longitude(RadarProjection.x(longitude)-dx/size),zoom).normalized()
    }
    fun zoomAt(factor:Float,x:Float,y:Float,width:Float,height:Float,density:Float):RadarCamera {
        if(!factor.isFinite()||factor<=0||!density.isFinite()||density<=0||!x.isFinite()||!y.isFinite())return this
        val next=(zoom+ln(factor.toDouble())/ln(2.0)).coerceIn(2.0,7.0)
        val before=world(density);val after=256*density*2.0.pow(next)
        val cx=RadarProjection.x(longitude)+(x-width/2)*(1/before-1/after)
        val cy=RadarProjection.y(latitude)+(y-height/2)*(1/before-1/after)
        return RadarCamera(RadarProjection.latitude(cy.coerceIn(0.0,1.0)),RadarProjection.longitude(cx),next).normalized()
    }
}
data class RadarTile(val z:Int,val x:Int,val y:Int,val column:Int)
object RadarProjection {
    fun x(lon:Double)=(lon+180)/360
    fun y(lat:Double):Double {val r=Math.toRadians(lat.coerceIn(-85.0,85.0));return (1-ln(tan(r)+1/cos(r))/PI)/2}
    fun latitude(y:Double)=Math.toDegrees(atan(sinh(PI*(1-2*y))))
    fun longitude(x:Double)=((x*360)%360+360)%360-180
    fun tiles(c:RadarCamera,width:Float,height:Float,density:Float):List<RadarTile> {
        if(!width.isFinite()||!height.isFinite()||!density.isFinite()||width<=0||height<=0||density<=0)return emptyList()
        val z=floor(c.zoom).toInt().coerceIn(2,7);val n=1 shl z
        val tile=256*density*2.0.pow(c.zoom-z);val world=c.world(density)
        val left=floor((x(c.longitude)*world-width/2)/tile).toInt()
        val top=floor((y(c.latitude)*world-height/2)/tile).toInt()
        val cols=ceil(width/tile).toInt()+2;val rows=ceil(height/tile).toInt()+2
        return (top until top+rows).flatMap {ty->(left until left+cols).mapNotNull {tx->
            if(ty !in 0 until n)null else RadarTile(z,Math.floorMod(tx,n),ty,tx)
        }}.take(36)
    }
}
