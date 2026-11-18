package io.atmos.weather.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected as selectedState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.atmos.weather.R
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.AppleMotion
import io.atmos.weather.ui.IOSSettingsHeader
import io.atmos.weather.ui.IOSV25SettingsGroup
import io.atmos.weather.ui.SettingsChevron
import io.atmos.weather.ui.ThinnDesignTokens
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosJellyPress
import io.atmos.weather.ui.rememberAppleReduceMotion
import kotlin.math.PI
import kotlin.math.sin

private val Rose = Color(0xFFFF5E7E)
private val Coral = Color(0xFFFF9A6B)

/** The buddy, breathing in a warm glow, with small hearts drifting up. Still when motion is reduced. */
@Composable
private fun SponsorBuddy(size: androidx.compose.ui.unit.Dp, hearts: Boolean, hop: Int = 0) {
    val reduce = rememberAppleReduceMotion()
    val clock = if (reduce) null else rememberInfiniteTransition(label = "贊助夥伴")
    val t = clock?.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "呼吸")?.value ?: .25f
    // A happy hop after the user comes back from paying.
    val jump = remember { Animatable(0f) }
    LaunchedEffect(hop) {
        if (hop > 0 && !reduce) {
            jump.animateTo(1f, spring(.45f, 420f)); jump.animateTo(0f, spring(.5f, 260f))
        }
    }
    val bob = sin(t * 2 * PI).toFloat()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension * (.48f + .03f * bob)
            drawCircle(Brush.radialGradient(listOf(Rose.copy(alpha = .30f), Coral.copy(alpha = .10f), Color.Transparent),
                center, r), r, center)
        }
        Image(painterResource(R.drawable.buddy_vector_still_sun_day), null,
            Modifier.fillMaxSize(.72f).graphicsLayer {
                translationY = (bob * 4f - jump.value * 26f) * density
                val s = 1f + .02f * bob + .06f * jump.value; scaleX = s; scaleY = s
            })
        if (hearts && clock != null) for (i in 0 until 3) {
            val phase = (t + i / 3f) % 1f
            Icon(Icons.Rounded.Favorite, null, tint = Rose,
                modifier = Modifier.align(Alignment.Center).size(14.dp + 4.dp * i).graphicsLayer {
                    translationX = (size.toPx() * (.22f + .12f * i) * if (i % 2 == 0) 1f else -1f) + sin(phase * 2 * PI).toFloat() * 6f * density
                    translationY = size.toPx() * (.10f - .48f * phase)
                    alpha = (1f - phase) * (phase * 6f).coerceAtMost(1f)
                    val s = .7f + .5f * phase; scaleX = s; scaleY = s
                })
        }
    }
}

