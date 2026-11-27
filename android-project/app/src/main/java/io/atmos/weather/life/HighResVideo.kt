package io.atmos.weather.life

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.CamcorderProfile
import android.media.EncoderProfiles
import android.media.MediaCodec
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat

/**
 * 8K video. CameraX records up to 4K, so on phones whose camera offers an 8K recording profile this
 * drives the camera directly (Camera2 + MediaRecorder on a persistent surface, as Android's own
 * camera samples do). It records the profile's own frame rate (8K is 24 or 30 fps on today's
 * phones) and does not burn the watermark in: an 8K frame is too large to redraw live.
 */
internal class HighResVideo(private val ctx: Context, private val onError: (String) -> Unit) {
    class Spec(val cameraId: String, val width: Int, val height: Int, val fps: Int, val bitRate: Int, val codec: Int, val sensorOrientation: Int)

    private val thread = HandlerThread("thinn-8k").apply { start() }
    private val handler = Handler(thread.looper)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var preview: Surface? = null
    private var persistent: Surface? = null
    private var recorder: MediaRecorder? = null
    private var output: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    var spec: Spec? = null; private set
    val recording get() = recorder != null

    companion object {
        /** The back camera's 8K recording profile, when the phone has one. */
        fun find(ctx: Context): Spec? = runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.firstNotNullOfOrNull { id ->
                val ch = cm.getCameraCharacteristics(id)
                if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) return@firstNotNullOfOrNull null
                val numeric = id.toIntOrNull() ?: return@firstNotNullOfOrNull null
                if (!CamcorderProfile.hasProfile(numeric, CamcorderProfile.QUALITY_8KUHD)) return@firstNotNullOfOrNull null
                val profile: EncoderProfiles = CamcorderProfile.getAll(id, CamcorderProfile.QUALITY_8KUHD) ?: return@firstNotNullOfOrNull null
                val v = profile.videoProfiles.firstOrNull() ?: return@firstNotNullOfOrNull null
                Spec(id, v.width, v.height, v.frameRate, v.bitrate, v.codec, ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90)
            }
        }.getOrNull()
    }

    /** Opens the camera and shows the preview in [view] (call once its surface texture exists). */
    @SuppressLint("MissingPermission")
    fun open(view: TextureView, texture: SurfaceTexture, rotation: Int) {
        val s = find(ctx) ?: return onError("這支手機不支援 8K 錄影")
        spec = s
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return onError("需要相機權限")
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val map = cm.getCameraCharacteristics(s.cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        // A 16:9 preview no larger than 1080p.
        val pv = map?.getOutputSizes(SurfaceTexture::class.java)?.filter { it.width * 9 == it.height * 16 && it.width <= 1920 }?.maxByOrNull { it.width } ?: Size(1920, 1080)
        texture.setDefaultBufferSize(pv.width, pv.height)
        fitPreview(view, pv, rotation)
        val previewSurface = Surface(texture); preview = previewSurface
        val surface = MediaCodec.createPersistentInputSurface(); persistent = surface
        // Preparing a recorder once sizes the persistent surface for the session.
        runCatching { newRecorder(s, surface, null, rotation).apply { prepare(); release() } }.onFailure { return onError("8K 錄影初始化失敗") }
        runCatching {
            cm.openCamera(s.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    @Suppress("DEPRECATION")
                    d.createCaptureSession(listOf(previewSurface, surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(cs: CameraCaptureSession) { session = cs; repeat(false) }
                        override fun onConfigureFailed(cs: CameraCaptureSession) { onError("這支手機無法同時預覽與 8K 錄影") }
                    }, handler)
                }
                override fun onDisconnected(d: CameraDevice) { d.close(); device = null }
                override fun onError(d: CameraDevice, error: Int) { d.close(); device = null; onError("相機無法啟動（$error）") }
            }, handler)
        }.onFailure { onError("相機無法啟動") }
    }

    private fun repeat(record: Boolean) {
        val d = device ?: return; val cs = session ?: return; val s = spec ?: return
        runCatching {
            val req = d.createCaptureRequest(if (record) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW).apply {
                preview?.let { addTarget(it) }
                if (record) persistent?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(s.fps, s.fps))
                set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            }
            cs.setRepeatingRequest(req.build(), null, handler)
        }
    }

    @SuppressLint("MissingPermission") // sound is added only when the microphone permission is granted
    private fun newRecorder(s: Spec, surface: Surface, fd: java.io.FileDescriptor?, rotation: Int) = MediaRecorder(ctx).apply {
        val sound = fd != null && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (sound) setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        if (fd != null) setOutputFile(fd) else setOutputFile(java.io.File(ctx.cacheDir, "8k-probe.mp4").absolutePath)
        setVideoEncodingBitRate(s.bitRate)
        setVideoFrameRate(s.fps)
        setVideoSize(s.width, s.height)
        setVideoEncoder(if (s.codec == MediaRecorder.VideoEncoder.HEVC) MediaRecorder.VideoEncoder.HEVC else MediaRecorder.VideoEncoder.H264)
        if (sound) { setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioEncodingBitRate(256_000); setAudioSamplingRate(48_000); setAudioChannels(2) }
        setOrientationHint((s.sensorOrientation - rotation + 360) % 360)
        setInputSurface(surface)
    }

    /** Starts recording into Movies/thinn-khi. */
    fun start(rotation: Int): Boolean {
        val s = spec ?: return false; val surface = persistent ?: return false
        if (recorder != null) return true
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "thinn-khi-8K-${System.currentTimeMillis()}.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/thinn-khi"); put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val target = runCatching { ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return false
        val fd = runCatching { ctx.contentResolver.openFileDescriptor(target, "w") }.getOrNull() ?: run { runCatching { ctx.contentResolver.delete(target, null, null) }; return false }
        return runCatching {
            val r = newRecorder(s, surface, fd.fileDescriptor, rotation); r.prepare()
            repeat(true); r.start()
            recorder = r; output = fd; uri = target; true
        }.getOrElse { runCatching { fd.close() }; runCatching { ctx.contentResolver.delete(target, null, null) }; repeat(false); false }
    }

    /** Stops and keeps the video; returns its place in the gallery. */
    fun stop(): Uri? {
        val r = recorder ?: return null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }; recorder = null
        repeat(false)
        runCatching { output?.close() }; output = null
        val u = uri; uri = null
        if (u == null) return null
        return if (ok) { runCatching { ctx.contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }; u }
        else { runCatching { ctx.contentResolver.delete(u, null, null) }; null }
    }

    fun close() {
        stop()
        runCatching { session?.close() }; session = null
        runCatching { device?.close() }; device = null
        runCatching { persistent?.release() }; persistent = null
        runCatching { preview?.release() }; preview = null
        thread.quitSafely()
    }

    /** Scales the stretched TextureView content back to its own shape, filling the view. */
    private fun fitPreview(view: TextureView, buffer: Size, rotation: Int) {
        view.post {
            val vw = view.width.toFloat(); val vh = view.height.toFloat()
            if (vw <= 0 || vh <= 0) return@post
            val m = Matrix()
            if (rotation == 90 || rotation == 270) {
                // As in Android's Camera2 sample: map, fill, then turn the picture upright.
                val viewRect = android.graphics.RectF(0f, 0f, vw, vh)
                val bufRect = android.graphics.RectF(0f, 0f, buffer.height.toFloat(), buffer.width.toFloat())
                bufRect.offset(viewRect.centerX() - bufRect.centerX(), viewRect.centerY() - bufRect.centerY())
                m.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL)
                val s = maxOf(vh / buffer.height, vw / buffer.width)
                m.postScale(s, s, vw / 2, vh / 2); m.postRotate((rotation - 180).toFloat(), vw / 2, vh / 2)
            } else {
                val cw = buffer.height.toFloat(); val ch = buffer.width.toFloat()
                val s = maxOf(vw / cw, vh / ch)
                m.setScale(cw * s / vw, ch * s / vh, vw / 2, vh / 2)
                if (rotation == 180) m.postRotate(180f, vw / 2, vh / 2)
            }
            view.setTransform(m)
        }
    }
}
