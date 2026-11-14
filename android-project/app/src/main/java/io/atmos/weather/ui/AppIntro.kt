package io.atmos.weather.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import io.atmos.weather.R
import io.atmos.weather.earthquake.SeismicAlertDelivery
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Brand entry film shown over the already-running app.
 *
 * The app loads underneath, so the film never delays weather or alerts: it leaves on a tap,
 * when it ends, when anything else takes the window, when an earthquake warning arrives, or
 * if playback fails for any reason. Two cuts are bundled, portrait and landscape; the one
 * closer to the window is scaled to fit and its edge pixels are extended into any remaining
 * space, so every window shape is filled without stretching or cropping the artwork.
 */
object AppIntro {
    const val KEY_ENABLED="app_intro_film_enabled"
    /** How far past an exact fit the film may be enlarged before edges are extended instead. */
    const val MAX_ZOOM=1.08f
    internal const val LONG_SIDE=1920
    internal const val SHORT_SIDE=1080
    private const val FIRST_FRAME_DEADLINE_MS=2_500L
    private const val HARD_LIMIT_MS=14_000L

    data class Frame(val width:Int,val height:Int)

    /** The landscape cut is used only when the window is wider than it is tall. */
    fun useLandscape(windowWidth:Int,windowHeight:Int)=windowWidth>windowHeight

    /** Size of the film inside a window: an exact fit, enlarged by at most [MAX_ZOOM] toward filling it. */
    fun frame(windowWidth:Int,windowHeight:Int,landscape:Boolean):Frame {
        val w=windowWidth.coerceAtLeast(1).toFloat();val h=windowHeight.coerceAtLeast(1).toFloat()
        val filmW=(if(landscape)LONG_SIDE else SHORT_SIDE).toFloat();val filmH=(if(landscape)SHORT_SIDE else LONG_SIDE).toFloat()
        val fit=min(w/filmW,h/filmH);val cover=max(w/filmW,h/filmH)
        val scale=min(cover,fit*MAX_ZOOM)
        return Frame((filmW*scale).roundToInt().coerceAtLeast(1),(filmH*scale).roundToInt().coerceAtLeast(1))
    }

    /** Only an ordinary launch from the home screen plays the film; alerts, alarms and deep links open directly. */
    fun wanted(action:String?,launcher:Boolean,extraKeys:Set<String>,restored:Boolean,enabled:Boolean,reducedMotion:Boolean,warningActive:Boolean):Boolean =
        !restored&&enabled&&!reducedMotion&&!warningActive&&action==Intent.ACTION_MAIN&&launcher&&extraKeys.none{it.startsWith("open_")}

