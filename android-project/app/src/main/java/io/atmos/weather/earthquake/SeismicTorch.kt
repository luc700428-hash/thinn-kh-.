package io.atmos.weather.earthquake

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * Flashes the camera light during a strong-shaking warning (as KNY does), so a phone face-up on a
 * desk or in a dark room is noticed even with the sound off. Opt-in, only for a local estimate of
 * intensity 4 or more, at most 30 s, and stopped the moment the warning is dismissed.
 * Torch mode needs no camera permission.
 */
internal object SeismicTorch {
    const val PREF="seismic_torch_flash"
    private val main=Handler(Looper.getMainLooper())
    private var cameraId:String?=null
    private var manager:CameraManager?=null
    private var until=0L
    private var on=false

    fun enabled(ctx:Context)=ctx.getSharedPreferences("thinn_seismic_v15",0).getBoolean(PREF,false)
    fun setEnabled(ctx:Context,value:Boolean)=ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putBoolean(PREF,value).apply()

    /** [local] is the estimated local intensity label (e.g. "4", "5弱"); weaker shaking never flashes. */
    fun start(ctx:Context,local:String?) {
        if(!enabled(ctx)||local==null)return
        val rank=maxOf(CwaEarthquakeParser.intensityRank(local),local.firstOrNull()?.digitToIntOrNull()?:0)
        if(rank<4)return
        val cm=ctx.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager?:return
        val id=runCatching{cm.cameraIdList.firstOrNull{cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true}}.getOrNull()?:return
        main.removeCallbacksAndMessages(null)
        manager=cm;cameraId=id;until=System.currentTimeMillis()+30_000;on=false
        main.post(blink)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        val cm=manager;val id=cameraId
        if(cm!=null&&id!=null)runCatching{cm.setTorchMode(id,false)}
        manager=null;cameraId=null;on=false
    }

    private val blink=object:Runnable {
        override fun run() {
            val cm=manager;val id=cameraId
            if(cm==null||id==null||System.currentTimeMillis()>until){stop();return}
            on=!on
            if(runCatching{cm.setTorchMode(id,on)}.isFailure){stop();return}
            main.postDelayed(this,if(on)180 else 320)
        }
    }
}
