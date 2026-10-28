package io.atmos.weather.alerts

import android.annotation.SuppressLint
import android.os.Build
import android.view.ContextThemeWrapper
import android.webkit.*
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun CwaAlertsPage(initialTitle:String?=null,initialUrl:String?=null,
    initialHazards:List<CwaCountyHazard> = emptyList(),initialNotices:List<CwaWarning> = emptyList(),onClose:()->Unit) {
    var result by remember{mutableStateOf<CwaBulletinsResult?>(null)}
    var notices by remember{mutableStateOf(initialNotices)}
    var refresh by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(true)}
    var national by remember{mutableStateOf<CwaNationalWarnings?>(null)}
    var clock by remember{mutableStateOf(Instant.now())}
    var checked by remember{mutableStateOf(Instant.now())}
    val lifecycle=LocalLifecycleOwner.current
    var selected by remember{mutableStateOf(initialTitle)}
    var officialPage by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(refresh) {
        loading=true
        try {coroutineScope {
            val rss=async{CwaWarningFeed.fetchNational()}
            val counties=async{CwaNationalWarningFeed.fetch(refresh>0)}
            result=CwaBulletins.fetch(refresh>0)
            national=counties.await()
            val recent=rss.await();if(recent.checkSucceeded)notices=recent.warnings
        }}finally{loading=false;checked=Instant.now();clock=checked}
    }
    LaunchedEffect(lifecycle){lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while(isActive){clock=Instant.now();if(clock.epochSecond-checked.epochSecond>=300)refresh++;delay(30000)}
    }}
    BackHandler(officialPage!=null||selected!=null){if(officialPage!=null)officialPage=null else selected=null}
    val root:@Composable ()->Unit={
        CwaAlertsContent(result,notices,initialHazards,loading,selected,
            onSelect={selected=it},onRetry={refresh++},onBack={if(selected!=null)selected=null else onClose()},
            onOfficial={title->officialPage=initialUrl?.takeIf{selected==initialTitle && CwaOfficialPages.allowed(it)}
                ?:CwaOfficialPages.forTitle(title,notices)},now=clock,national=national)
    }
    var retained by remember{mutableStateOf(CwaOfficialPages.overview)}
    officialPage?.let{retained=it}
    AppleParallaxStack(officialPage!=null,{officialPage=null},Modifier.fillMaxSize(),root) {
        OfficialCwaPage(retained){officialPage=null}
    }
}

