package io.atmos.weather.next.features

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.data.OpenMeteoWeather
import io.atmos.weather.next.design.*
import io.atmos.weather.next.domain.*
import io.atmos.weather.next.platform.NextWeatherWidget

private enum class Screen(val title:String,val glyph:String) {
    WEATHER("天氣","☀"),QUAKE("地震","◉"),TYPHOON("颱風","◎"),ALARM("鬧鐘","◷"),SETTINGS("設定","⚙")
}
/** Root owns one shared navigation stack. Nested sheets own their own BackHandler. */
@Composable
fun NextApp() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val user=remember(context) {context.getSharedPreferences("next_user_v1",Context.MODE_PRIVATE)}
    var city by remember {mutableStateOf(KnownCities.cities.firstOrNull {it.id==user.getString("city","taipei")}
        ?:KnownCities.cities.first())}
    var screen by remember {mutableStateOf(Screen.WEATHER)}
    val navigationOrigins=remember {mutableStateListOf<Screen>()}
    BackHandler(enabled=navigationOrigins.isNotEmpty()) {
        screen=navigationOrigins.removeAt(navigationOrigins.lastIndex)
    }
    var reduceMotion by remember {mutableStateOf(user.getBoolean("reduce_motion",false))}
    var fahrenheit by remember {mutableStateOf(user.getBoolean("fahrenheit",false))}
    var haptics by remember {mutableStateOf(user.getBoolean("haptics",true))}
    var weather by remember {mutableStateOf<WeatherLoad?>(null)}
    var refreshing by remember {mutableStateOf(false)}
    var refreshCount by remember {mutableIntStateOf(0)}
    val api=remember(context){OpenMeteoWeather(context)}
    LaunchedEffect(city,refreshCount) {
        refreshing=true
        weather=null
        try {weather=api.load(city)}
        finally {refreshing=false;NextWeatherWidget.refresh(context)}
    }
    val reading=when(val w=weather) {
        is WeatherLoad.Live->w.reading;is WeatherLoad.Cached->w.reading;else->null
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(screen==Screen.WEATHER) AtmosphericBackground(reading?.scene,reduceMotion,Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(targetState=screen,transitionSpec={
                    if(reduceMotion)fadeIn(tween(110)) togetherWith fadeOut(tween(110))
                    else (fadeIn(tween(260))+slideInHorizontally(tween(330)){it/12}) togetherWith
                        (fadeOut(tween(190))+slideOutHorizontally(tween(270)){-it/16})
                },label="iosPageNavigation") { page->
                    when(page){
                        Screen.WEATHER->IosWeatherPage(city,reading,weather,refreshing,fahrenheit,
                            onCity={city=it;user.edit().putString("city",it.id).apply()},
                            onRetry={if(!refreshing)refreshCount++},onOpenSettings={
                                navigationOrigins.add(Screen.WEATHER);screen=Screen.SETTINGS
                            })
                        Screen.ALARM->IosAlarmPage()
                        Screen.SETTINGS->IosSettingsPage(reduceMotion,{next->
                            reduceMotion=next;user.edit().putBoolean("reduce_motion",next).apply()
                        },fahrenheit,{next->
                            fahrenheit=next;user.edit().putBoolean("fahrenheit",next).apply()
                        },haptics,{next->
                            haptics=next;user.edit().putBoolean("haptics",next).apply()
                        })
                        Screen.QUAKE->UnderConstruction("地震中心","◉",
                            "真實地震速報、測站及推播仍在新架構驗證。此頁不會假造地震警報。")
                        Screen.TYPHOON->UnderConstruction("颱風中心","◎",
                            "官方颱風路徑與即時地圖仍在新架構接入中；無資料不顯示推測路徑。")
                    }
                }
            }
            IosAppTabBar(screen){ next ->
                if(next!=screen){navigationOrigins.add(screen);screen=next}
            }
        }
    }
}
@Composable
private fun UnderConstruction(title:String,icon:String,message:String) {
    Column(Modifier.fillMaxSize().background(IosColor.background).padding(23.dp)) {
        Spacer(Modifier.height(25.dp))
        IosText(title,36,FontWeight.Bold)
        Spacer(Modifier.height(44.dp))
        IosText(icon,53,color=IosColor.blue)
        Spacer(Modifier.height(15.dp))
        IosGroup(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(18.dp)){IosText(message,16,color=IosColor.secondary)}
        }
        Spacer(Modifier.height(14.dp))
        IosText("尚未完成的功能不應取代舊版正式服務。",14,color=IosColor.muted)
    }
}
@Composable
private fun IosAppTabBar(selected:Screen,onPick:(Screen)->Unit) {
    val dark=selected!=Screen.WEATHER
    Row(Modifier.fillMaxWidth()
        .background(if(dark)Color(0xFF131314) else Color(0xFF193C5C).copy(alpha=.88f))
        .border(.5.dp,Color.White.copy(alpha=if(dark).10f else .18f))
        .padding(top=9.dp,bottom=4.dp,start=9.dp,end=9.dp),
        horizontalArrangement=Arrangement.SpaceBetween) {
        Screen.entries.forEach {tab->
            val current=selected==tab
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable{onPick(tab)}
                .padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                val tint=when {
                    !current->Color.White.copy(alpha=.56f)
                    tab==Screen.ALARM->IosColor.orange
                    tab==Screen.WEATHER->Color(0xFFF0FAFF)
                    else->IosColor.blue
                }
                IosText(tab.glyph,23,color=tint)
                Spacer(Modifier.height(2.dp))
                IosText(tab.title,14,if(current)FontWeight.SemiBold else FontWeight.Normal,tint)
            }
        }
    }
}