    fun attachIfAppropriate(activity:Activity,intent:Intent?,savedInstanceState:Bundle?) {
        runCatching {
            val prefs=activity.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
            val reduced=prefs.getBoolean("reduce_motion",false)||
                runCatching{Settings.Global.getFloat(activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}.getOrDefault(false)
            val keys=runCatching{intent?.extras?.keySet()?.toSet()}.getOrNull()?:emptySet()
            if(!wanted(intent?.action,intent?.hasCategory(Intent.CATEGORY_LAUNCHER)==true,keys,savedInstanceState!=null,
                    prefs.getBoolean(KEY_ENABLED,true),reduced,SeismicAlertDelivery.active.value!=null))return
            activity.addContentView(Film(activity),ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private class Film(context:Context):FrameLayout(context),TextureView.SurfaceTextureListener {
        private val texture=TextureView(context)
        private val edges=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
        private val from=Rect();private val to=Rect()
        private var surface:Surface?=null
        private var player:MediaPlayer?=null
        private var landscapeLoaded:Boolean?=null
        private var shown=false
        private var leaving=false
        private var frames=0
        private val giveUp=Runnable{if(!shown)leave(false)}
        private val limit=Runnable{leave(false)}

        init {
            // The first seconds of the film are this colour, so nothing flashes before the first frame.
            setBackgroundColor(0xFF0D312E.toInt())
            isClickable=true
            contentDescription="開場動畫，輕觸即可略過"
            setOnClickListener{leave(true)}
            texture.surfaceTextureListener=this
            addView(texture,LayoutParams(1,1,Gravity.CENTER))
            postDelayed(giveUp,FIRST_FRAME_DEADLINE_MS)
            postDelayed(limit,HARD_LIMIT_MS)
        }

        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
            super.onSizeChanged(w,h,oldw,oldh)
            if(w<=0||h<=0)return
            // Rotation, folding and split-screen arrive here; the activity is not recreated for them.
            post {
                if(leaving)return@post
                val landscape=useLandscape(w,h);val size=frame(w,h,landscape)
                texture.layoutParams=LayoutParams(size.width,size.height,Gravity.CENTER)
                if(surface!=null&&landscapeLoaded!=null&&landscapeLoaded!=landscape)open(landscape,runCatching{player?.currentPosition}.getOrNull()?:0)
            }
        }

        override fun onSurfaceTextureAvailable(texture:SurfaceTexture,width:Int,height:Int) {
            surface=Surface(texture)
            open(useLandscape(this.width,this.height),0)
        }
        override fun onSurfaceTextureSizeChanged(texture:SurfaceTexture,width:Int,height:Int)=Unit
        override fun onSurfaceTextureDestroyed(texture:SurfaceTexture):Boolean {release();return true}
        override fun onSurfaceTextureUpdated(texture:SurfaceTexture) {
            shown=true
            if(SeismicAlertDelivery.active.value!=null){leave(true);return}
            // Edge colours change slowly; every other frame is plenty.
            if(frames++%2==0&&hasUnfilledSpace()){runCatching{this.texture.getBitmap(edges)};invalidate()}
        }

        private fun hasUnfilledSpace()=texture.left>0||texture.top>0||texture.right<width||texture.bottom<height

        private fun open(landscape:Boolean,positionMs:Int) {
            runCatching {
                val target=surface?:return
                player?.let{old->runCatching{old.release()}}
                player=null
                landscapeLoaded=landscape
                val file=resources.openRawResourceFd(if(landscape)R.raw.thinn_intro_landscape else R.raw.thinn_intro_portrait)
                val next=MediaPlayer()
                player=next
                try{next.setDataSource(file.fileDescriptor,file.startOffset,file.length)}finally{runCatching{file.close()}}
                next.setSurface(target)
                next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                if(!soundWelcome())next.setVolume(0f,0f)
                next.setOnPreparedListener{ready->
                    runCatching {
                        if(positionMs>0)ready.seekTo(positionMs.toLong(),MediaPlayer.SEEK_CLOSEST)
                        ready.start()
                    }.onFailure{leave(false)}
                }
                next.setOnCompletionListener{leave(false)}
                next.setOnErrorListener{_,_,_->leave(false);true}
                next.prepareAsync()
            }.onFailure{leave(false)}
        }

        /** Sound only when the phone is set to ring and nothing else is already playing. */
        private fun soundWelcome():Boolean=runCatching {
            val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.ringerMode==AudioManager.RINGER_MODE_NORMAL&&!audio.isMusicActive
        }.getOrDefault(false)

        override fun dispatchDraw(canvas:Canvas) {
            if(shown&&hasUnfilledSpace()) {
                val l=texture.left;val t=texture.top;val r=texture.right;val b=texture.bottom
                val bw=edges.width;val bh=edges.height
                if(t>0){from.set(0,0,bw,1);to.set(l,0,r,t);canvas.drawBitmap(edges,from,to,paint)}
                if(b<height){from.set(0,bh-1,bw,bh);to.set(l,b,r,height);canvas.drawBitmap(edges,from,to,paint)}
                if(l>0){from.set(0,0,1,bh);to.set(0,t,l,b);canvas.drawBitmap(edges,from,to,paint)}
                if(r<width){from.set(bw-1,0,bw,bh);to.set(r,t,width,b);canvas.drawBitmap(edges,from,to,paint)}
            }
            super.dispatchDraw(canvas)
        }

        override fun onWindowFocusChanged(hasWindowFocus:Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            // A permission dialog, the notification shade or another app now has the user's attention.
            if(!hasWindowFocus&&shown)leave(true)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(giveUp);removeCallbacks(limit)
            release()
            super.onDetachedFromWindow()
        }

        private fun release() {
            player?.let{p->runCatching{p.setOnCompletionListener(null);p.release()}}
            player=null
            surface?.let{s->runCatching{s.release()}}
            surface=null
        }

        private fun leave(quick:Boolean) {
            if(leaving)return
            leaving=true
            removeCallbacks(giveUp);removeCallbacks(limit)
            isClickable=false
            val remove=Runnable {
                release()
                runCatching{(parent as? ViewGroup)?.removeView(this)}
            }
            if(!shown){remove.run();return}
            runCatching{player?.setVolume(0f,0f)}
            animate().alpha(0f).setDuration(if(quick)220L else 420L).withEndAction(remove).start()
        }
    }
}
