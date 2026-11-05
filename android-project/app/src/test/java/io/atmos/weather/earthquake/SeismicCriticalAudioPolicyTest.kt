package io.atmos.weather.earthquake
import org.junit.Assert.*
import org.junit.Test

class SeismicCriticalAudioPolicyTest {
    private fun route(critical:Boolean=true,normal:Boolean=false,filter:SeismicInterruption=SeismicInterruption.ALL,
        granted:Boolean=false,bypass:Boolean=false,notification:Int=0,alarm:Int=4)=
        SeismicCriticalAudioPolicy.chooseRoute(critical,normal,filter,granted,bypass,notification,alarm)
    @Test fun ordinaryModeRespectsSilentAndDnd() {
        assertNull(route(critical=false,normal=false,notification=4))
        for(filter in SeismicInterruption.entries.filter{it!=SeismicInterruption.ALL})assertNull(route(critical=false,normal=true,filter=filter,notification=4))
    }
    @Test fun ordinaryModeUsesNotificationAudioOnly() {
        assertEquals(SeismicSoundRoute.NOTIFICATION,route(critical=false,normal=true,notification=4))
        assertNull(route(critical=false,normal=true,notification=0))
    }
    @Test fun criticalModeUsesAlarmsWhenRingerIsSilent() {
        for(normal in listOf(false,true))assertEquals(SeismicSoundRoute.ALARM,route(normal=normal,notification=0))
    }
    @Test fun priorityDndNeedsBothPolicyGrantAndActualChannelException() {
        for(granted in listOf(false,true))for(bypass in listOf(false,true))assertEquals(
            if(granted&&bypass)SeismicSoundRoute.ALARM else null,route(filter=SeismicInterruption.PRIORITY,granted=granted,bypass=bypass))
    }
    @Test fun totalSilenceAndUnknownModesRemainSystemVetoes() {
        for(filter in listOf(SeismicInterruption.NONE,SeismicInterruption.UNKNOWN))assertNull(route(filter=filter,granted=true,bypass=true))
    }
    @Test fun alarmsOnlyModePermitsCriticalAlarmAudio() {
        assertEquals(SeismicSoundRoute.ALARM,route(filter=SeismicInterruption.ALARMS))
    }
    @Test fun criticalModeCannotRaiseAZeroAlarmVolume() {
        for(filter in SeismicInterruption.entries)assertNull(route(filter=filter,granted=true,bypass=true,alarm=0))
    }
}
