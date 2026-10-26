package io.atmos.weather.ui
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.widgets.WidgetSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Composable fun IOSDeviceSurfacesSettings(onBack:()->Unit){
 val ctx=LocalContext.current;val p=remember{ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)};val palette=MaterialTheme.colorScheme
 var motion by remember{mutableStateOf(p.getBoolean("widget_motion_enabled",true))};var stamp by remember{mutableStateOf(p.getBoolean("widget_show_updated",true))};var agenda by remember{mutableStateOf(p.getBoolean("widget_show_agenda",true))};var weather by remember{mutableStateOf(p.getBoolean("island_weather_details",true))}
 var capability by remember{mutableStateOf<XiaomiFocusBridge.Availability?>(null)};var protocol by remember{mutableIntStateOf(0)}
 LaunchedEffect(Unit){withContext(Dispatchers.IO){XiaomiFocusBridge.availability(ctx) to XiaomiFocusBridge.protocol(ctx)}.let{capability=it.first;protocol=it.second}}
 fun changed(key:String,value:Boolean){p.edit().putBoolean(key,value).apply();WidgetSync.refreshCached(ctx)}
 Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()){
  IOSSettingsHeader("小工具與動態島",onBack)
  Column(Modifier.fillMaxSize().appleBouncingScroll().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
   Text("桌面小工具",color=palette.onSurfaceVariant,fontSize=14.sp)
   IOSV25SettingsGroup{
    GlassToggle("動態天氣圖示","配合桌面、省電與減少動態效果設定",motion){motion=it;changed("widget_motion_enabled",it)};GlassDivider()
    GlassToggle("顯示資料時間","保留快取及過期狀態",stamp){stamp=it;changed("widget_show_updated",it)};GlassDivider()
    GlassToggle("顯示即將到來的事項","天氣時鐘版面中的下一個私人事項",agenda){agenda=it;changed("widget_show_agenda",it)}
   }
   Text("長按桌面加入小工具，可選即時天氣、逐時預報、天氣時鐘、氣象數據與安心資訊，以及五種配色。調整大小會改變資訊密度。",color=palette.onSurfaceVariant,fontSize=14.sp)
   GlassButton(onClick={WidgetSync.request(ctx)},modifier=Modifier.fillMaxWidth()){Text("同步桌面天氣")}
   Text("小米超級島",color=palette.onSurfaceVariant,fontSize=14.sp)
   IOSV25SettingsGroup{GlassToggle("鬧鐘與天氣詳細資訊","僅附加有效天氣快取，保留真實標籤及操作",weather){weather=it;p.edit().putBoolean("island_weather_details",it).apply()}}
   Text(when(capability){null->"正在檢查裝置相容性";XiaomiFocusBridge.Availability.ENABLED->"已啟用 · 系統協定 $protocol";XiaomiFocusBridge.Availability.NOT_XIAOMI->"此裝置使用通知與 App 響鈴介面";XiaomiFocusBridge.Availability.UNSUPPORTED->"此系統版本未提供相容的超級島協定";else->"系統尚未授權此 App 的焦點通知"},color=palette.onSurfaceVariant,fontSize=14.sp)
   GlassOutlinedButton(onClick={ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName))},modifier=Modifier.fillMaxWidth()){Text("開啟通知設定")}
   Text("桌面時鐘及倒數由桌面程式更新；超級島的展開、動畫與顯示由小米系統管理。資料更新排程仍受手機背景限制影響。",color=palette.onSurfaceVariant,fontSize=14.sp)
   Spacer(Modifier.height(100.dp))
  }
 }
}
