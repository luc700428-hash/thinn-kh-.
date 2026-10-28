package io.atmos.weather.radar

import kotlin.math.*

/** Base maps can reveal streets; radar remains capped at the provider's native level. */
object RadarTilePolicy {
    const val RADAR_MAX_ZOOM=7
    const val BASE_MAX_ZOOM=18
    const val CAMERA_MAX_ZOOM=16.0
    const val RADAR_PIXELS=512
    const val MAX_VISIBLE_TILES=36
    const val BASE_MAX_VISIBLE_TILES=96
    fun valid(tile:RadarTile,maxZoom:Int)=tile.z in 0..maxZoom&&tile.x in 0 until (1 shl tile.z)&&tile.y in 0 until (1 shl tile.z)
    fun imageKey(frame:RadarFrame?,tile:RadarTile)=
        (frame?.let{"radar-hd-raw-512:${it.path}"}?:"base")+"/${tile.z}/${tile.x}/${tile.y}"
    fun imageUrl(frame:RadarFrame?,tile:RadarTile):String {
        require(valid(tile,if(frame==null)BASE_MAX_ZOOM else RADAR_MAX_ZOOM))
        if(frame==null)return "https://tile.openstreetmap.org/${tile.z}/${tile.x}/${tile.y}.png"
        require(Regex("/v2/radar/[A-Za-z0-9/_-]{1,150}").matches(frame.path))
        // 512px at the same geographic extent; raw cells preserve measured intensity.
        return "https://tilecache.rainviewer.com${frame.path}/$RADAR_PIXELS/${tile.z}/${tile.x}/${tile.y}/2/0_1.png"
    }
}

data class RadarCamera(val latitude:Double=23.75,val longitude:Double=120.95,val zoom:Double=6.5) {
    fun normalized()=RadarCamera((latitude.takeIf{it.isFinite()}?:23.75).coerceIn(-85.0,85.0),
        (((longitude.takeIf{it.isFinite()}?:120.95)+180)%360+360)%360-180,
        (zoom.takeIf{it.isFinite()}?:6.5).coerceIn(2.0,RadarTilePolicy.CAMERA_MAX_ZOOM))
    fun world(density:Float)=256.0*density*2.0.pow(zoom)
    fun pan(dx:Float,dy:Float,density:Float):RadarCamera {
        if(!dx.isFinite()||!dy.isFinite()||!density.isFinite()||density<=0)return this
        val size=world(density)
        return RadarCamera(RadarProjection.latitude((RadarProjection.y(latitude)-dy/size).coerceIn(0.0,1.0)),
            RadarProjection.longitude(RadarProjection.x(longitude)-dx/size),zoom).normalized()
    }
    fun zoomAt(factor:Float,x:Float,y:Float,width:Float,height:Float,density:Float):RadarCamera {
        if(!factor.isFinite()||factor<=0||!density.isFinite()||density<=0||!x.isFinite()||!y.isFinite()||!width.isFinite()||!height.isFinite())return this
        val next=(zoom+ln(factor.toDouble())/ln(2.0)).coerceIn(2.0,RadarTilePolicy.CAMERA_MAX_ZOOM)
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
    fun tiles(c:RadarCamera,width:Float,height:Float,density:Float,maxZoom:Int=RadarTilePolicy.RADAR_MAX_ZOOM):List<RadarTile> {
        if(!width.isFinite()||!height.isFinite()||!density.isFinite()||width<=0||height<=0||density<=0)return emptyList()
        val camera=c.normalized();val world=camera.world(density)
        val baseLayer=maxZoom>RadarTilePolicy.RADAR_MAX_ZOOM
        val budget=if(baseLayer)RadarTilePolicy.BASE_MAX_VISIBLE_TILES else RadarTilePolicy.MAX_VISIBLE_TILES
        fun at(z:Int):List<RadarTile> {
            val n=1 shl z;val tile=256*density*2.0.pow(camera.zoom-z)
            val left=floor((x(camera.longitude)*world-width/2)/tile).toInt()
            val right=floor((x(camera.longitude)*world+width/2-.01)/tile).toInt()
            val top=floor((y(camera.latitude)*world-height/2)/tile).toInt().coerceAtLeast(0)
            val bottom=floor((y(camera.latitude)*world+height/2-.01)/tile).toInt().coerceAtMost(n-1)
            if(bottom<top)return emptyList()
            if((right.toLong()-left+1)*(bottom-top+1)>budget)return emptyList()
            return (top..bottom).flatMap {ty->(left..right).map {tx->RadarTile(z,Math.floorMod(tx,n),ty,tx)}}
        }
        var z=(if(baseLayer)ceil(camera.zoom+log2(density.toDouble().coerceAtLeast(1.0))) else floor(camera.zoom)).toInt().coerceIn(0,maxZoom.coerceIn(0,RadarTilePolicy.BASE_MAX_ZOOM))
        // Very wide displays use a coarser complete map, never a truncated bottom row.
        while(z>0){val result=at(z);if(result.isNotEmpty())return result;z--}
        return at(0)
    }
}
