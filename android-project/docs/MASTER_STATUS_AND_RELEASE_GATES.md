# thinn-khì｜從本聊天室起點到目前的完整需求與可驗收總表

> 重要：此文件是**全部需求整合及現有工程審核**，**不是**已完成／已上線證明。凡稱「候選原始碼」代表 ZIP 裡有程式，**不代表** GitHub 正式版、APK 編譯與實機已驗收。

## 一、原始要求總範圍（不能拆成只有首頁或微縮城市）

完整保留：Android/Termux OTA → 全部 iOS-inspired 液態玻璃 UI 與頁面 → 字體資訊層級 → 天氣/官方預警/雨雷音效 → 地震監測/緊急警報/回顧播放 → 雨量雷達和衛星雲圖 → 智慧穿搭/免費 AI → 工具與待辦 CRUD/時間滾輪/提醒 → 系統設定/更新中心/公開 OTA → Widget/通知 → 全臺 22 縣市、各外島小島及國際地標的獨立實景 3D、定位選擇、地標名稱、轉場與快取 → CI 簽章/實機驗收。

## 二、本次對話的新增與修正要求及程式證據（逐項）

| ID | 範圍 | 使用者實際驗收要求 | 現況（不是發布狀態） | 程式證據／缺口 |
|---|---|---|---|---|
| A01 | 原始專案 | 保持原有 Android package io.atmos.weather、既有資料與 OTA 原簽章，不能只生成網頁或另造無法覆蓋安裝的 App | 候選原始碼 | app/build.gradle.kts；完整 APK 與原簽章未驗證 |
| A02 | 原始專案 | 從本聊天室第一個 App 指令起，所有累積需求全部保留，不以最新一張截圖或單一 ZIP 取代完整規格 | 本次整合規格 | 此文件 + 舊執行摘要118項 + 新微縮城市研究摘要 |
| A03 | 建置與發布 | GitHub Actions 必須真正建置 main 分支 Android Kotlin 來源，不再編譯固定 V22 ZIP | 候選工作流程 | native/.github/workflows/build-apk.yml；遠端尚未更新 |
| A04 | 建置與發布 | GitHub CI 要完整執行 Android unit tests、assembleRelease、APK 簽章與雜湊驗證，失敗不可發布 | 候選工作流程 | 工作流程已寫入但未在 GitHub 完成 CI |
| A05 | 建置與發布 | 公開 OTA 版本 JSON；朋友不必登入 GitHub；避開 API 403／限流 | 原始碼候選 | update/AppUpdater.kt, tools/create_ota_manifest.py；正式 OTA 未驗 |
| A06 | 建置與發布 | 更新中心顯示版本、變更內容、下載進度、取消、校驗和、離線與安裝失敗說明 | 原始碼候選 | update/AppUpdatePage.kt；下載/恢復/安裝未真機驗證 |
| A07 | 建置與發布 | Termux 操作簡化、提供 gh 整合發送方式，不以手動改 ZIP 版本當成更新功能 | 候選部署腳本 | native/deploy-to-github.sh；使用者尚未執行 |
| A08 | 建置與發布 | 發布前確認既有裝置可原簽章覆蓋安裝、版本碼遞增、更新內容真正生效 | 未驗收 | 需 CI signed APK + 裝置安裝/升級測試 |
| B01 | 設計系統 | 從啟動／登入（若有）到所有二級頁都統一 iOS-inspired Liquid Glass 設計語言 | 部分原始碼 | ui/ThinnDesignTokens.kt + ThinnComponents.kt；逐頁未驗 |
| B02 | 設計系統 | 繁體中文排版、標題資訊層級、重要數值大字級，避免 10–13sp 文字密集 | 部分原始碼 | ui/ThinnDesignTokens.kt；尚須 1.3x/1.5x 字型實測 |
| B03 | 設計系統 | 白/深色、動態晴雨天氣色、液態玻璃背景、字色對比、圓角、陰影保持一致 | 部分原始碼 | ui/GlassControls.kt 與主頁；全螢幕對比檢查未驗 |
| B04 | 設計系統 | 首頁重要天氣資訊必須不展開就能看到；只隱藏技術診斷與演算法細節 | 候選原始碼 | presentation/home/WeatherHomeScreen.kt；未在手機真機確認 |
| B05 | 設計系統 | 所有一般按鈕、切換、滑桿、列表列、表單、底部抽屜、提醒彈窗統一元件 | 部分原始碼 | ui/GlassControls.kt / GlassSheets.kt / ThinnComponents.kt |
| B06 | 設計系統 | 分頁與畫面轉場、按壓回饋、不得加入無關特效；動畫須可關閉 | 部分原始碼 | ui/GlassNavigation.kt；部分頁面尚未核對 |
| B07 | 設計系統 | 觸覺震動可設定強度、可關閉；按鈕／拖曳／更新／警報應各自恰當 | 部分原始碼 | haptics/HapticEngine.kt；實機馬達/系統限制待驗 |
| B08 | 設計系統 | 使用 Android Material3 原生無障礙語意、TalkBack、足夠熱區、焦點、螢幕縮放 | 部分原始碼 | ui/GlassControls.kt；無障礙完整驗收未完成 |
| B09 | 設計系統 | 所有頁面在小螢幕、長文字、字級放大、橫豎屏不可重疊、裁切或遮擋 | 未驗收 | 需 Android screenshot tests/真機截圖 |
| B10 | 設計系統 | 首次開啟、定位/通知/警報權限導引簡潔一致；若無真正帳號，不憑空增加登入 | 原始碼候選 | permission/PermissionGuide.kt + MainActivity.kt |
| C01 | 天氣首頁 | 位置、當前溫度、體感、最高/最低溫、天氣概況直接展示 | 候選原始碼 | presentation/home/WeatherHomeScreen.kt |
| C02 | 天氣首頁 | 未來6小時降雨機率、氣溫趨勢、風速/風向、濕度、空氣品質直觀可見 | 部分原始碼 | WeatherHomeScreen.kt；需確認依真實資料與實機資訊密度 |
| C03 | 天氣首頁 | 24 小時預報、天氣曲線、即時預報與預警保持高優先且可操作 | 部分原始碼 | WeatherHomeScreen.kt + ui/RadarThreeHourCard.kt |
| C04 | 天氣首頁 | 氣象局/CWA 官方警報不因縣市名稱格式而消失，有警報時顯著提示 | 候選原始碼 | WeatherHomeScreen.kt；來源/推播/全部縣市驗收待完成 |
| C05 | 天氣首頁 | 穿搭／帶傘／防曬／運動的關鍵提醒直接展示；無需點詳情才能看到 | 候選原始碼 | WeatherHomeScreen.kt + lifestyle/LifestyleAdvisor.kt |
| C06 | 天氣首頁 | 空氣品質與 PM2.5 / AQI 來源必須標明是模型/官方，不可冒用臺灣官方 AQI | 部分原始碼 | WeatherHomeScreen.kt；語意與資料源真實性待驗 |
| C07 | 天氣首頁 | 天氣動態景色、晴雨晨昏光影、資料載入錯誤／離線／過期資料明確呈現 | 部分原始碼 | presentation/home/WeatherSurface.kt; WeatherHomeScreen.kt |
| C08 | 天氣首頁 | 聲音/雨雷音效、演示與正式天氣資料嚴格區分 | 部分原始碼 | audio/WeatherAudio.kt + demo/；音訊授權與真機待驗 |
| D01 | 地震 | 監控主頁顯示最新地震、測站、震度、時效/資料來源與完整警報狀態 | 部分原始碼 | earthquake/SeismicLiveMapPage.kt；資料時效/官方來源實測待驗 |
| D02 | 地震 | 強震預警即時高優先級，震動、聲響與語音、Android 通知權限相容 | 部分原始碼 | push/; notifications/; earthquake/；端到端官方告警未驗 |
| D03 | 地震 | 回顧播放與事件時間軸、過期站點不消失而標註「非即時」 | 候選原始碼 | earthquake/SeismicLiveMapPage.kt；實機待驗 |
| D04 | 地震 | 真實地震警報與模擬測試明顯不同，不能假造官方緊急警報 | 安全要求待全測 | 地震提醒完整裝置測試不可略過 |
| E01 | 雷達 | 真實降雨雷達、時間軸、來源/更新時間顯示，不得偽稱數值預報為實測雷達 | 部分原始碼 | ui/RadarHistoryDialog.kt + assets/radar/history.html；實時服務未測 |
| E02 | 雷達 | 合法可用的衛星雲圖、雷達/衛星切換與地圖交互 | 未完成 | 未取得可使用之衛星來源授權 |
| E03 | 雷達 | 官方特報、雨量、感測資料需保持出處與實測/預測差異 | 部分原始碼 | alerts/ + radar/；實際服務驗收待完成 |
| F01 | 智慧生活 | 免費/開源 AI 穿搭建議與基本無AI替代邏輯 | 部分原始碼 | lifestyle/ + 外部 Ollama 服務未驗 |
| F02 | 智慧生活 | 外套、雨具、鞋子、情境、空氣品質與溫差建議應具體、不冗長 | 部分原始碼 | lifestyle/LifestyleAdvisor.kt + WeatherHomeScreen.kt |
| F03 | 智慧生活 | AI API/HTTPS、隱私、金鑰、本地偏好、關閉選項與錯誤回退 | 部分原始碼 | lifestyle/；外部服務與資料交換待驗 |
| G01 | 事項工具 | 我的事項清單：新增、編輯、完成、刪除及持久保存 | 候選原始碼 | task/TaskCenterPage.kt + TaskStore.kt；未通過 Android 實機 |
| G02 | 事項工具 | 新增事項含日期與時間、清楚時間選擇器、提醒開關 | 候選原始碼 | task/ThinnTask.kt + ui/GlassClockDial.kt |
| G03 | 事項工具 | 提醒排程、通知點回事項、開機/重啟/權限拒絕時正確處理 | 部分原始碼 | task/TaskReminderScheduler.kt；真機定時通知未驗 |
| G04 | 事項工具 | 工具中心資訊架構、重要項目直接看見、不要大量不相關字 | 部分原始碼 | ui/ThinnComponents.kt、TaskCenterPage.kt |
| G05 | 設定 | 設定頁分組清楚：外觀、字級、動效、定位、通知、地震、生活建議、更新 | 部分原始碼 | ui/GlassNavigation.kt / WeatherHomeScreen.kt；需要逐頁控制值有效性測試 |
| G06 | 設定 | 選項/滑桿/日期滾輪改動真正保存並生效，不是只有外觀 | 部分原始碼 | 多數 prefs/task 資料流；需逐項端到端驗收 |
| G07 | Widget | Android 桌面小工具與全 App 風格一致、資訊正確且更新可靠 | 待重設/驗收 | widgets/AtmosWidgets.kt；25 款尚未逐款統一 |
| H01 | 微縮城市 | 完整 22 縣市、多個景點，不只一縣市一個泛用小屋 | 資料庫候選 | assets/landmarks/catalog_v2.json：132筆候選資料 |
| H02 | 微縮城市 | 小琉球、蘭嶼、龜山島、綠島、澎湖列島、金門、馬祖及知名小島不可缺 | 資料庫候選 | catalog_v2.json：20個島嶼地域；非全小島名錄/建模 |
| H03 | 微縮城市 | 依使用者 GPS 縣市與實際島嶼優先選在地模型，手動選城及拒絕定位回退 | 部分原始碼 | domain/visual/LandmarkGeo.kt, LandmarkRegistry.kt；無精確島界GIS |
| H04 | 微縮城市 | 一個縣市多個獨立景點；每次開啟 App 同地區輪替，可手動下一個 | 候選原始碼 | WeatherHomeScreen.kt openingLandmarkIndex、landmarkStep |
| H05 | 微縮城市 | 地標標籤為「縣市、地標名稱」，字體大且清楚，如「高雄、夢時代」 | 候選原始碼 | LandmarkRegistry.kt caption、WeatherHomeScreen.kt 24sp |
| H06 | 微縮城市 | 建模涵蓋 101、淡江大橋、北回歸線、夢時代、85大樓、新臺南車站、新高雄車站、高流、宮原眼科等 | 部分原始碼 | catalog_v2.json + 17 GLB 幾何原型；細節/興建狀態待查 |
| H07 | 微縮城市 | 各地標必須可辨識、有專屬建築特徵，是真正的高品質獨立 3D 建模 | 尚未完成 | 目前17個原創幾何GLB原型，115候選點尚未建模 |
| H08 | 微縮城市 | 首頁需要真正 GLB 即時渲染、PBR 材質、自由旋轉/鏡頭、無縫3D飛越轉場 | 尚未完成 | 首頁 LandmarkCanvas 使用 PNG 預覽，非 Filament 即時3D |
| H09 | 微縮城市 | 臺灣及國際景點可擴增，依所在地、天氣、夜景、日照動態光效 | 未完成/部分資料 | 國際候選資料有；天氣與3D引擎整合未完成 |
| H10 | 微縮城市 | 每個模型要有真實合法來源/授權、建成狀態、攝影參考及幾何驗收 | 未完成 | catalog 授權欄位/原型狀態存在，但未完成全部來源核實 |
| H11 | 微縮城市 | GPU/CPU/網路/快取效能、低階手機退化、減少動效與離線替代 | 部分原始碼 | 減動效與PNG fallback；沒有完整3D流量/LOD管線 |
| I01 | 安全隱私 | 位置權限依需求、使用者可關閉、概略定位與離線有合理後備 | 部分原始碼 | permission/ + location/；邊界/隱私完整性需實測 |
| I02 | 安全隱私 | 各資料來源、授權、個資通知、測試功能與正式安全訊息分開 | 需要系統驗收 | 服務憑證/權限與授權未全查 |
| I03 | 完成定義 | 每項需對上單元測試／UI 測試／CI／實機截圖／Release，而非一句已完成 | 本次統一規範 | 此文件；目前沒有正式裝置驗收 |

