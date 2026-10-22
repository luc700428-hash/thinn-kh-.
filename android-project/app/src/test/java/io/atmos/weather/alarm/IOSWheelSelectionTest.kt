package io.atmos.weather.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IOSWheelSelectionTest {
    @Test fun amPmHasSameStepAndBoundedFlingAsOtherColumns() {
        assertEquals(-41f,IOSWheelSelection.clampOffset(0,2,-410f,41f),.001f)
        assertEquals(0f,IOSWheelSelection.clampOffset(0,2,300f,41f),.001f)
        assertEquals(41f,IOSWheelSelection.clampOffset(1,2,300f,41f),.001f)
        assertEquals(0f,IOSWheelSelection.clampOffset(1,2,-300f,41f),.001f)
        assertEquals(1,IOSWheelSelection.afterSteps(0,2,-1))
        assertEquals(0,IOSWheelSelection.afterSteps(1,2,1))
        assertEquals(0,IOSWheelSelection.afterSteps(0,2,1))
        assertEquals(1,IOSWheelSelection.afterSteps(1,2,-1))
        assertEquals(1,IOSWheelSelection.predictedSteps(1,2,12f,130f,41f))
        assertEquals(-1,IOSWheelSelection.predictedSteps(0,2,-12f,-130f,41f))
        assertEquals(1,IOSWheelSelection.rowValue(0,2,1,0))
        assertNull(IOSWheelSelection.rowValue(0,2,-1,0))
        assertNull(IOSWheelSelection.rowValue(1,2,1,0))
    }
    @Test fun hourAndMinuteRemainCyclic() {
        assertEquals(11,IOSWheelSelection.afterSteps(0,12,1))
        assertEquals(0,IOSWheelSelection.afterSteps(11,12,-1))
        assertEquals(59,IOSWheelSelection.afterSteps(0,60,1))
        assertEquals(0,IOSWheelSelection.afterSteps(59,60,-1))
        assertEquals(59,IOSWheelSelection.rowValue(0,60,-1,0))
        assertEquals(-6560f,IOSWheelSelection.clampOffset(0,60,-9000f,41f),.001f)
    }
    @Test fun noonAndMidnightDoNotChangeOnMeridiemScrollIfAlreadySelected() {
        assertEquals(0,V25AlarmMeridiem.hour24(0,IOSWheelSelection.afterSteps(0,2,1)))
        assertEquals(12,V25AlarmMeridiem.hour24(0,IOSWheelSelection.afterSteps(0,2,-1)))
        assertEquals(12,V25AlarmMeridiem.hour24(0,IOSWheelSelection.afterSteps(1,2,-1)))
        assertEquals(0,V25AlarmMeridiem.hour24(0,IOSWheelSelection.afterSteps(1,2,1)))
    }
}
