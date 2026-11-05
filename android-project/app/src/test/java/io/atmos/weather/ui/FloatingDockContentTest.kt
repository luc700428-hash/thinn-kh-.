package io.atmos.weather.ui
import org.junit.Assert.*
import org.junit.Test

class FloatingDockContentTest {
    @Test fun fourDestinationsFormACompactCapsule() {
        val length=FloatingDockContent.length(4,1f)
        assertTrue(length in 280f..330f);assertTrue(length<560f*.65f)
        assertTrue(FloatingDockContent.length(3,1f)<length)
    }
    @Test fun largerReadingSizesGrowCellsWithoutStretchingToAWindow() {
        val values=listOf(1f,1.5f,2f,3f).map{FloatingDockContent.length(4,it)}
        assertTrue(values.zipWithNext().all{(a,b)->b>a})
        for(scale in listOf(1f,1.5f,2f))assertEquals(2f,FloatingDockContent.length(4,scale)-FloatingDockContent.length(4,scale,true),.01f)
    }
    @Test fun invalidScaleAndEmptyActionListsHaveFiniteSafeSizes() {
        for(scale in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY))assertEquals(FloatingDockContent.length(4,1f),FloatingDockContent.length(4,scale),.01f)
        assertTrue(FloatingDockContent.length(0,1f)>44f)
        assertEquals(FloatingDockContent.length(4,3f),FloatingDockContent.length(4,20f),.01f)
    }
}