/** Top-of-settings card, like the account card on iOS. */
@Composable
fun SponsorSettingsCard(onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val feedback = io.atmos.weather.haptics.LocalHapticEngine.current
    IOSV25SettingsGroup {
        Row(Modifier.fillMaxWidth().iosJellyPress().testTag("sponsor-card")
            .semantics(mergeDescendants = true) { contentDescription = "贊助 thinn-khì，讓天氣與地震提醒持續免費" }
            .clickable(role = Role.Button) { feedback?.play(io.atmos.weather.haptics.HapticEngine.Event.TAP); onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SponsorBuddy(56.dp, hearts = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("贊助 thinn-khì", color = palette.onSurface, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("讓天氣與地震提醒持續免費", color = palette.onSurfaceVariant, fontSize = 15.sp)
            }
            Icon(Icons.Rounded.Favorite, null, tint = Rose, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            SettingsChevron()
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, title: String, detail: String, last: Boolean) {
    val palette = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(19.dp), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.onSurface, fontSize = 17.sp, lineHeight = 22.sp)
                Text(detail, color = palette.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        if (!last) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(.5.dp)
            .background(if (palette.background.red > .5f) ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
    }
}

/** The big rose capsule. A soft light sweeps across it now and then; it squishes when pressed. */
@Composable
private fun SponsorButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    val reduce = rememberAppleReduceMotion()
    val sweep = if (reduce || !enabled) -1f else rememberInfiniteTransition(label = "按鈕光")
        .animateFloat(-.4f, 1.6f, infiniteRepeatable(tween(2800, delayMillis = 1400, easing = AppleMotion.fluid)), label = "掃光").value
    val shape = RoundedCornerShape(9999.dp)
    Box(Modifier.fillMaxWidth().height(56.dp).iosJellyPress().clip(shape)
        // Deep rose to deep orange: white text keeps at least 4.5:1 along the whole capsule.
        .background(if (enabled) Brush.horizontalGradient(listOf(Color(0xFFC2185B), Color(0xFFC93E14))) else Brush.horizontalGradient(listOf(Color(0xFF8E8E93), Color(0xFF8E8E93))))
        .testTag("sponsor-button")
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (sweep > -1f) Canvas(Modifier.matchParentSize()) {
            val x = size.width * sweep
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .28f), Color.Transparent),
                Offset(x - size.height * 1.4f, 0f), Offset(x + size.height * 1.4f, size.height)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VolunteerActivism, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            // The label rolls to the new amount when another way of supporting is picked.
            androidx.compose.animation.AnimatedContent(if (enabled) label else "贊助連結準備中", transitionSpec = {
                (androidx.compose.animation.slideInVertically(spring(.8f, 520f)) { it / 2 } + fadeIn(tween(160))) togetherWith
                    (androidx.compose.animation.slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(120)))
            }, label = "贊助金額") { text ->
                Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

private fun tierIcon(id: String): ImageVector = when (id) {
    "coffee" -> Icons.Rounded.LocalCafe
    "snack" -> Icons.Rounded.Cookie
    "feed" -> Icons.Rounded.Autorenew
    "meal" -> Icons.Rounded.RamenDining
    "build" -> Icons.Rounded.Code
    else -> Icons.Rounded.Edit
}
private fun tierTint(id: String): Color = when (id) {
    "coffee" -> Color(0xFFA2845E)
    "snack" -> Color(0xFFFF9500)
    "feed" -> Rose
    "meal" -> Color(0xFFFF6B3D)
    "build" -> Color(0xFF5E5CE6)
    else -> Color(0xFF30B0C7)
}

/** iOS plan picker: one row per way of supporting; the chosen row gets a check that springs in. */
@Composable
private fun TierPicker(selected: String, onSelect: (Sponsor.Tier) -> Unit) {
    val palette = MaterialTheme.colorScheme
    val light = palette.background.red > .5f
    IOSV25SettingsGroup {
        Sponsor.tiers.forEachIndexed { i, t ->
            val on = t.id == selected
            val pick by animateFloatAsState(if (on) 1f else 0f, spring(.55f, 520f), label = "選取")
            Column {
                Row(Modifier.fillMaxWidth().iosJellyPress().testTag("sponsor-tier-${t.id}")
                    .background(Rose.copy(alpha = .07f * pick))
                    .semantics(mergeDescendants = true) {
                        selectedState = on
                        contentDescription = "${t.title}，${Sponsor.price(t)}，${t.detail}"
                    }
                    .clickable(role = Role.RadioButton) { onSelect(t) }
                    .padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(tierTint(t.id)), contentAlignment = Alignment.Center) {
                        Icon(tierIcon(t.id), null, Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title, color = palette.onSurface, fontSize = 17.sp, lineHeight = 22.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false))
                            if (t.monthly) {
                                Spacer(Modifier.width(6.dp))
                                // Rose ink on a pale rose capsule, readable in both appearances.
                                Text("每月", color = if (light) Color(0xFFC2185B) else Color(0xFFFF8FA8), fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Rose.copy(alpha = if (light) .12f else .22f))
                                        .padding(horizontal = 7.dp, vertical = 1.dp))
                            }
                        }
                        Text(t.detail, color = palette.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(Sponsor.amountLabel(t), color = if (on) palette.onSurface else palette.onSurfaceVariant, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    // Empty ring when not chosen; a filled rose check that pops in when chosen.
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(999.dp))
                            .background(palette.onSurfaceVariant.copy(alpha = .35f * (1f - pick))))
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(999.dp)).background(palette.surface.copy(alpha = 1f - pick)))
                        Box(Modifier.matchParentSize().graphicsLayer { val sc = pick.coerceIn(0f, 1.2f); scaleX = sc; scaleY = sc }
                            .clip(RoundedCornerShape(999.dp)).background(Rose), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
                if (i < Sponsor.tiers.lastIndex) Box(Modifier.padding(start = 59.dp).fillMaxWidth().height(.5.dp)
                    .background(if (light) ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
            }
        }
    }
}

@Composable
fun SponsorPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val palette = MaterialTheme.colorScheme
    val feedback = io.atmos.weather.haptics.LocalHapticEngine.current
    var leftToPay by rememberSaveable { mutableStateOf(false) }
    var thanked by rememberSaveable { mutableStateOf(false) }
    var hop by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var tierId by rememberSaveable { mutableStateOf(Sponsor.defaultTier.id) }
    val tier = Sponsor.tiers.firstOrNull { it.id == tierId } ?: Sponsor.defaultTier
    // Coming back from ECPay: we cannot know whether a payment went through, so the thanks is
    // phrased for either case and never claims a payment.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && leftToPay) { leftToPay = false; thanked = true; hop++ }
        }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding().testTag("sponsor-page")) {
        IOSSettingsHeader("贊助", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.iosAppear(0)) { SponsorBuddy(168.dp, hearts = true, hop = hop) }
            Text("支持 thinn-khì", color = palette.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.iosAppear(1).padding(top = 4.dp))
            Text("沒有廣告，所有功能都免費。\n如果它陪你躲過一場雨、早一步知道地震，\n歡迎請它喝杯咖啡。",
                color = palette.onSurfaceVariant, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                modifier = Modifier.iosAppear(2).padding(top = 8.dp, bottom = 20.dp))
            AnimatedVisibility(thanked, enter = scaleIn(spring(.55f, 380f), initialScale = .85f) + fadeIn(),
                exit = scaleOut(targetScale = .9f) + fadeOut()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(16.dp))
                    .background(Rose.copy(alpha = .14f)).padding(14.dp).testTag("sponsor-thanks"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, null, tint = Rose, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("謝謝你！不論剛剛有沒有完成贊助，有你在用就是最大的支持。",
                        color = palette.onSurface, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                }
            }
            Column(Modifier.fillMaxWidth().iosAppear(3)) {
                Text("你的贊助會用在", fontSize = 14.sp, color = palette.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                IOSV25SettingsGroup {
                    InfoRow(Icons.Rounded.NotificationsActive, Color(0xFFFF9500), "推播與資料服務", "地震速報、特報與天氣資料的費用", false)
                    InfoRow(Icons.Rounded.AutoAwesome, Color(0xFF5E5CE6), "新功能與每次更新", "讓小工具、動畫與提醒越來越好", false)
                    InfoRow(Icons.Rounded.Block, Color(0xFF34C759), "保持沒有廣告", "畫面只留給天氣與你", true)
                }
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().iosAppear(4)) {
                Text("選擇支持方式", fontSize = 14.sp, color = palette.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                TierPicker(tier.id) { t ->
                    if (t.id != tierId) { tierId = t.id; feedback?.play(io.atmos.weather.haptics.HapticEngine.Event.TAP) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.iosAppear(5)) {
                val label = if (tier.amount == null) "前往綠界，輸入金額" else "以 ${Sponsor.price(tier)} 贊助"
                SponsorButton(Sponsor.isAllowed(tier.link), label) {
                    feedback?.play(io.atmos.weather.haptics.HapticEngine.Event.TAP)
                    if (Sponsor.open(ctx, tier.link)) { leftToPay = true; failed = false } else failed = true
                }
            }
            if (failed) Text("無法開啟綠界頁面，請確認手機有可用的瀏覽器。", color = Color(0xFFFF3B30), fontSize = 14.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().iosAppear(6)) {
                Text("付款說明", fontSize = 14.sp, color = palette.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                IOSV25SettingsGroup {
                    InfoRow(Icons.Rounded.Lock, Color(0xFF007AFF), "由綠界科技 ECPay 處理", "付款在綠界的安全頁面完成", false)
                    InfoRow(Icons.Rounded.Payments, Color(0xFF30B0C7), "確認後才付款", "在綠界頁面確認金額與付款方式後才會扣款", false)
                    InfoRow(Icons.Rounded.Favorite, Rose, "App 不會取得付款資料", "thinn-khì 看不到你的卡號或帳戶", true)
                }
            }
            Text("贊助是自由的心意，不會解鎖額外功能。",
                color = palette.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 120.dp))
        }
    }
}
