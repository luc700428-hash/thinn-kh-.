package io.atmos.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ColorScheme

internal data class ThemeContribution(val theme:AppThemeColor,val dark:Boolean,val weight:Float)
internal data class ThemeFrame(val parts:List<ThemeContribution>) {
    private val paints=java.util.concurrent.ConcurrentHashMap<Int,Color>()
    private val schemes=java.util.concurrent.ConcurrentHashMap<Int,ColorScheme>()
    val accent:Color by lazy{mix{Color(it.theme.primaryArgb)}}
    fun brand(base:Int):Color=paints.getOrPut(base){mix{Color(ThemeColorTint.argb(base,it.theme))}}
    private fun mix(color:(ThemeContribution)->Color):Color {
        var result=color(parts.first());var weight=parts.first().weight
        for(part in parts.drop(1)){result=lerp(result,color(part),part.weight/(weight+part.weight));weight+=part.weight}
        return result
    }
    fun scheme(dark:Boolean?=null):ColorScheme=schemes.getOrPut(if(dark==null)-1 else if(dark)1 else 0) {
        var result=BrandPalette.scheme(parts.first().theme,dark?:parts.first().dark);var weight=parts.first().weight
        for(part in parts.drop(1)){
            result=mixSchemes(result,BrandPalette.scheme(part.theme,dark?:part.dark),part.weight/(weight+part.weight));weight+=part.weight
        }
        result.readable()
    }
    fun toward(target:ThemeFrame,progress:Float):ThemeFrame {
        val t=progress.coerceIn(0f,1f);if(t<=0f)return this;if(t>=1f)return target
        val grouped=(parts.map{it.copy(weight=it.weight*(1-t))}+target.parts.map{it.copy(weight=it.weight*t)}).groupBy{it.theme to it.dark}
        return ThemeFrame(grouped.map{(key,values)->ThemeContribution(key.first,key.second,values.sumOf{it.weight.toDouble()}.toFloat())}.filter{it.weight>.000001f})
    }
    companion object{fun at(theme:AppThemeColor,dark:Boolean)=ThemeFrame(listOf(ThemeContribution(theme,dark,1f)))}
}
internal object ThemeMotion {
    private val hosts=mutableListOf<Any>()
    var owner by mutableStateOf<Any?>(null)
        private set
    val active get()=owner!=null
    var frame by mutableStateOf(ThemeFrame.at(AppThemeColor.FOREST,false))
    fun attach(token:Any,initial:ThemeFrame){if(hosts.isEmpty())frame=initial;hosts+=token;owner=token}
    fun detach(token:Any){hosts.remove(token);owner=hosts.lastOrNull()}
    val current get()=if(active)frame else ThemeFrame.at(AppThemeColors.value,IOS27AppearanceMode.value=="dark")
}
internal val LocalThemeMotionHost=staticCompositionLocalOf{false}
@Composable internal fun ThemeMotionHost(theme:AppThemeColor,dark:Boolean,reduceMotion:Boolean){
    val token=remember{Any()};val owner=ThemeMotion.owner
    val initial=remember{ThemeFrame.at(theme,dark)};val progress=remember{Animatable(1f)}
    DisposableEffect(Unit){ThemeMotion.attach(token,initial);onDispose{ThemeMotion.detach(token)}}
    LaunchedEffect(theme,dark,reduceMotion,owner){
        if(owner!==token)return@LaunchedEffect
        val target=ThemeFrame.at(theme,dark);val start=ThemeMotion.current
        if(reduceMotion||start==target)ThemeMotion.frame=target else {
            progress.snapTo(0f)
            progress.animateTo(1f,tween(420,easing=FastOutSlowInEasing)){if(ThemeMotion.owner===token)ThemeMotion.frame=start.toward(target,value)}
            if(ThemeMotion.owner===token)ThemeMotion.frame=target
        }
    }
}
internal fun animatedBrandArgb(base:Long)=brandColor(base).toArgb()
