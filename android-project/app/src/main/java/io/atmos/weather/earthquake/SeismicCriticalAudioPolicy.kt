package io.atmos.weather.earthquake

internal enum class SeismicInterruption { ALL, PRIORITY, ALARMS, NONE, UNKNOWN }
internal enum class SeismicSoundRoute { ALARM, NOTIFICATION }
internal object SeismicCriticalAudioPolicy {
    /** Priority channel exceptions cannot override Android's total-silence veto. */
    fun chooseRoute(critical:Boolean,ringerNormal:Boolean,filter:SeismicInterruption,policyAccess:Boolean,channelBypass:Boolean,
        notificationVolume:Int,alarmVolume:Int):SeismicSoundRoute? {
        if(filter==SeismicInterruption.NONE)return null
        if(critical) {
            // r19: an earthquake warning must be heard under Do Not Disturb. It plays as an ALARM,
            // which priority-only DND lets through by default (as the system clock does); the
            // channel exception (policy access + bypass) is a bonus, not a requirement.
            // Only total silence ("NONE") still vetoes it - Android mutes alarms there itself.
            val allowed=filter==SeismicInterruption.ALL||filter==SeismicInterruption.ALARMS||
                filter==SeismicInterruption.PRIORITY
            return if(allowed&&alarmVolume>0)SeismicSoundRoute.ALARM else null
        }
        return if(ringerNormal&&filter==SeismicInterruption.ALL&&notificationVolume>0)
            SeismicSoundRoute.NOTIFICATION else null
    }
}
