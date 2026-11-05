package io.atmos.weather.earthquake

internal enum class SeismicInterruption { ALL, PRIORITY, ALARMS, NONE, UNKNOWN }
internal enum class SeismicSoundRoute { ALARM, NOTIFICATION }
internal object SeismicCriticalAudioPolicy {
    /** Priority channel exceptions cannot override Android's total-silence veto. */
    fun chooseRoute(critical:Boolean,ringerNormal:Boolean,filter:SeismicInterruption,policyAccess:Boolean,channelBypass:Boolean,
        notificationVolume:Int,alarmVolume:Int):SeismicSoundRoute? {
        if(filter==SeismicInterruption.NONE)return null
        if(critical) {
            val allowed=filter==SeismicInterruption.ALL||filter==SeismicInterruption.ALARMS||
                (filter==SeismicInterruption.PRIORITY&&policyAccess&&channelBypass)
            return if(allowed&&alarmVolume>0)SeismicSoundRoute.ALARM else null
        }
        return if(ringerNormal&&filter==SeismicInterruption.ALL&&notificationVolume>0)
            SeismicSoundRoute.NOTIFICATION else null
    }
}
