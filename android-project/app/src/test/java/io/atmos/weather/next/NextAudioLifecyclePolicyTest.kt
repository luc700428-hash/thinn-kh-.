package io.atmos.weather.next

import org.junit.Assert.*
import org.junit.Test

class NextAudioLifecyclePolicyTest {
    @Test fun foregroundAndUnmutedRestoresExpectedAudioLevel() {
        assertEquals(0.3f,NextAudioLifecyclePolicy.gain(true,true,true,.3f),.00001f)
        assertEquals(0f,NextAudioLifecyclePolicy.gain(false,true,true,.3f),.00001f)
        assertEquals(0.3f,NextAudioLifecyclePolicy.gain(true,true,true,.3f),.00001f)
    }
    @Test fun mutedOrDisabledIsSilentAndCannotExceedSafeMixerVolume() {
        assertFalse(NextAudioLifecyclePolicy.canPlay(true,true,false))
        assertEquals(0f,NextAudioLifecyclePolicy.gain(true,true,false,.55f),.00001f)
        assertEquals(0f,NextAudioLifecyclePolicy.gain(true,false,true,.55f),.00001f)
        assertEquals(.55f,NextAudioLifecyclePolicy.gain(true,true,true,2f),.00001f)
        assertEquals(0f,NextAudioLifecyclePolicy.gain(true,true,true,-1f),.00001f)
    }
}
