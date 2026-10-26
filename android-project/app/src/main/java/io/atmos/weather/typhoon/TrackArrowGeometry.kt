package io.atmos.weather.typhoon
import kotlin.math.hypot
internal object TrackArrowGeometry {
    data class Point(val x:Float,val y:Float)
    fun head(ax:Float,ay:Float,bx:Float,by:Float,size:Float):List<Point> {
        val dx=bx-ax;val dy=by-ay;val distance=hypot(dx,dy)
        if(!distance.isFinite() || distance<size*3 || size<=0)return emptyList()
        val ux=dx/distance;val uy=dy/distance
        val x=ax+dx*.62f;val y=ay+dy*.62f
        return listOf(Point(x+ux*size,y+uy*size),Point(x-ux*size-uy*size*.65f,y-uy*size+ux*size*.65f),
            Point(x-ux*size+uy*size*.65f,y-uy*size-ux*size*.65f))
    }
}
