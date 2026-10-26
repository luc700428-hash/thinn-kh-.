package io.atmos.weather.update

/** Version-name AND Android version-code gates; a cached equal/older APK is never actionable. */
internal object OtaVersionPolicy {
    fun compare(a:String,b:String):Int=AppUpdateDelta.compare(a,b)
    fun isNewer(target:String,installed:String,targetCode:Long?=null,installedCode:Long?=null):Boolean =
        compare(target,installed)>0 && (targetCode==null || installedCode==null || targetCode>installedCode)
}
