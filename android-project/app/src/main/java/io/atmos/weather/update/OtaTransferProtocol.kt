package io.atmos.weather.update

/** Pure protocol rules shared with JVM tests; no UI or Android dependencies. */
internal object OtaTransferProtocol {
    const val MAX_APK_BYTES=512L*1024L*1024L
    fun contentRange(header:String?):Pair<Long,Long>? {
        val m=Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(header ?: return null) ?: return null
        val first=m.groupValues[1].toLongOrNull() ?: return null
        val last=m.groupValues[2].toLongOrNull() ?: return null
        val total=m.groupValues[3].toLongOrNull() ?: return null
        return if(first<=last && last<total && total<=MAX_APK_BYTES) first to total else null
    }
}
