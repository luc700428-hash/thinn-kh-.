package io.atmos.weather.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** UI-only appearance preference: never touches weather, alarm or OTA state. */
object IOS27AppearanceMode {
    var value by mutableStateOf("dark")
        private set
    private var started = false
    fun initialize(context: Context) {
        if (!started) {
            value = context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
                .getString("ios27_theme_mode", "dark")?.takeIf { it in setOf("light", "dark", "auto") } ?: "dark"
            started = true
        }
    }
    fun update(context: Context, selection: String) {
        require(selection in setOf("light", "dark", "auto"))
        context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE).edit()
            .putString("ios27_theme_mode", selection).apply()
        value = selection
    }
}

/** One coherent 135-degree highlight, applied to controls instead of pretending to be Apple private APIs. */
@Composable
fun IOS27RoundControl(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                      fill: Color = MaterialTheme.colorScheme.surface, foreground: Color = MaterialTheme.colorScheme.onSurface) {
    val response=iosMotionResponse()
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce=rememberAppleReduceMotion()
    val scale by animateFloatAsState(if(pressed && !reduce) .96f else 1f,
        animationSpec=if(reduce)tween(0) else if(pressed)tween(90) else spring(
            dampingRatio=response.damping,stiffness=response.stiffness),label="ios27-round-control")
    val feedback=LocalHapticFeedback.current
    Box(modifier.size(42.dp).graphicsLayer {
            scaleX=scale;scaleY=scale;alpha=if(pressed) .92f else 1f
        }.semantics { contentDescription=when(label) { "‹" -> "返回"; "×" -> "關閉"; "✓" -> "確認";else -> label } }.shadow(5.dp,CircleShape,ambientColor=Color.Black.copy(alpha=.08f),spotColor=Color.Black.copy(alpha=.08f)).clip(CircleShape).thinnBackdrop(rememberAppleReduceTransparency(),21.dp).background(fill.copy(alpha=if(rememberAppleReduceTransparency())1f else .87f))
        .border(0.8.dp, Brush.linearGradient(listOf(Color.White.copy(alpha=.62f),
            Color.White.copy(alpha=.18f), Color.Transparent)), CircleShape)
        .thinnSpecularEdge(21.dp)
        .clickable(interactionSource=interaction,indication=null) {
            feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }, contentAlignment=Alignment.Center) {
        if(label in listOf("‹","×","✓")) Canvas(Modifier.size(20.dp)) {
            val w=size.width;val h=size.height;val stroke=2.dp.toPx()
            fun line(x1:Float,y1:Float,x2:Float,y2:Float) = drawLine(foreground,Offset(w*x1,h*y1),Offset(w*x2,h*y2),stroke,StrokeCap.Round)
            when(label) {
                "‹" -> {line(.63f,.16f,.29f,.5f);line(.29f,.5f,.63f,.84f)}
                "×" -> {line(.24f,.24f,.76f,.76f);line(.76f,.24f,.24f,.76f)}
                else -> {line(.15f,.52f,.4f,.76f);line(.4f,.76f,.86f,.24f)}
            }
        } else Text(label, color=foreground, fontSize=23.sp, fontWeight=FontWeight.Medium)
    }
}

@Composable
private fun LockPreview(dark: Boolean, chosen: Boolean, onChoose: () -> Unit) {
    val background = if (dark) Color(0xFF070F22) else Color(0xFFBBDCE8)
    val label = if (dark) "深色" else "淺色"
    Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.iosJellyPress().clickable(
        interactionSource=remember {MutableInteractionSource()},indication=null,onClick=onChoose)) {
        Box(Modifier.width(111.dp).height(181.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(background, Color(0xFF126DA5), Color(0xFF20B2A0))))
            .border(1.dp, Color.White.copy(alpha=.35f), RoundedCornerShape(20.dp))) {
            Text("09:41", Modifier.align(Alignment.TopCenter).padding(top=24.dp),
                fontSize=26.sp,color=Color.White, fontWeight=FontWeight.Light)
            Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal=9.dp).height(29.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha=.26f)))
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom=23.dp).width(45.dp).height(5.dp)
                .clip(CircleShape).background(Color.White.copy(alpha=.65f)))
        }
        Spacer(Modifier.height(9.dp))
        Text(label, fontSize=16.sp, color=MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.size(23.dp).clip(CircleShape)
            .background(if (chosen) Color(0xFF007AFF) else Color.Transparent)
            .border(if(chosen) 0.dp else 1.5.dp, Color(0xFF8E8E93), CircleShape),
            contentAlignment=Alignment.Center) {
            if(chosen) Text("✓", color=Color.White, fontSize=15.sp, fontWeight=FontWeight.Bold)
        }
    }
}

