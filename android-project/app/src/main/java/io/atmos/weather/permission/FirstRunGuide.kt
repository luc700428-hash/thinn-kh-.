package io.atmos.weather.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhonelinkRing
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.atmos.weather.R
import io.atmos.weather.permission.OnboardingPermissions.Item
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlin.math.PI
import kotlin.math.sin

private val Mint = Color(0xFF34C759)

private fun icon(item: Item): ImageVector = when (item) {
    Item.LOCATION -> Icons.Rounded.LocationOn
    Item.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
    Item.BATTERY -> Icons.Rounded.BatteryChargingFull
    Item.FULL_SCREEN -> Icons.Rounded.PhonelinkRing
    Item.OVERLAY -> Icons.Rounded.PictureInPicture
    Item.EXACT_ALARM -> Icons.Rounded.AccessAlarm
    Item.DND -> Icons.Rounded.DoNotDisturbOn
}
private fun tint(item: Item): Color = when (item) {
    Item.LOCATION -> Color(0xFF007AFF)
    Item.NOTIFICATIONS -> Color(0xFFFF3B30)
    Item.BATTERY -> Color(0xFF34C759)
    Item.FULL_SCREEN -> Color(0xFFFF9500)
    Item.OVERLAY -> Color(0xFF5E5CE6)
    Item.EXACT_ALARM -> Color(0xFFFF2D55)
    Item.DND -> Color(0xFF5856D6)
}

/**
 * First launch: a welcome page, then a live checklist of what alerts need to work. Every item can
 * be skipped; rows turn into a green check the moment the system grants them, including after
 * returning from a settings page.
 */
@Composable
fun FirstRunGuide(onContinue: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val reduce = rememberAppleReduceMotion()
    ThinnAppTheme {
        AdaptivePageFrame(Modifier.background(ThinnOpticalMaterial.page), maxContentWidth = 640.dp) {
            AnimatedContent(step, transitionSpec = {
                if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (slideInHorizontally(spring(.86f, 380f)) { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(180)))
            }, label = "首次設定", modifier = Modifier.fillMaxSize().testTag("first-run")) { page ->
                if (page == 0) Welcome { step = 1 } else Checklist(onContinue)
            }
        }
    }
}

