package io.atmos.weather.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.atmos.weather.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Github Releases self-update for the sideloaded edition, with SHA256 verification.
 * A release must be signed by the SAME key as the installed APK; Android enforces that on install.
 * No silent installation is attempted or permitted.
 */
data class ReleaseInfo(val version:String,val notes:String,val url:String,val sha256:String)
object AppUpdater {
    /** Public Release asset: no api.github.com access or GitHub login required. */
    private const val officialManifestUrl =
        "https://github.com/luc700428-hash/thinn-kh-./releases/latest/download/ota.json"
    // This release is marked PRERELEASE and is never GitHub's official latest release.
    private const val previewManifestUrl =
        "https://github.com/luc700428-hash/thinn-kh-./releases/download/v25-preview/ota.json"
    private val inPreviewChannel: Boolean get() = BuildConfig.THINN_OTA_CHANNEL == "preview"
    private val manifestUrl: String get() = if (inPreviewChannel) previewManifestUrl else officialManifestUrl
    private const val downloadBase =
        "https://github.com/luc700428-hash/thinn-kh-./releases/download/"

    private fun newer(latest: String, installed: String): Boolean {
        val a = latest.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = installed.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    suspend fun findUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "thinn-khi-android/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val status = conn.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                error("無法取得公開 OTA 版本資訊（HTTP $status）。請稍後重試，或從 GitHub Releases 手動下載。")
            }
            require(conn.url.protocol == "https") { "OTA 版本資訊不是 HTTPS 連線" }
            val content = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(content.length in 1..131072) { "OTA 版本資訊內容無效或過大" }
            val info = JSONObject(content)
            require(info.optInt("schema") == 1) { "OTA 版本資訊格式不相容" }
            val version = info.getString("version")
            require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) { "OTA 版本號不合法" }
            require(info.optString("package_name") == context.packageName) { "OTA 套件名稱不符" }
            val sha = info.getString("sha256")
            require(Regex("[A-Fa-f0-9]{64}").matches(sha)) { "OTA SHA-256 格式不正確" }
            val url = info.getString("apk_url")
            val expectedTag = if (inPreviewChannel) "v25-preview" else "v$version"
            if (inPreviewChannel) require(version.startsWith("25.0.")) { "測試 OTA 版本不合法" }
            require(url == "${downloadBase}$expectedTag/thinn-khi.apk") { "OTA APK 來源不受信任" }
            if (!newer(version, BuildConfig.VERSION_NAME)) return@withContext null
            ReleaseInfo(version, info.optString("notes").take(16000), url, sha)
        } finally {
            conn.disconnect()
        }
    }
    suspend fun downloadAndInstall(
        context: Context,
        release: ReleaseInfo,
        onProgress: suspend (downloaded:Long,total:Long?)->Unit = { _, _ -> }
    )=withContext(Dispatchers.IO) {
        val target=File(context.cacheDir,"updates/thinn-khi.apk")
        target.parentFile?.mkdirs()
        val conn=(URL(release.url).openConnection() as HttpURLConnection)
        conn.instanceFollowRedirects=true;conn.connectTimeout=15000;conn.readTimeout=35000
        // GitHub redirects downloads to their CDN; require HTTPS before consuming bytes.
        try {
            val expected=conn.contentLengthLong.takeIf { it > 0L }
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer=ByteArray(64*1024)
                    var received=0L
                    var lastNotify=0L
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n=input.read(buffer)
                        if(n < 0) break
                        output.write(buffer,0,n)
                        received+=n
                        if(received-lastNotify >= 256*1024 || (expected!=null && received>=expected)) {
                            lastNotify=received
                            withContext(Dispatchers.Main) {onProgress(received,expected)}
                        }
                    }
                    if(received == 0L) error("APK 下載內容為空")
                    withContext(Dispatchers.Main) {onProgress(received,expected)}
                }
            }
        } catch (cancel:CancellationException) {
            target.delete()
            throw cancel
        } catch (error:Exception) {
            target.delete()
            throw error
        } finally {conn.disconnect()}
        val md=MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input ->
            val buffer=ByteArray(65536)
            while(true) {
                val n=input.read(buffer)
                if(n < 0) break
                md.update(buffer,0,n)
            }
        }
        currentCoroutineContext().ensureActive()
        val sha=md.digest().joinToString("") {"%02x".format(it.toInt() and 0xff)}
        if(!sha.equals(release.sha256,ignoreCase=true)) {
            target.delete()
            error("APK SHA-256 驗證失敗，不會安裝")
        }
        val pm=context.packageManager
        @Suppress("DEPRECATION")
        val packageInfo=requireNotNull(pm.getPackageArchiveInfo(target.path,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)) {
            "無法辨識下載 APK 的套件或簽章"
        }
        if(packageInfo.packageName != context.packageName) {
            target.delete(); error("APK 套件名稱不符，已取消更新")
        }
        @Suppress("DEPRECATION")
        val installed=pm.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        if (packageInfo.longVersionCode <= installed.longVersionCode) {
            target.delete()
            error("更新 APK 的版本號沒有高於已安裝版本，請重新發布遞增版號的 APK")
        }
        // Give a clear error before opening the installer when old cloud-built debug APKs have
        // different signing certificates. Without the original key Android cannot upgrade them.
        val currentCerts=installed.signingInfo?.apkContentsSigners?.map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).toList()
        }?.toSet()
        val updateCerts=packageInfo.signingInfo?.apkContentsSigners?.map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).toList()
        }?.toSet()
        if (currentCerts.isNullOrEmpty() || updateCerts.isNullOrEmpty() || currentCerts != updateCerts) {
            target.delete()
            error("此 APK 與目前已安裝版本的簽章不同，無法覆蓋。若之前裝的是使用不同金鑰的 Debug 版，請先備份資料，再換裝一次固定簽章正式版。")
        }
        if(android.os.Build.VERSION.SDK_INT>=26 && !pm.canRequestPackageInstalls()) {
            // Installation permission is user-controlled and granted separately.
            val intent=Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            error("請允許此 App 安裝更新後，再按一次下載並安裝")
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.update.files",target)
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) { context.startActivity(intent) }
    }
}
