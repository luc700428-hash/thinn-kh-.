package io.atmos.weather.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/** Only public Android TextToSpeech engines; Bixby Voice is not an Android TTS service. */
object SamsungTtsSelector {
    const val AUTO = "auto"
    const val SYSTEM = "system"
    data class EngineChoice(val id:String,val title:String)
    private fun pref(ctx:Context)=ctx.getSharedPreferences("thinn_alarm_tts",Context.MODE_PRIVATE)
    fun selected(ctx:Context):String=pref(ctx).getString("selected_engine",AUTO) ?: AUTO
    fun setSelected(ctx:Context,id:String) {
        require(id==AUTO || id==SYSTEM || installed(ctx).any { it.id==id })
        pref(ctx).edit().putString("selected_engine",id).putString("last_status","尚未試聽").apply()
    }
    fun lastStatus(ctx:Context):String=pref(ctx).getString("last_status","尚未執行播報") ?: "尚未執行播報"
    fun record(ctx:Context,value:String) { pref(ctx).edit().putString("last_status",value).apply() }
    fun installed(ctx:Context):List<EngineChoice> {
        @Suppress("DEPRECATION")
        val entries=runCatching {
            ctx.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),0)
        }.getOrDefault(emptyList())
        return entries.mapNotNull { info ->
            val name=info.serviceInfo?.packageName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title=runCatching { info.loadLabel(ctx.packageManager)?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: name
            EngineChoice(name,"$title（$name）")
        }.distinctBy { it.id }.sortedBy { it.title }
    }
    private fun samsung(name:String):Boolean =
        name.equals("com.samsung.SMT",true) ||
            (name.startsWith("com.samsung.",true) && (name.contains("tts",true) || name.contains("smt",true)))
    fun preferredEngine(ctx:Context):String? = installed(ctx).firstOrNull { samsung(it.id) }?.id
    /** AUTO selects Samsung on Samsung devices; elsewhere it intentionally uses system default. */
    fun intended(ctx:Context):String? = when(val choice=selected(ctx)) {
        AUTO -> if(Build.MANUFACTURER.equals("samsung",true)) preferredEngine(ctx) else null
        SYSTEM -> null
        else -> installed(ctx).firstOrNull { it.id==choice }?.id
    }
    fun canStart(ctx:Context):Boolean {
        val choice=selected(ctx)
        return when {
            choice==SYSTEM -> true
            choice==AUTO && !Build.MANUFACTURER.equals("samsung",true) -> true
            else -> intended(ctx)!=null
        }
    }
    fun label(ctx:Context):String = when(val choice=selected(ctx)) {
        AUTO -> if(Build.MANUFACTURER.equals("samsung",true))
            if(preferredEngine(ctx)==null) "三星 TTS 未找到（不自動改用 Google）" else "優先 Samsung TTS"
            else "依系統預設（非三星手機）"
        SYSTEM -> "系統預設引擎（可能是 Google）"
        else -> installed(ctx).firstOrNull {it.id==choice}?.title ?: "已選引擎未安裝"
    }
    fun choices(ctx:Context):List<EngineChoice> = listOf(
        EngineChoice(AUTO,"自動：三星選 Samsung TTS，其餘依系統"),
        EngineChoice(SYSTEM,"系統預設（可能使用 Google TTS）")
    ) + installed(ctx)
    /** Never request online synthesis silently. The exact installed offline zh-TW voice is required. */
    fun prepare(tts:TextToSpeech):String? {
        val lang=runCatching { tts.setLanguage(Locale.TAIWAN) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if(lang<TextToSpeech.LANG_AVAILABLE) return null
        val best=runCatching {
            tts.voices.orEmpty().filter { v ->
                v.locale.language==Locale.CHINESE.language &&
                    v.locale.country.equals("TW",true) && !v.isNetworkConnectionRequired
            }.maxWithOrNull(compareBy<Voice> { it.quality }.thenByDescending { it.latency })
        }.getOrNull() ?: return null
        return if(tts.setVoice(best)==TextToSpeech.SUCCESS) best.name else null
    }
    /** Short fixed, non-private preview. Report the real bound engine rather than a requested label. */
    fun preview(ctx:Context,onResult:(String)->Unit) {
        val handler=Handler(Looper.getMainLooper())
        if(!canStart(ctx)) {onResult(label(ctx));return}
        val requested=intended(ctx)
        var spoken:TextToSpeech?=null
        var finished=false
        fun finish(result:String) {
            if(finished)return
            finished=true
            runCatching { spoken?.stop();spoken?.shutdown() }
            record(ctx,result)
            handler.post { onResult(result) }
        }
        val init=TextToSpeech.OnInitListener { result ->
            handler.post {
                if(finished)return@post
                val engine=spoken
                if(result!=TextToSpeech.SUCCESS || engine==null) {finish("引擎無法啟動；未切換到 Google");return@post}
                val actual=runCatching {engine.currentEngine}.getOrNull().orEmpty()
                if(requested!=null && !actual.equals(requested,true)) {
                    finish("指定 $requested，但系統實際啟動 $actual；已停止，未冒充三星語音")
                    return@post
                }
                val voice=prepare(engine)
                if(voice==null) {finish("$actual 沒有可用的離線繁體中文聲線；未切換 Google");return@post}
                engine.setSpeechRate(.94f)
                engine.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                    override fun onStart(utteranceId:String?) {}
                    override fun onDone(utteranceId:String?) { handler.post {finish("實際引擎：$actual；聲線：$voice")}}
                    @Deprecated("TTS") override fun onError(utteranceId:String?) {handler.post {finish("$actual 播放失敗")}}
                    override fun onError(utteranceId:String?,errorCode:Int) {onError(utteranceId)}
                })
                if(engine.speak("早安，現在是天氣鬧鐘語音試聽。",TextToSpeech.QUEUE_FLUSH,null,"thinn_alarm_test")!=TextToSpeech.SUCCESS)
                    finish("$actual 試聽啟動失敗")
            }
        }
        spoken=if(requested==null) TextToSpeech(ctx.applicationContext,init)
            else TextToSpeech(ctx.applicationContext,init,requested)
        handler.postDelayed({finish("語音試聽逾時，請確認引擎是否安裝")},12_000L)
    }
}