## 三、現況的硬性事實（由工程檔檢查）

- 候選原始碼：`native/android-project`，包含 Android Kotlin/Compose、地震及天氣 backend、OTA manifest 產生器。
- `catalog_v2.json` 實際包含 **132 筆候選地標資料**及 **20 個島嶼地域**；這不是「所有島嶼都已完成建模」。
- 其中 **17 筆 `original_prototype` GLB 原型**，其餘 **115 筆尚無可播放的專屬原型**。
- `WeatherHomeScreen.kt` 的 `LandmarkCanvas` 實際以 `Image(bitmap=...)` 呈現 **PNG 圖像**，不是 Filament 即時 GLB 渲染。
- `native/.github/workflows/build-apk.yml` 候選版會編譯追蹤的 `android-project/`；GitHub main 的舊流程仍需成功部署替換，否則仍可能使用固定 V22 ZIP。
- 靜態/政策本地測試：Python 32/32、Node 7/7 通過；**無法因此認定完整 Android UI 可編譯或真機所有功能可用**。
- 舊 ZIP 正式發布或 HTML 原型**不構成**實機更新；必須以 GitHub commit、workflow、APK hash、原簽章升級及裝置截圖查驗。

## 四、不可跳過的最終驗收門檻

1. 全數保留所有要求並以 ID 明確追蹤，新增需求不得覆蓋舊需求。
2. GitHub main 有實際更新的 `android-project/`，建置工作流程不再抽取舊 ZIP。
3. GitHub Actions 完整單元測試、Kotlin/Compose compile、assembleRelease 通過，出現可下載的 signed APK。
4. 同一 release 有 `thinn-khi.apk`、`thinn-khi.apk.sha256`、`ota.json`，校驗碼比對一致，原簽章覆蓋安裝成功。
5. 在真實 Android 手機依首次啟動→授權→天氣→地震→工具→新增/編輯/提醒→設定→系統更新→Widget 逐頁操作，保留截圖與問題紀錄。
6. 全 App 所有重要資訊不用額外點擊便能看見；對比度、動態天氣背景、大字體/螢幕閱讀器/降低動效/低階效能測試通過。
7. 微縮城市定位核對實際在地島嶼，臺灣22縣市各有多個**可辨識建模地標**，無假小屋、無以 PNG 冒充 3D 的情況。
8. 對 EEW 真實資料、來源/模擬區分、推播/聲音、衛星/雷達資料授權與服務逐項實測，不得宣稱未完成品已能使用。
9. 正式 OTA 發布後確認 App 顯示的介面與 GitHub commit 完全相符，且朋友可免 GitHub 登入更新。
