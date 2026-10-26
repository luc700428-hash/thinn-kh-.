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
data class ReleaseInfo(val version:String,val notes:String,val url:String,val sha256:String,val sizeBytes:Long?=null,val versionCode:Long?=null)
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

    internal fun newer(latest:String,installed:String)=OtaVersionPolicy.isNewer(latest,installed)
    internal fun isNewerThanInstalled(context:Context,release:ReleaseInfo):Boolean {
        val installed=context.packageManager.getPackageInfo(context.packageName,0)
        return OtaVersionPolicy.isNewer(release.version,installed.versionName ?: BuildConfig.VERSION_NAME,
            release.versionCode,installed.longVersionCode)
    }
    suspend fun findUpdate(context:Context):ReleaseInfo?=findLatestRelease(context).takeIf {isNewerThanInstalled(context,it)}

    suspend fun findLatestRelease(context: Context): ReleaseInfo = withContext(Dispatchers.IO) {
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
            val content = conn.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val block=ByteArray(8192)
                while(true) {
                    val n=input.read(block)
                    if(n<0) break
                    require(output.size()+n<=131072) { "OTA 版本資訊內容過大" }
                    output.write(block,0,n)
                }
                output.toString("UTF-8")
            }
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
            ReleaseInfo(version, info.optString("notes"), url, sha,info.optLong("apk_size",0L).takeIf {it>0L && it<=OtaTransferProtocol.MAX_APK_BYTES},
                info.optLong("version_code",0L).takeIf {it>0L})
        } finally {
            conn.disconnect()
        }
    }
    /** Install is an explicit foreground user action. The service passes false. */
    suspend fun downloadAndInstall(
        context: Context,
        release: ReleaseInfo,
        installWhenReady: Boolean = true,
        onProgress: suspend (downloaded:Long,total:Long?)->Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        validateRelease(release)
        require(isNewerThanInstalled(context,release)) {"此版本已安裝，請重新檢查軟體更新"}
        val target=downloadFile(context,release)
        target.parentFile?.mkdirs()
        if(!target.isFile) downloadResumable(context,release,target,onProgress)
        verifyPackage(context,release,target)
        if(!installWhenReady) return@withContext
        val pm=context.packageManager
        if(android.os.Build.VERSION.SDK_INT>=26 && !pm.canRequestPackageInstalls()) {
            // Installation permission is user-controlled and granted separately.
            val intent=Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            error("請允許此 App 安裝更新後，再按一次立即安裝；不需重新下載")
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.update.files",target)
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) { context.startActivity(intent) }
    }

    internal fun downloadFile(context:Context,release:ReleaseInfo):File {
        validateRelease(release)
        return File(context.cacheDir,"updates/${release.sha256.lowercase()}.apk")
    }

    internal fun validateRelease(release:ReleaseInfo) {
        require(Regex("[A-Fa-f0-9]{64}").matches(release.sha256)) { "APK 指紋格式無效" }
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(release.version)) { "APK 版本格式無效" }
        val tag=if(inPreviewChannel) "v25-preview" else "v${release.version}"
        require(release.url == "${downloadBase}$tag/thinn-khi.apk") { "APK 來源不受信任" }
    }

    private suspend fun downloadResumable(context:Context,release:ReleaseInfo,target:File,
        onProgress:suspend(Long,Long?)->Unit) {
        val part=File(target.path+".part")
        val etagFile=File(target.path+".etag")
        // Cache only the selected release; never accumulate old APKs indefinitely.
        target.parentFile?.listFiles()?.filter { it.name.substringBefore(".apk") != release.sha256.lowercase() }
            ?.forEach { it.delete() }
        val resume=part.takeIf {it.isFile}?.length() ?: 0L
        val conn=(URL(release.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects=true; connectTimeout=15000; readTimeout=35000
            setRequestProperty("Accept-Encoding","identity")
            if(resume>0L) {
                setRequestProperty("Range","bytes=$resume-")
                if(etagFile.isFile) setRequestProperty("If-Range",etagFile.readText())
            }
        }
        try {
            val response=conn.responseCode
            require(conn.url.protocol == "https") { "APK 重新導向不是 HTTPS" }
            if(response==416) {
                // A fully downloaded partial may have survived a process death before rename.
                runCatching { verifyPackage(context,release,part) }.getOrElse {
                    part.delete(); etagFile.delete(); error("續傳範圍已失效，請重試下載")
                }
            } else {
                require(response==200 || response==206) { "APK 下載失敗（HTTP $response）" }
                val range=if(response==206) OtaTransferProtocol.contentRange(conn.getHeaderField("Content-Range")) else null
                if(response==206 && (range==null || range.first!=resume)) {
                    part.delete();etagFile.delete();error("伺服器續傳範圍不符，請重試下載")
                }
                val append=response==206 && resume>0L
                var received=if(append) resume else 0L
                val expected=range?.second ?: conn.contentLengthLong.takeIf {it>0L}
                require(expected==null || expected<=OtaTransferProtocol.MAX_APK_BYTES) { "APK 檔案過大" }
                conn.getHeaderField("ETag")?.takeIf {it.length<=512}?.let {etagFile.writeText(it)}
                withContext(Dispatchers.Main) {onProgress(received,expected)}
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(part,append).use { output ->
                        val buffer=ByteArray(64*1024)
                        var lastNotify=received
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val n=input.read(buffer)
                            if(n<0) break
                            output.write(buffer,0,n); received+=n
                            require(received<=OtaTransferProtocol.MAX_APK_BYTES) { "APK 檔案過大" }
                            if(received-lastNotify>=256*1024) {
                                lastNotify=received
                                withContext(Dispatchers.Main) {onProgress(received,expected)}
                            }
                        }
                        output.fd.sync()
                    }
                }
                require(received>0L && (expected==null || expected==received)) { "APK 下載尚未完整，請繼續下載" }
                withContext(Dispatchers.Main) {onProgress(received,expected ?: received)}
                verifyPackage(context,release,part)
            }
            check(part.renameTo(target)) { "無法保存已驗證安裝檔，請重試" }
            etagFile.delete()
        } finally { conn.disconnect() }
        // Partial bytes remain on network failure/cancel; retries use Range + If-Range.
        // Hash/package/certificate failures delete the partial in verifyPackage.
    }

    private suspend fun verifyPackage(context:Context,release:ReleaseInfo,target:File) {
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
        val packageInfo=pm.getPackageArchiveInfo(target.path,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            ?: run {target.delete();error("無法辨識下載 APK 的套件或簽章")}
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
    }
}