/** The buddy floats in a soft glow while the title and points rise in one after another. */
@Composable
private fun Welcome(onNext: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val reduce = rememberAppleReduceMotion()
    val t = if (reduce) .25f else rememberInfiniteTransition(label = "歡迎").animateFloat(0f, 1f,
        infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "漂浮").value
    val bob = sin(t * 2 * PI).toFloat()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(200.dp).iosAppear(0), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val r = size.minDimension * (.46f + .03f * bob)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD66B).copy(alpha = .34f), Color(0xFF7CC4FF).copy(alpha = .10f), Color.Transparent), center, r), r, center)
            }
            Image(painterResource(R.drawable.buddy_vector_still_sun_day), null, Modifier.size(148.dp).graphicsLayer {
                translationY = bob * 6f * density; val s = 1f + .025f * bob; scaleX = s; scaleY = s
            })
        }
        Text("歡迎使用 thinn-khì", color = palette.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.iosAppear(1).padding(top = 8.dp))
        Text("天氣、特報與地震速報，都在你需要的那一刻出現。", color = palette.onSurfaceVariant, fontSize = 16.sp,
            lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.iosAppear(2).padding(top = 8.dp, bottom = 28.dp))
        listOf(
            Triple(Icons.Rounded.PhonelinkRing, "地震來時直接跳出", "速報視窗、倒數與語音，鎖屏也會亮起"),
            Triple(Icons.Rounded.NotificationsActive, "下雨前先提醒你", "雨快到你所在的地方時通知你"),
            Triple(Icons.Rounded.LocationOn, "沒有帳號、沒有廣告", "所有權限都能略過，之後也能在設定裡開啟"),
        ).forEachIndexed { i, (ic, title, detail) ->
            Row(Modifier.fillMaxWidth().iosAppear(3 + i).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(ic, null, Modifier.size(24.dp), tint = palette.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = palette.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(detail, color = palette.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        GlassButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(54.dp).iosAppear(6).testTag("first-run-next")) {
            Text("開始設定", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Checklist(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val palette = MaterialTheme.colorScheme
    val feedback = io.atmos.weather.haptics.LocalHapticEngine.current
    val items = remember { OnboardingPermissions.items() }
    var revision by remember { mutableIntStateOf(0) }
    val granted = remember(revision) { items.associateWith { OnboardingPermissions.granted(ctx, it) } }
    val prefs = remember { ctx.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE) }
    // Re-check whenever the user comes back from a system page.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    // A gentle tick each time another item turns green.
    val count = granted.count { it.value }
    var lastCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > lastCount) feedback?.play(io.atmos.weather.haptics.HapticEngine.Event.TAP)
        lastCount = count
    }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }
    fun ask(item: Item) {
        val perms = OnboardingPermissions.runtime[item]
        val askedKey = "onboarding_asked_${item.name.lowercase()}"
        if (perms != null && perms.isNotEmpty() && !prefs.getBoolean(askedKey, false)) {
            // The home screen must not ask again for what was asked here.
            prefs.edit().putBoolean(askedKey, true)
                .putBoolean(if (item == Item.LOCATION) "location_permission_asked" else "alert_permission_asked", true).apply()
            runtimeLauncher.launch(perms)
        } else OnboardingPermissions.open(ctx, item)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("讓提醒準時送到你手上", color = palette.onSurface, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Text("地震速報需要前四項才能在鎖屏與背景可靠地跳出。", color = palette.onSurfaceVariant,
                    fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.width(12.dp))
            ProgressRing(count, items.size)
        }
        Spacer(Modifier.height(20.dp))
        IOSV25SettingsGroup {
            items.forEachIndexed { i, item ->
                PermissionRow(item, granted[item] == true, last = i == items.lastIndex, index = i) { ask(item) }
            }
        }
        Text("每一項都可以略過；之後可在「地震設定 → 速報 → 速報能不能跳出來」重新設定。", color = palette.onSurfaceVariant,
            fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
        Spacer(Modifier.height(24.dp))
        val all = count == items.size
        GlassButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("first-run-done")) {
            AnimatedContent(if (all) "全部完成，開始使用" else "完成", label = "完成") { Text(it, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (!all) GlassTextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("稍後再設定") }
        Spacer(Modifier.height(20.dp))
    }
}

/** Ring that fills as items are granted; the count in the middle rolls to the new number. */
@Composable
private fun ProgressRing(done: Int, total: Int) {
    val palette = MaterialTheme.colorScheme
    val p by animateFloatAsState(if (total == 0) 1f else done / total.toFloat(), spring(.7f, 120f), label = "進度")
    val track = palette.onSurface.copy(alpha = .12f)
    Box(Modifier.size(64.dp).semantics { contentDescription = "已完成 $done 項，共 $total 項" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = 6.dp.toPx(); val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(Brush.sweepGradient(listOf(Mint, Color(0xFF30D158), Mint)), -90f, 360f * p.coerceIn(0f, 1f), false,
                Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        AnimatedContent(done, transitionSpec = { (scaleIn(spring(.5f, 500f), .6f) + fadeIn()) togetherWith (scaleOut(tween(120)) + fadeOut(tween(120))) },
            label = "數字") { n ->
            Text("$n/$total", color = palette.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionRow(item: Item, granted: Boolean, last: Boolean, index: Int, onAsk: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val light = palette.background.red > .5f
    val on by animateFloatAsState(if (granted) 1f else 0f, spring(.48f, 420f), label = "已允許")
    Column(Modifier.iosAppear(index)) {
        Row(Modifier.fillMaxWidth().testTag("first-run-${item.name.lowercase()}")
            .semantics(mergeDescendants = true) { contentDescription = "${item.title}，${if (granted) "已允許" else "尚未允許"}，${item.reason}" }
            .clickable(enabled = !granted, role = Role.Button, onClick = onAsk)
            .padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(tint(item)), contentAlignment = Alignment.Center) {
                Icon(icon(item), null, Modifier.size(21.dp), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, color = palette.onSurface, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                    if (item.essential && !granted) {
                        Spacer(Modifier.width(6.dp))
                        Text("建議", color = if (light) Color(0xFFB25000) else Color(0xFFFFB340), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFF9500).copy(alpha = if (light) .14f else .22f))
                                .padding(horizontal = 7.dp, vertical = 1.dp))
                    }
                }
                Text(item.reason, color = palette.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
            }
            Spacer(Modifier.width(10.dp))
            // 「允許」 capsule shrinks away as a green check springs in.
            Box(contentAlignment = Alignment.Center) {
                if (on < .99f) Row(Modifier.graphicsLayer { val s = 1f - on; scaleX = s; scaleY = s; alpha = 1f - on }
                    .clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("允許", color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (OnboardingPermissions.runtime[item].isNullOrEmpty()) {
                        Spacer(Modifier.width(3.dp)); Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp), tint = palette.primary)
                    }
                }
                if (on > .01f) Box(Modifier.size(28.dp).graphicsLayer { scaleX = on; scaleY = on }.clip(RoundedCornerShape(999.dp)).background(Mint),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White) }
            }
        }
        if (!last) Box(Modifier.padding(start = 61.dp).fillMaxWidth().height(.5.dp)
            .background(if (light) ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
    }
}
