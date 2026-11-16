package io.atmos.weather.earthquake

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb

/**
 * Makes a real earthquake warning APPEAR, not just sit in the notification shade.
 *
 * Android only turns a full-screen notification into a window when the phone is locked; while
 * someone is using another app it shrinks to a heads-up banner that is easy to miss. With the
 * "display over other apps" permission (the one Android itself calls 顯示在其他應用程式上層)
 * the app may open its warning window from the background, so this:
 *  1. opens the full warning directly when our own app is in front (existing path);
 *  2. otherwise, with the overlay permission, starts the warning activity directly;
 *  3. if that window has not appeared shortly after (OEM pop-up blocks, e.g. MIUI's
 *     "background pop-up" switch), floats an iOS-style alert card over whatever is on screen,
 *     with the local intensity and a live countdown; tapping it opens the full warning.
 * Without the permission the notification (with its full-screen intent) is all Android allows.
 */
internal object SeismicPopup {
    /** Ticket whose warning window is currently resumed; set by [SeismicAlertActivity]. */
    @Volatile var shownKey:String?=null
    private var card:View?=null
    private val main=Handler(Looper.getMainLooper())

    fun canOverlay(ctx:Context)=runCatching{Settings.canDrawOverlays(ctx)}.getOrDefault(false)

    fun launch(ctx:Context,ticket:SeismicAlertDelivery.Ticket) {
        if(SeismicForegroundWindow.present(ctx,ticket))return
        if(!canOverlay(ctx))return
        val app=ctx.applicationContext
        val intent=Intent(app,SeismicAlertActivity::class.java).putExtra("ticket",ticket.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching{app.startActivity(intent)}
        main.postDelayed({
            if(shownKey!=ticket.key&&SeismicAlertDelivery.active.value?.key==ticket.key)showCard(app,ticket)
        },1500)
    }

    private fun reduceMotion(ctx:Context)=ctx.getSharedPreferences("thinn_settings",0).getBoolean("reduce_motion",false)

    fun dismissCard() {
        main.post {
            val v=card?:return@post;card=null
            val remove={runCatching{(v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)};Unit}
            val panel=(v as? FrameLayout)?.getChildAt(0)
            if(panel==null||reduceMotion(v.context)){remove();return@post}
            // Leaves the way it came: lifts and fades upward on the iOS curve.
            panel.animate().translationY(-panel.height*.6f).alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(260)
                .setInterpolator(android.view.animation.PathInterpolator(.3f,0f,.8f,.15f)).withEndAction{remove()}.start()
        }
    }

    private fun dp(ctx:Context,v:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,ctx.resources.displayMetrics)

    private fun showCard(ctx:Context,ticket:SeismicAlertDelivery.Ticket) {
        if(card!=null||!canOverlay(ctx))return
        val e=ticket.incident
        val place=SeismicUserLocation.current(ctx)
        val local=SeismicAlertThreshold.local(e.localIntensityByCounty,place?.name)
        val wm=ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root=FrameLayout(ctx)
        val panel=LinearLayout(ctx).apply {
            orientation=LinearLayout.VERTICAL
            val pad=dp(ctx,18f).toInt();setPadding(pad,pad,pad,pad)
            // iOS 27 alert material: deep translucent glass, continuous corners, hairline rim.
            background=GradientDrawable().apply {
                cornerRadius=dp(ctx,30f);setColor(AColor.argb(238,22,24,30))
                setStroke(dp(ctx,1f).toInt(),AColor.argb(70,255,255,255))
            }
            elevation=dp(ctx,24f)
        }
        fun text(s:String,size:Float,color:Int,bold:Boolean=false)=TextView(ctx).apply {
            text=s;setTextColor(color);setTextSize(TypedValue.COMPLEX_UNIT_SP,size)
            if(bold)typeface=android.graphics.Typeface.DEFAULT_BOLD
        }
        val header=LinearLayout(ctx).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val grade=local?.replace("級","")?.trim()
        val fill=SeismicIntensityColors.color(grade)
        val tile=text(SeismicDisplayFormat.symbol(grade),30f,seismicInkOn(fill).toArgb(),true).apply {
            gravity=Gravity.CENTER
            background=GradientDrawable().apply{cornerRadius=dp(ctx,16f);setColor(fill.toArgb())}
            val s=dp(ctx,64f).toInt();layoutParams=LinearLayout.LayoutParams(s,s)
        }
        val titles=LinearLayout(ctx).apply {
            orientation=LinearLayout.VERTICAL
            val p=dp(ctx,14f).toInt();setPadding(p,0,0,0)
            addView(text("緊急地震速報",19f,AColor.WHITE,true))
            addView(text("${SeismicDisplayFormat.location(e.epicenter)} · ${SeismicDisplayFormat.magnitude(e.magnitude)}",15f,AColor.argb(210,255,255,255)))
            addView(text(if(grade==null)"所在地震度待來源提供" else "${place?.name?:"所在地"}預估 ${SeismicDisplayFormat.symbol(grade)} 級",15f,AColor.argb(210,255,255,255)))
        }
        header.addView(tile);header.addView(titles,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        panel.addView(header)
        val countdown=text("",34f,AColor.rgb(255,214,10),true).apply{setPadding(0,dp(ctx,12f).toInt(),0,0)}
        panel.addView(countdown)
        panel.addView(text("趴下、掩護、穩住",17f,AColor.WHITE,true))
        val actions=LinearLayout(ctx).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(ctx,14f).toInt(),0,0)}
        fun pill(label:String,bg:Int,onClick:()->Unit)=text(label,16f,AColor.WHITE,true).apply {
            gravity=Gravity.CENTER;val v=dp(ctx,12f).toInt();setPadding(0,v,0,v)
            background=GradientDrawable().apply{cornerRadius=dp(ctx,999f);setColor(bg)}
            setOnClickListener{onClick()}
        }
        actions.addView(pill("關閉",AColor.argb(60,255,255,255)){SeismicAlertDelivery.dismiss(ctx,ticket.key);dismissCard()},
            LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginEnd=dp(ctx,10f).toInt()})
        actions.addView(pill("查看速報",AColor.rgb(255,59,48)){
            dismissCard()
            runCatching{ctx.startActivity(Intent(ctx,SeismicAlertActivity::class.java).putExtra("ticket",ticket.key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
        },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        panel.addView(actions)
        val m=dp(ctx,12f).toInt()
        root.addView(panel,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(m,m,m,m)})
        val type=if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION")WindowManager.LayoutParams.TYPE_PHONE
        val params=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP;y=dp(ctx,36f).toInt()}
        if(runCatching{wm.addView(root,params)}.isFailure)return
        card=root
        val calm=reduceMotion(ctx)
        if(!calm) {
            // iOS 27 alert entrance: drops in from the top with a soft overshoot, then the
            // intensity tile pops so the eye lands on it first.
            panel.alpha=0f;panel.translationY=-dp(ctx,140f);panel.scaleX=.94f;panel.scaleY=.94f
            panel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(520)
                .setInterpolator(android.view.animation.OvershootInterpolator(.9f)).start()
            tile.scaleX=.6f;tile.scaleY=.6f
            tile.animate().scaleX(1f).scaleY(1f).setStartDelay(160).setDuration(420)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
        }
        val arrival=place?.name?.let{p->e.arrivalAtMsByCounty.entries.firstOrNull{p.contains(it.key)||it.key.contains(p)}?.value}
        val tick=object:Runnable{
            override fun run() {
                if(card!==root)return
                val now=System.currentTimeMillis()
                if(SeismicAlertDelivery.active.value?.key!=ticket.key||now>=SeismicAlertDelivery.expires(e)){dismissCard();return}
                val next=when{
                    arrival==null->"注意搖晃"
                    arrival>now->"${kotlin.math.ceil((arrival-now)/1000.0).toInt()} 秒後抵達"
                    else->"震波已抵達"
                }
                if(next!=countdown.text.toString()) {
                    countdown.text=next
                    val urgent=arrival==null||arrival-now<=10_000
                    countdown.setTextColor(if(urgent)AColor.rgb(255,69,58) else AColor.rgb(255,214,10))
                    // A heartbeat on every second: stronger in the last ten seconds.
                    if(!calm){
                        val beat=if(urgent)1.12f else 1.05f
                        countdown.pivotX=0f;countdown.pivotY=countdown.height/2f
                        countdown.scaleX=beat;countdown.scaleY=beat
                        countdown.animate().scaleX(1f).scaleY(1f).setDuration(320)
                            .setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
                    }
                }
                main.postDelayed(this,200)
            }
        }
        main.post(tick)
    }
}
