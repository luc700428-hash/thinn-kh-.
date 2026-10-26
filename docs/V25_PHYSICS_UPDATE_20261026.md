# thinn-khì V25｜液態介面、天氣物理、地圖與 OTA 更新

本次直接修改 Kotlin / Jetpack Compose 原生 Android 工程，基於 GitHub main `ad9e4d38167ffdb7f808d0c36b0fbc7bdaf1f1e9`。延續前一包的設定圖示、軟體更新卡片、新增鬧鐘滾輪及全螢幕滑動停止介面，再整合這次的需求。

## 本次修改

| 需求 | 這次實作 | 主要程式 |
| --- | --- | --- |
| Dock 拖動要有水黏連 | 手指主體直接跟隨；較慢的彈簧尾部形成液態橋，拉伸時細頸縮小、斷開，放手再接合吸附。外框及四個分頁位置固定。 | `ui/AppleLiquidDockPaint.kt` |
| 全站滑桿與圖二一致 | 細軌道、藍色進度、54×34 dp 透明膠囊、內部折射色帶、陰影、拖曳微拉伸。統一所有頁面，包括穿搭設定、地震回放與颱風時間軸；保留數值範圍、逐格選取及輔助使用語意。 | `ui/GlassControls.kt` |
| 新版本的完整更新內容 | 顯示目標版本的實際發行說明與檔案大小；優先取該版本的增量區塊，沒有區塊就顯示該版本原文。不再截取八項或 16,000 字元。超過 128 KiB 的整份 OTA 資訊會明確拒絕產生，避免默默截斷。 | `update/AppUpdateDelta.kt`、`tools/create_ota_manifest.py` |
| 移除自然語言／自然語音 | 移除自然語音設定、路由及雲端播放／請求程式；更新後清除舊同意設定及快取。鬧鐘鈴聲、獨立的 Android 鬧鐘播報與地震警示音生命週期保留。 | `voice/NaturalVoiceHub.kt`、`MainActivity.kt` |
| 天氣卡片有雨雪物理 | 雨滴附著、增重、合併、重力加速、滑落、尾跡與撞擊飛濺。雪在卡片頂緣形成逐漸堆高的雪丘，天氣轉換後融化。由真實天氣狀態決定；每張卡片使用不同粒子種子。 | `ui/WeatherSurfacePhysics.kt`、`ui/TkPrecipitationCardLens.kt` |
| 颱風地圖不見、放大不清楚 | 內建國土測繪中心縣市界線向量，三個精細度，共 7,113 / 20,233 / 84,579 個座標點，圖資約 0.86 MiB。兩個颱風畫面共用，離線可見、雙指縮放 1–32 倍、平移及臺灣／完整路徑切換。離島標籤以螢幕碰撞避讓，避免重疊。 | `maps/TaiwanVectorAtlas.kt`、`typhoon/TyphoonViewport.kt` |
| 地震主頁及速報視窗重畫 | 地圖優先、圖層分段選取、三張觀測卡、監測開關、近期報告、歷史回放、演練與折疊接收紀錄。速報重畫所在地震度、震央、規模、深度、向量圖與縣市震度。未知值保留「—」，演練持續標示虛構／非即時。 | `earthquake/SeismicLiveMapPage.kt`、`earthquake/SeismicWarningOverlay.kt` |
| 工具與設定太混亂 | 工具依日常生活、天氣防災、時間換算分類，新增搜尋、碼錶、世界時鐘及離線氣象單位換算；維護功能集中在設定。設定的字體、聲音觸覺、資料更新、通知音效各有獨立入口。 | `ui/GlassNavigation.kt`、`ui/EverydayToolsSheet.kt` |
| 編譯成功但 OTA 發送失敗 | 一般簽章更新與「V25 全部功能里程碑」分開。一般發布仍驗證簽章、套件名稱、遞增版本、來源指紋、APK SHA-256 與目前 main；只有明確勾選完整里程碑時才要求全部 22 項驗收。 | `.github/workflows/build-apk.yml` |

## 工具與設定分類

