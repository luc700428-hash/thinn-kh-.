package io.atmos.weather.permission
import io.atmos.weather.ui.appleBouncingScroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.R
import io.atmos.weather.ui.*

/** No account signup: this app starts with a concise, optional permission guide. */
@Composable
fun FirstRunGuide(onContinue:()->Unit) {
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    var visible by remember {mutableStateOf(false)}
    LaunchedEffect(Unit){visible=true}
    ThinnAppTheme {
        Column(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1A2C),Color(0xFF1D435F),Color(0xFF2A6484))))
            .appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=25.dp,vertical=36.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.height(35.dp))
            // The logo is embedded in the atmospheric background, not a separate solid tile.
            Box(Modifier.size(156.dp).background(Brush.radialGradient(listOf(
                ThinnDesignTokens.accent.copy(alpha=.16f),Color.Transparent))),
                contentAlignment=Alignment.Center) {
                Image(painterResource(R.drawable.brand_symbol),"thinn-khì 品牌標誌",Modifier.size(123.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("thinn-khì",color=Color.White,fontSize=34.sp,fontWeight=FontWeight.Bold)
            Text("好天氣，從你所在的地方開始",fontSize=15.sp,color=ThinnDesignTokens.secondaryInkDark)
            Spacer(Modifier.height(33.dp))
            AnimatedVisibility(visible,enter=fadeIn(tween(if(reduce)0 else 350))+
                slideInVertically(animationSpec=tween(if(reduce)0 else 350)){it/14}) {
                Column(verticalArrangement=Arrangement.spacedBy(11.dp)) {
                    IntroItem(Icons.Rounded.LocationOn,"位置資訊","提供所在縣市的預報；拒絕後仍能搜尋其他城市。")
                    IntroItem(Icons.Rounded.NotificationsActive,"通知提醒","天氣警特報、個人事項提醒需經你允許。")
                    IntroItem(Icons.Rounded.Security,"尊重你的選擇","所有權限都能略過，之後再從設定開啟。")
                }
            }
            Spacer(Modifier.height(27.dp))
            GlassButton(onClick=onContinue,modifier=Modifier.fillMaxWidth().height(54.dp)) {
                Text("開始使用",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
            }
            Spacer(Modifier.height(15.dp))
            Text("不必註冊帳號 · 不會要求 GitHub 登入",fontSize=14.sp,
                color=ThinnDesignTokens.secondaryInkDark,textAlign=TextAlign.Center)
        }
    }
}

@Composable
private fun IntroItem(icon:ImageVector,title:String,detail:String) {
    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.10f),RoundedCornerShape(19.dp))
        .border(1.dp,Color.White.copy(alpha=.15f),RoundedCornerShape(19.dp))
        .padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,Modifier.size(24.dp),tint=ThinnDesignTokens.accent)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            Text(detail,color=ThinnDesignTokens.secondaryInkDark,fontSize=14.sp,lineHeight=18.sp)
        }
    }
}
