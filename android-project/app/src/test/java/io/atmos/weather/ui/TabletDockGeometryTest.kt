package io.atmos.weather.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class TabletDockGeometryTest {
    @Test fun everyEdgeReservesASeparateLaneAtTabletAndFoldedPaneSizes() {
        for(pane in listOf(Rect(0f,0f,1280f,800f),Rect(0f,0f,768f,1024f),Rect(0f,0f,600f,960f),
            Rect(526f,0f,1000f,900f),Rect(0f,506f,1000f,1000f),Rect(0f,0f,1200f,360f),Rect(0f,0f,260f,200f)))
            for(scale in listOf(1f,1.5f,2f))for(position in TabletDockPosition.entries) {
                val result=TabletDockGeometry.place(pane,scale,position)
                for(bounds in listOf(result.dock,result.content)) {
                    assertTrue("$position $pane $bounds",bounds.left>=pane.left&&bounds.top>=pane.top&&bounds.right<=pane.right&&bounds.bottom<=pane.bottom)
                    assertTrue(bounds.width>0&&bounds.height>0)
                }
                assertFalse("Dock hides content: $position $pane",result.content.overlaps(result.dock))
                if(result.position.vertical)assertTrue(result.dock.height>=4*44f)
                else assertTrue(result.dock.width>=4*44f)
            }
    }
    @Test fun aNarrowOrShortPaneFallsBackWithoutChangingTheUsersPreference() {
        assertEquals(TabletDockPosition.BOTTOM,TabletDockGeometry.place(Rect(0f,0f,350f,800f),1f,TabletDockPosition.RIGHT).position)
        assertEquals(TabletDockPosition.BOTTOM,TabletDockGeometry.place(Rect(0f,0f,1000f,240f),1f,TabletDockPosition.LEFT).position)
        assertEquals(TabletDockPosition.RIGHT,TabletDockGeometry.place(Rect(0f,0f,1000f,800f),1f,TabletDockPosition.RIGHT).position)
        assertEquals(TabletDockPosition.RIGHT,TabletDockPosition.fromId(null))
        assertEquals(TabletDockPosition.RIGHT,TabletDockPosition.fromId("invalid"))
    }
    @Test fun resizingAndHingeChangesCannotProduceNegativePadding() {
        val old=Rect(800f,100f,1250f,790f)
        for(pane in listOf(Rect(0f,0f,400f,800f),Rect(526f,0f,1000f,900f),Rect(0f,506f,1000f,1000f),Rect(0f,0f,1f,1f))) {
            val fitted=TabletDockGeometry.fit(old,pane)
            assertTrue(fitted.left>=pane.left&&fitted.top>=pane.top&&fitted.right<=pane.right&&fitted.bottom<=pane.bottom)
        }
    }
    @Test fun phoneAndNarrowOuterScreensKeepTheirExistingNavigationMode() {
        assertFalse(WorkspaceLayout(1280f,800f,1f,device=DeviceKind.PHONE).tablet)
        assertFalse(WorkspaceLayout(400f,900f,1f,device=DeviceKind.FOLDABLE).tablet)
        assertTrue(WorkspaceLayout(768f,1024f,1f,device=DeviceKind.TABLET).tablet)
        assertTrue("Keyboard must not turn a tablet into phone navigation",WorkspaceLayout(1200f,160f,1f,device=DeviceKind.TABLET).tablet)
    }
}
