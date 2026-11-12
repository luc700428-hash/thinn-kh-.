package io.atmos.weather.next
import org.junit.Assert.*
import org.junit.Test
class DockLiquidGeometryTest {
 @Test fun restingLensesMatchTheInnerDockHeightAcrossDensitiesAndAxes() {
  for(d in listOf(1f,2.75f))for(vertical in listOf(false,true))for(size in listOf(296f to 78f,360f to 110f,560f to 90f)) {
   val g=DockLiquidGeometry.shape(size.first*d,size.second*d,d,4,1f,1f,vertical)!!
   assertEquals((size.second-(if(vertical)12f else 10f))*d,g.height,.001f)
   assertEquals(minOf(g.width,g.height)/2,g.cornerRadius,.001f)
   assertTrue(g.width<((size.first-(if(vertical)10f else 12f))*d)/4)
  }
 }
 @Test fun touchLiftsAndPullingDeformsTheMaterialWithoutMovingTheHitCell() {
  val a=DockLiquidGeometry.shape(360f,78f,1f,4,1f,1f)!!
  val b=DockLiquidGeometry.shape(360f,78f,1f,4,1f,1f,engagement=1f)!!
  val c=DockLiquidGeometry.shape(360f,78f,1f,4,1.8f,1f,engagement=1f)!!
  assertTrue(b.height>a.height);assertTrue(c.width>b.width&&c.height<b.height)
  assertEquals(a.headX,b.headX,0f);assertTrue(c.tailX>a.tailX)
 }
 @Test fun overshootAndFastJumpsStayInsideTheDockAndNeverHighlightEveryTab() {
  val g=DockLiquidGeometry.shape(360f,78f,1f,4,-4f,8f)!!
  assertTrue(g.headX-g.width/2>=5);assertTrue(g.tailX+g.width/2<=355)
  val jump=DockLiquidGeometry.shape(360f,78f,1f,4,3f,0f,engagement=1f)!!
  assertTrue(jump.headX-jump.tailX+jump.width<180f)
 }
 @Test fun densityScalesMaterialAndNeckTogether() {
  val a=DockLiquidGeometry.shape(360f,78f,1f,4,1.8f,1f)!!
  val b=DockLiquidGeometry.shape(990f,214.5f,2.75f,4,1.8f,1f)!!
  assertEquals(a.width*2.75f,b.width,.001f);assertEquals(a.height*2.75f,b.height,.001f)
 }
 @Test fun invalidMeasurementsAreNotPainted() {
  assertNull(DockLiquidGeometry.shape(0f,68f,1f,4,0f,0f));assertNull(DockLiquidGeometry.shape(360f,68f,1f,0,0f,0f))
  assertNull(DockLiquidGeometry.shape(360f,68f,1f,4,Float.NaN,0f))
 }
}