@Composable internal fun CwaAlertsContent(result:CwaBulletinsResult?,notices:List<CwaWarning>,
    hazards:List<CwaCountyHazard>,loading:Boolean,selected:String?,onSelect:(String)->Unit,
    onRetry:()->Unit,onBack:()->Unit,onOfficial:(String)->Unit,now:Instant=Instant.now(),national:CwaNationalWarnings?=null) {
    val p=MaterialTheme.colorScheme
    val date=DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei"))
    val bulletin=result?.bulletins?.firstOrNull{selected==null || selected.contains(it.title)||it.title.contains(selected)}
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader(if(selected==null)"中央氣象署警特報" else "警特報詳情",onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)
            .padding(top=12.dp,bottom=132.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(loading)Text("正在更新中央氣象署公告…",color=p.onSurfaceVariant,fontSize=14.sp)
            if(result?.failed==true) GlassSurface {
                Text(if(result.cached)"更新未完成，顯示先前取得的公告；請核對有效時間。" else "官方公告尚未載入，請重試。未載入不代表沒有警報。",color=p.onSurface)
                GlassTextButton(onRetry,enabled=!loading){Text("重新取得公告")}
            }
            CwaWarningMapCard(CwaWarningMapState.build(national,result?.bulletins.orEmpty(),hazards,selected,now),selected)
            if(selected==null) {
                result?.checkedAt?.let{Text("最後查詢 ${date.format(it)} · 臺灣時間",color=p.onSurfaceVariant,fontSize=14.sp)}
                result?.bulletins.orEmpty().forEach {b->
                    GlassSurface {
                        Text(if(b.active(now))"有效公告" else "公告資訊 · 請核對有效時間",color=p.primary,fontSize=14.sp)
                        GlassAction(b.title,b.areas.joinToString("、")){onSelect(b.title)}
                        Text("${date.format(b.issuedAt)} 發布",color=p.onSurfaceVariant,fontSize=14.sp)
                    }
                }
                val missing=hazards.filter{h->result?.bulletins.orEmpty().none{it.title.contains(h.phenomenon)}}
                missing.distinctBy{it.title}.forEach{h->GlassSurface{GlassAction(h.title,h.county){onSelect(h.title)}}}
                if(!loading && result?.failed==false && result.bulletins.isEmpty())
                    Text("本次特報內容資料未列出公告；其他種類警報可在下方官方圖表中查看。",color=p.onSurfaceVariant)
                if(notices.isNotEmpty()) {
                    Text("近期官方公告",color=p.onSurface,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                    Text("近期公告可能包含更新或解除訊息，請核對原文。",color=p.onSurfaceVariant,fontSize=14.sp)
                    notices.forEach{n->GlassSurface{GlassAction(n.title,date.format(n.issuedAt)){onSelect(n.title)}}}
                }
                GlassOutlinedButton({onOfficial("")},modifier=Modifier.fillMaxWidth()){Text("在 App 內查看官方警報總覽")}
            } else {
                val title=bulletin?.title?:selected
                GlassSurface {
                    Text(title,modifier=Modifier.fillMaxWidth(),color=p.onSurface,fontSize=24.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
                    val header=hazards.firstOrNull{selected.contains(it.phenomenon)}
                    val issued=bulletin?.issuedAt?:header?.issuedAt
                    val expires=bulletin?.expiresAt?:header?.expiresAt
                    issued?.let{Text("官方發布 ${date.format(it)}",color=p.onSurfaceVariant,fontSize=15.sp)}
                    expires?.let{Text("有效至 ${date.format(it)}"+(if(!now.isBefore(it))" · 已超過有效時間" else ""),color=p.onSurface,fontSize=15.sp)}
                    if(bulletin?.areas?.isNotEmpty()==true) {
                        Spacer(Modifier.height(12.dp));Text("警戒地區",color=p.onSurface,fontWeight=FontWeight.SemiBold)
                        Text(bulletin.areas.joinToString("、"),color=p.onSurface,fontSize=16.sp,lineHeight=25.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    if(bulletin!=null) SelectionContainer {
                        Text(bulletin.text,color=p.onSurface,fontSize=17.sp,lineHeight=28.sp)
                    } else Text("此公告完整內容與圖表請在下方官方頁面查看。",color=p.onSurfaceVariant,fontSize=16.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("資料來源：中央氣象署 · 官方內容原文",color=p.onSurfaceVariant,fontSize=14.sp)
                }
                GlassButton({onOfficial(title)},modifier=Modifier.fillMaxWidth()){Text("在 App 內查看官方圖表與完整頁面")}
            }
            if(!loading)GlassTextButton(onRetry){Text("更新警特報")}
        }
    }
}

/** The official site stays inside the app. No Intent/UriHandler, JS bridge or file access. */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable internal fun OfficialCwaPage(url:String,onClose:()->Unit) {
    val p=MaterialTheme.colorScheme
    val dark=p.background.red<.5f
    val textZoom=(androidx.compose.ui.platform.LocalDensity.current.fontScale*100).toInt().coerceIn(100,180)
    var page by remember{mutableStateOf<WebView?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var progress by remember{mutableIntStateOf(0)}
    var rendererLost by remember{mutableStateOf(false)}
    var browserGeneration by remember{mutableIntStateOf(0)}
    BackHandler {if(page?.canGoBack()==true)page?.goBack() else onClose()}
    DisposableEffect(Unit){onDispose{page?.stopLoading();page?.destroy();page=null}}
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding().padding(bottom=96.dp)) {
        IOSSettingsHeader("中央氣象署官方資訊",onClose)
        if(progress<100)IOSLinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        error?.let{message->Column(Modifier.padding(16.dp)) {
            Text(message,color=p.onSurface)
            GlassTextButton({error=null;if(rendererLost){rendererLost=false;browserGeneration++}else page?.reload()}){Text("重新載入官方頁面")}
        }}
        key(dark,browserGeneration) {
        AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={context->
            WebView(ContextThemeWrapper(context,if(dark)android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)).apply {
                page=this
                settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                settings.allowFileAccess=false;settings.allowContentAccess=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportZoom(true);settings.builtInZoomControls=true;settings.displayZoomControls=false
                settings.setSupportMultipleWindows(false);settings.javaScriptCanOpenWindowsAutomatically=false
                settings.safeBrowsingEnabled=true
                webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                        if(!CwaOfficialPages.allowed(request.url.toString())) {
                            if(request.isForMainFrame)error="此連結不是氣象署官方頁面，已留在 App 內。"
                            return true
                        };return false
                    }
                    override fun onReceivedError(view:WebView,request:WebResourceRequest,e:WebResourceError) {
                        if(request.isForMainFrame)error="官方頁面連線未完成，請重試。"
                    }
                    override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                        if(request.isForMainFrame)error="官方頁面暫時無法提供，原公告與已載入資訊仍保留。"
                    }
                    override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {
                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                        view.destroy();if(page===view)page=null
                        rendererLost=true;error="官方圖表顯示程序已停止，可重新載入；公告文字仍保留。"
                        return true
                    }
                }
                webChromeClient=object:WebChromeClient(){override fun onProgressChanged(v:WebView,value:Int){progress=value}}
                if(CwaOfficialPages.allowed(url))loadUrl(url)
            }
        },onRelease={view->
            if(page===view)page=null
            runCatching{view.stopLoading();view.destroy()}
        },update={view->
            if(!rendererLost) {
                view.setBackgroundColor(p.background.toArgb())
                view.settings.textZoom=textZoom
                if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings,dark)
            }
        })
        }
    }
}
