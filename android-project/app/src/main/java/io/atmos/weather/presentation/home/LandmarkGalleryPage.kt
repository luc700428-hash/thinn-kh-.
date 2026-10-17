package io.atmos.weather.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.domain.visual.LandmarkPlace

/** Browse every locally bundled 2D picture. No GPU/Filament, safe on unsupported devices. */
@Composable
fun LandmarkGalleryPage(places: List<LandmarkPlace>, onClose: () -> Unit) {
    BackHandler { onClose() }
    var international by remember { mutableStateOf(false) }
    var selectedRegion by remember { mutableStateOf("全部") }
    var enlarged by remember { mutableStateOf<LandmarkPlace?>(null) }
    val taiwan = places.filter { it.county !in setOf("東京", "京都", "首爾", "新加坡", "巴黎", "倫敦", "紐約", "舊金山") }
    val abroad = places.filter { it !in taiwan }
    val activeList = if (international) abroad else taiwan
    val regions = remember(international, places) { listOf("全部") + activeList.map { it.displayRegion }.distinct() }
    val shown = activeList.filter { selectedRegion == "全部" || it.displayRegion == selectedRegion }
    val bg = Color(0xFF071D31)
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("2D 地標探索", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("已收錄 ${places.size} 處 · 每處皆有獨立 2D 插畫", color = Color(0xFFBBD5E7),fontSize = 14.sp)
            }
            Text("返回", color = Color.White, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A506C)).clickable(onClick = onClose)
                    .padding(horizontal = 16.dp, vertical = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(false to "臺灣與離島 (${taiwan.size})", true to "國外 (${abroad.size})").forEach { (isAbroad,title) ->
                FilterChip(selected = international == isAbroad,
                    onClick = { international = isAbroad; selectedRegion = "全部" },
                    label = { Text(title, fontSize = 14.sp) })
            }
        }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 13.dp),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions) { region ->
                FilterChip(selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                    label = { Text(region, fontSize = 14.sp) })
            }
        }
        Text("以下為風格插畫，非景點實景照片或精確建築模型。", color = Color(0xFFA9C3D2), fontSize = 14.sp,
            modifier = Modifier.padding(start = 19.dp, end = 19.dp, bottom = 9.dp))
        LazyColumn(contentPadding = PaddingValues(start = 15.dp,end = 15.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown,key = { it.id }) { place ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF173751))
                    .border(1.dp,Color(0xFF43617A),RoundedCornerShape(18.dp))
                    .clickable { enlarged = place }
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LandmarkGalleryBitmap(place, Modifier.width(174.dp).height(114.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(start = 13.dp),verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(place.name,color = Color.White,fontSize = 17.sp,fontWeight = FontWeight.SemiBold)
                        Text(place.displayRegion,color = Color(0xFFB8D5E6),fontSize = 14.sp)
                        Text("點選放大 2D 圖卡",color = Color(0xFF8BDBF5),fontSize = 14.sp)
                    }
                }
            }
        }
    }
    val chosen = enlarged
    if(chosen!=null) {
        BackHandler { enlarged = null }
        Dialog(onDismissRequest = { enlarged = null },properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment = Alignment.CenterVertically) {
                    Text(chosen.caption,color = Color.White,fontSize = 20.sp,fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("關閉",color = Color.White,fontSize = 16.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A506C)).clickable { enlarged = null }
                            .padding(horizontal = 18.dp, vertical = 11.dp))
                }
                LandmarkGalleryBitmap(chosen,Modifier.fillMaxWidth().weight(1f))
                Text("獨立 2D 風格插畫 · 非實景照片", color = Color(0xFFC8DAE5),fontSize = 15.sp,
                    modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun LandmarkGalleryBitmap(place: LandmarkPlace,modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val image = remember(place.id) {
        listOfNotNull(place.illustration,place.preview).firstNotNullOfOrNull { path ->
            runCatching {
                ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    if(image!=null) Image(image,contentDescription="${place.caption}，2D 地標風格插畫",
        modifier = modifier.background(Color(0xFF28485D)),contentScale=ContentScale.Fit)
    else Box(modifier.background(Color(0xFF28485D)),contentAlignment = Alignment.Center) {
        Text("2D 圖片暫時無法讀取",color = Color.White,fontSize=15.sp)
    }
}
