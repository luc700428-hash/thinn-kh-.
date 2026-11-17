package io.atmos.weather.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.domain.model.*
import io.atmos.weather.location.*
import io.atmos.weather.presentation.home.LocationFeedback
import kotlinx.coroutines.delay

@Composable fun CitySearchSheet(selected:WeatherCity,saved:List<WeatherCity>,results:List<WeatherCity>,
    searching:Boolean,searchError:String?,locationFeedback:LocationFeedback,onLocate:()->Unit,
    onQuery:(String)->Unit,onSelect:(WeatherCity)->Unit,onRemove:(String)->Unit,onClose:()->Unit) {
    val context=LocalContext.current;val palette=MaterialTheme.colorScheme
    var query by rememberSaveable{mutableStateOf("")};var submitted by remember{mutableStateOf("")}
    var scope by rememberSaveable{mutableIntStateOf(0)}
    var recent by remember{mutableStateOf(RecentCityStore.read(context))}
    LaunchedEffect(query) {if(query.isBlank()){submitted="";onQuery("")}else {delay(280);if(submitted!=query.trim()){submitted=query.trim();onQuery(submitted)}}}
    val shown=results.filter {val tw=it.name.startsWith("台灣")||LocationLabels.probablyTaiwan(it.coordinate)||LocationLabels.isDiaoyutai(it.coordinate)
        scope==0||scope==1&&tw||scope==2&&!tw}
    WorkspaceSearchDialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        AppSystemBars(palette.background.red<.5f)
        Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            IOSSettingsHeader("地點與城市",onClose)
            Row(Modifier.padding(horizontal=16.dp,vertical=8.dp).fillMaxWidth().background(palette.surfaceVariant,RoundedCornerShape(28.dp)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search,null,tint=palette.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(query,{query=it},singleLine=true,
                    textStyle=androidx.compose.ui.text.TextStyle(color=palette.onSurface,fontSize=17.sp,fontFamily=io.atmos.weather.presentation.home.LocalAppearance.current.family,fontWeight=FontWeight(io.atmos.weather.presentation.home.LocalAppearance.current.fontWeight.toInt().coerceIn(100,900))),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submitted=query.trim();onQuery(submitted)}),
                    modifier=Modifier.weight(1f).padding(vertical=17.dp),decorationBox={inner->Box {if(query.isEmpty())Text("城市、鄉鎮或緯度,經度",color=palette.onSurfaceVariant,fontSize=16.sp);inner()}})
                if(query.isNotEmpty())GlassIconButton({query=""}){Icon(Icons.Rounded.Close,"清除搜尋")}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                GlassTextButton(onClick=onLocate,enabled=!locationFeedback.inProgress) {Icon(Icons.Rounded.MyLocation,null);Spacer(Modifier.width(8.dp));Text(if(locationFeedback.inProgress)"正在定位…" else "使用目前位置")}
            }
            if(query.isBlank()) {
                // Quick picks like iOS search suggestions: one tap fills the search.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=6.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("台北","台中","高雄","花蓮","墾丁","阿里山","日月潭","東京","大阪","首爾","香港","新加坡","曼谷","倫敦","紐約").forEachIndexed{i,name->
                        Text(name,color=palette.onSurface,fontSize=15.sp,fontWeight=FontWeight.Medium,
                            modifier=Modifier.iosAppear(i).iosJellyPress().clip(RoundedCornerShape(999.dp)).background(palette.surfaceVariant)
                                .clickable{query=name}.padding(horizontal=14.dp,vertical=8.dp))
                    }
                }
            }
            if(locationFeedback.detail.isNotBlank())Text(locationFeedback.detail,color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(horizontal=20.dp,vertical=6.dp))
            if(query.isNotBlank()) {
                GlassChoiceGroup(listOf("全部","台灣","國外"),scope,{scope=it},Modifier.padding(horizontal=16.dp,vertical=8.dp))
                if(searching)IOSLinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=16.dp))
                searchError?.let{Text(it,color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(horizontal=20.dp,vertical=6.dp))}
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal=16.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                if(query.isNotBlank()) {
                    item{Text("搜尋結果 · 點選儲存並查看",fontSize=14.sp,color=palette.onSurfaceVariant,modifier=Modifier.padding(vertical=10.dp))}
                    items(shown,key={"result-${it.id}"}){CityResultRow(it,selected,onSelect,query)}
                    if(!searching&&shown.isEmpty())item{Text("沒有找到相符地點。可試試完整縣市＋鄉鎮名稱、英文或座標。",color=palette.onSurfaceVariant,fontSize=15.sp,modifier=Modifier.padding(vertical=18.dp))}
                } else {
                    if(recent.isNotEmpty()) {
                        item{Row(verticalAlignment=Alignment.CenterVertically){
                            Text("最近查看",color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.weight(1f).padding(vertical=12.dp))
                            Text("清除",color=palette.primary,fontSize=14.sp,modifier=Modifier.clickable{RecentCityStore.clear(context);recent=emptyList()}.padding(8.dp))}}
                        items(recent,key={"recent-${it.id}"}){CityResultRow(it,selected,onSelect)}
                    }
                    item{Text("常用地點 · ${saved.size}/30",color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(vertical=12.dp))}
                    if(saved.isEmpty())item{Text("搜尋並選取地點後，會加入這裡。",color=palette.onSurfaceVariant,fontSize=15.sp)}
                    items(saved,key={"saved-${it.id}"}){city->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)){CityResultRow(city,selected,onSelect)}
                            GlassIconButton({onRemove(city.id)}){Icon(Icons.Rounded.Close,"移除${city.name}")}
                        }
                    }
                    item{Text("快速查看",color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(vertical=12.dp))}
                    items(SampleCities.all.take(6),key={"quick-${it.id}"}){CityResultRow(it,selected,onSelect)}
                }
                item{Text("鄉鎮地名與座標：GeoNames（CC BY 4.0）· 景點與地標：OpenStreetMap（ODbL）\n行政區名稱：內政部國土測繪中心。座標為地區代表位置。",color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(vertical=16.dp))}
            }
        }
    }
}
@Composable private fun CityResultRow(city:WeatherCity,selected:WeatherCity,onSelect:(WeatherCity)->Unit,query:String="") {
    val p=MaterialTheme.colorScheme;val parts=LocationLabels.display(city.name).split(" · ")
    Row(Modifier.fillMaxWidth().iosJellyPress().clickable{onSelect(city)}.padding(horizontal=6.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(if(city.id.startsWith("osm-"))Icons.Rounded.Landscape else Icons.Rounded.Place,null,tint=p.primary,modifier=Modifier.size(23.dp));Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            // The matched part of the name is bold, as in iOS search results.
            val name=parts.last();val at=if(query.isBlank())-1 else name.replace('臺','台').indexOf(query.trim().replace('臺','台'))
            Text(if(at<0)androidx.compose.ui.text.AnnotatedString(name) else androidx.compose.ui.text.buildAnnotatedString{
                append(name.substring(0,at))
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight=FontWeight.Bold,color=p.primary));append(name.substring(at,at+query.trim().length));pop()
                append(name.substring(at+query.trim().length))
            },color=p.onSurface,fontSize=17.sp,fontWeight=FontWeight.Medium)
            if(parts.size>1)Text(parts.dropLast(1).joinToString(" · "),color=p.onSurfaceVariant,fontSize=14.sp)}
        if(selected.id==city.id)Icon(Icons.Rounded.Check,"目前地點",tint=p.primary) else Icon(Icons.Rounded.ChevronRight,null,tint=p.onSurfaceVariant)
    }
}
