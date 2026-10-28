package io.atmos.weather.radar

import org.junit.Assert.*
import org.junit.Test

class RadarSmoothCameraTest {
    @Test fun streetZoomMovesByFractionalPixelsWithoutLargeWorldRounding() {
        for(zoom in listOf(6.5,12.0,16.0)) {
            val c=RadarCamera(25.033,121.565,zoom)
            val tile=RadarProjection.tiles(c,1080f,1800f,3f,RadarTilePolicy.BASE_MAX_ZOOM).first()
            val before=RadarTileGeometry.bounds(c,tile,1080f,1800f,3f)
            val after=RadarTileGeometry.bounds(c.pan(.25f,.125f,3f),tile,1080f,1800f,3f)
            assertEquals(.25f,after.left-before.left,.002f)
            assertEquals(.125f,after.top-before.top,.002f)
        }
    }
    @Test fun denseScreensLoadSourceTexelsInsteadOfStretchingLogicalTiles() {
        val c=RadarCamera(25.033,121.565,14.0)
        val tiles=RadarProjection.tiles(c,1080f,1800f,3f,RadarTilePolicy.BASE_MAX_ZOOM)
        assertTrue(tiles.all{it.z==16})
        assertTrue(RadarTileGeometry.bounds(c,tiles.first(),1080f,1800f,3f).width<=256f)
        assertTrue(RadarProjection.tiles(c,1080f,1800f,3f).all{it.z==7})
    }
    @Test fun fineTileArrivalKeepsThePreviouslyVisibleMapAndBoundsMemory() {
        val old=RadarTile(7,107,54,107);val next=RadarTile(8,214,108,214)
        var layer=RadarTileWindow.merge(mapOf(old to "old"),next,"new")
        assertEquals("old",layer[old]);assertEquals("new",layer[next])
        repeat(200){i->layer=RadarTileWindow.merge(layer,RadarTile(8,i,100,i),"tile")}
        assertEquals(RadarTilePolicy.BASE_MAX_VISIBLE_TILES,layer.size)
    }
}
