package io.atmos.weather.update

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the current sideload OTA transfer across navigation and recomposition.
 * Unlike rememberCoroutineScope(), this is not cancelled when update Dialog closes.
 * This process-lifetime scope is NOT a background service: Android may kill the
 * process under resource pressure. The installer still requires system consent.
 * Future robust-process restart support should use user-initiated data transfer jobs.
 */
internal object OtaRetainedSession {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val installing=mutableStateOf(false)
    val downloadJob=mutableStateOf<Job?>(null)
    val pending=mutableStateOf<ReleaseInfo?>(null)
    val pendingDelta=mutableStateOf("")
    val status=mutableStateOf("版本與更新中心已開啟")
    val downloaded=mutableLongStateOf(0L)
    val totalBytes=mutableStateOf<Long?>(null)
    val lastError=mutableStateOf<String?>(null)
    val installerPrompted=mutableStateOf(false)
}