工具包含天氣鬧鐘、我的事項、天氣定時通知、颱風動向、地震監測、中央氣象署降雨雷達、碼錶、世界時鐘、氣象單位換算。雷達入口開啟中央氣象署 QPE；單位換算與時鐘可離線使用。碼錶依單調時間計算，切到背景的時間仍計入，提供開始、暫停、計圈與重置。

設定分為外觀與操作、提醒與偏好、系統與資料。字體、聲音觸覺、資料更新頻率與通知音效分頁呈現；外觀頁保留既有淺深色預覽、跟隨系統、透明度、霧化、高光、速度與彈性設定。

## 視覺與實作邊界

依使用者的照片與影片對照幾何、層級、膠囊、光學滑塊和液態拖曳。共用元件沿用 135° 高光、0.96 按壓、回彈、減少動態效果與降低透明度設定。控制元件與導覽承載明顯的玻璃光效，內容卡片維持可讀的中性底色。

這是 Android 自繪介面；Apple 的系統材質及專有渲染器沒有移植進 APK。不能把可編譯或程式測試，寫成已達成「100% iOS 原生光效」或不同手機上的逐幀相同。視覺、觸覺、鎖屏鬧鐘及覆蓋安裝仍需實機確認。

為避免背景取樣形成渲染循環，共用背景層會標記錄製期間；後代元件不會把正在錄製的同一層再次取樣。滑桿使用自己的透明光學色帶，Dock 在內容層之外取樣。天氣只有一個共用時鐘，離開天氣分頁或 App 進入背景時暫停，卡片粒子數量有上限。

地圖是實際行政界線向量，並保留官方路徑與資料座標；不會為了填滿地圖編造颱風或地震資料。圖資來源版本為 `COUNTY_MOI_1090820`，不是將舊圖片無限放大。地震 P／S 波圈沿用時間驅動的教育示意，不作到達倒數。

## OTA 失敗的診斷與處理

已核對的 GitHub Actions #286（`40200048098`，來源 `fac2d934…`）在 `Require recorded full acceptance before official publication` 失敗；先前的編譯、簽章比對與候選檔案階段已通過。main `ad9e4d3…` 的 #287 候選建置成功。

舊流程把每次維護 OTA 都綁到全部 22 項實機驗收，造成「APK 編得出來，但不能發布」。現在增加 `full_milestone`，預設 false。一般 `publish_release=true` 不宣稱全部功能完成；`full_milestone=true` 仍會執行原本的嚴格驗收閘門，`verified_full_release` 沒有被改成 true。

`UPLOAD.sh` 預設只建置候選 APK；`UPLOAD.sh --publish` 會推送本包修改並啟動一般簽章 OTA 發布。簽章仍使用 GitHub 既有四個 Secrets，包內沒有金鑰。若簽章不符、版本未遞增、main 已前進、標籤已存在或檔案驗證失敗，流程仍停止。

本次尚未在使用者的 GitHub 執行新包發布，也未以原正式簽章在實機完成升級。工程包中的編譯證據為本機 Debug 與 unsigned Release。

## 對照來源

- Apple HIG Materials：<https://developer.apple.com/design/human-interface-guidelines/materials>。作為玻璃導覽層、可讀性及輔助使用原則的參考；精確尺寸採使用者提供的參考圖。
- Apple Liquid Glass：<https://developer.apple.com/documentation/technologyoverviews/liquid-glass>。
- Android RenderEffect：<https://developer.android.com/reference/android/graphics/RenderEffect>。Android 原生背景特效能力。
- 國土測繪中心縣市界線資料目錄：<https://data.gov.tw/dataset/7442>。
- 官方圖資下載：<https://maps.nlsc.gov.tw/pro/download.jsp>。縣市界線為政府資料開放授權條款第 1 版；來源與 SHA-256 保存在 `assets/maps/taiwan_counties.json`。

完整測試結果、可重現的上傳方式與套用驗證，請看更新包根目錄 `VERIFY_REPORT.md` 及 `README_更新包.md`。
