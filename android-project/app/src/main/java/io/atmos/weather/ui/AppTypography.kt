package io.atmos.weather.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.TextUnit
import io.atmos.weather.presentation.home.LocalAppearance
import io.atmos.weather.presentation.home.WeatherAppearance
import kotlin.math.roundToInt

internal fun adjustedTextWeight(base:Int,user:Int)=(base+user-400).coerceIn(100,900)

/** One observable source for typography, including feature pages with explicit text sizes. */
@Composable internal fun rememberAppAppearance():WeatherAppearance {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("thinn_settings",0)}
    var appearance by remember(context){mutableStateOf(WeatherAppearance.from(context))}
    DisposableEffect(prefs) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
            if(key?.startsWith("v4_")==true)appearance=WeatherAppearance.from(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return appearance
}
@Composable private fun readingWeight(weight:FontWeight?,style:TextStyle):FontWeight {
    val base=weight?:style.fontWeight?:LocalTextStyle.current.fontWeight?:FontWeight.Normal
    return FontWeight(adjustedTextWeight(base.weight,LocalAppearance.current.fontWeight.roundToInt()))
}
@Composable fun AppText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
    fontSize:TextUnit=TextUnit.Unspecified,fontStyle:FontStyle?=null,fontWeight:FontWeight?=null,
    fontFamily:FontFamily?=null,letterSpacing:TextUnit=TextUnit.Unspecified,textDecoration:TextDecoration?=null,
    textAlign:TextAlign?=null,lineHeight:TextUnit=TextUnit.Unspecified,overflow:TextOverflow=TextOverflow.Clip,
    softWrap:Boolean=true,maxLines:Int=Int.MAX_VALUE,minLines:Int=1,onTextLayout:((TextLayoutResult)->Unit)?=null,
    style:TextStyle=LocalTextStyle.current) {
    MaterialText(text,modifier,color,fontSize,fontStyle,readingWeight(fontWeight,style),
        fontFamily?:LocalAppearance.current.family,letterSpacing,textDecoration,textAlign,lineHeight,
        overflow,softWrap,maxLines,minLines,onTextLayout,style)
}
@Composable fun AppText(text:AnnotatedString,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
    fontSize:TextUnit=TextUnit.Unspecified,fontStyle:FontStyle?=null,fontWeight:FontWeight?=null,
    fontFamily:FontFamily?=null,letterSpacing:TextUnit=TextUnit.Unspecified,textDecoration:TextDecoration?=null,
    textAlign:TextAlign?=null,lineHeight:TextUnit=TextUnit.Unspecified,overflow:TextOverflow=TextOverflow.Clip,
    softWrap:Boolean=true,maxLines:Int=Int.MAX_VALUE,minLines:Int=1,inlineContent:Map<String,InlineTextContent> = mapOf(),
    onTextLayout:((TextLayoutResult)->Unit)?=null,style:TextStyle=LocalTextStyle.current) {
    MaterialText(text,modifier,color,fontSize,fontStyle,readingWeight(fontWeight,style),
        fontFamily?:LocalAppearance.current.family,letterSpacing,textDecoration,textAlign,lineHeight,
        overflow,softWrap,maxLines,minLines,inlineContent,onTextLayout?:{},style)
}