@Composable
fun IOS27AppearancePage(onBack: () -> Unit, advancedSettings: @Composable () -> Unit = {}) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val context = LocalContext.current
    IOS27AppearanceMode.initialize(context)
    val selection = IOS27AppearanceMode.value
    var advancedOpen by remember { mutableStateOf(false) }
    val auto = selection == "auto"
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkChosen = if (auto) systemDark else selection == "dark"
    val surface by animateColorAsState(MaterialTheme.colorScheme.surface, tween(300), label="appearance-card")
    val primary by animateColorAsState(MaterialTheme.colorScheme.onSurface, tween(300), label="appearance-ink")
    val page by animateColorAsState(MaterialTheme.colorScheme.background, tween(300), label="appearance-page")
    Column(Modifier.fillMaxSize().background(page).statusBarsPadding().navigationBarsPadding()
        .padding(bottom=104.dp).appleBouncingScroll().verticalScroll(rememberScrollState())
        .padding(horizontal=16.dp)) {
        Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment=Alignment.Center) {
            IOS27RoundControl("‹", onBack, modifier=Modifier.align(Alignment.CenterStart).size(40.dp), fill=surface,foreground=primary)
            Text("外觀", fontSize=20.sp,color=primary,fontWeight=FontWeight.SemiBold)
        }
        Spacer(Modifier.height(19.dp))
        Text("外觀", color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp,
            modifier=Modifier.padding(start=12.dp,bottom=10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(surface)) {
            Row(Modifier.fillMaxWidth().padding(top=25.dp,bottom=22.dp),
                horizontalArrangement=Arrangement.SpaceEvenly) {
                LockPreview(false,!darkChosen) { IOS27AppearanceMode.update(context,"light") }
                LockPreview(true,darkChosen) { IOS27AppearanceMode.update(context,"dark") }
            }
            Box(Modifier.fillMaxWidth().padding(start=16.dp).height(.5.dp)
                .background(if(MaterialTheme.colorScheme.background.red > .5f) ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
            Row(Modifier.fillMaxWidth().heightIn(min=55.dp).padding(horizontal=18.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text("自動",Modifier.weight(1f),fontSize=17.sp,color=primary)
                GlassSwitch(checked=auto,onCheckedChange={ selected ->
                    IOS27AppearanceMode.update(context,if(selected) "auto" else if(systemDark) "dark" else "light")
                })
            }
            Box(Modifier.fillMaxWidth().padding(start=16.dp).height(.5.dp)
                .background(if(MaterialTheme.colorScheme.background.red > .5f) ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
            Row(Modifier.fillMaxWidth().heightIn(min=55.dp).iosJellyPress().clickable {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
            }.padding(horizontal=18.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text("選項",Modifier.weight(1f),fontSize=17.sp,color=primary)
                Text(if(auto) "跟隨系統外觀  ›" else "手動選取  ›",fontSize=15.sp,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(15.dp))
        Text("開啟自動時，外觀跟隨手機系統設定；手動模式會保留你的選擇。",
            color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,
            modifier=Modifier.padding(horizontal=12.dp))
        Spacer(Modifier.height(22.dp))
        // Advanced controls are on-demand; the main Settings page stays iOS grouped.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(surface).clickable { advancedOpen=!advancedOpen }
            .padding(horizontal=17.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("進階外觀與動態",color=primary,fontSize=17.sp,modifier=Modifier.weight(1f))
            Text(if(advancedOpen) "收起  ﹀" else "開啟  ›",
                color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp)
        }
        if(advancedOpen) {
            Spacer(Modifier.height(18.dp))
            advancedSettings()
        }
        Spacer(Modifier.height(30.dp))
    }
}
