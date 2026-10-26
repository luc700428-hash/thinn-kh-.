# V25 更新狀態、雨滴碰撞、120 Hz 與玻璃光學修正

基於已發布 `25.0.288` 的 main：`3cea4550ed5702a0a2394cd223d72b89eb4d1291`。已核對 GitHub Actions #288 成功，Latest Release 是 `v25.0.288`。本包不更改資料來源、正式簽章或版本遞增規則。

## 截圖問題的原因與修正

| 問題 | 原因 | 修正 |
| --- | --- | --- |
| 更新後仍顯示同版待安裝 | 持久化下載紀錄還原時只驗證網址、雜湊，沒有重新與已安裝版本比對；非空的 pending 又阻止重新檢查。 | 還原、回到前景、下載服務重送時都核對已安裝版本。相同或更舊版本會清除其進度、ready、舊錯誤及舊 APK／續傳檔，保留真正更新的下載。最新檢查與可更新版本分開，成功確認無新版才顯示「已是最新版本」。簽章與 APK 版號安全檢查保留。 |
| 雨動畫奇怪、沒有打到卡片 | 原有三套空中雨場加卡片雨滴層同時疊加，彼此沒有共同碰撞；局部水花是獨立產生，並非實際雨點擊中卡片。 | 停用重疊雨場。以卡片實際排版座標註冊表面，空中雨滴掃掠碰撞屋緣；碰撞位置才產生反彈水花。回落水滴、邊緣積水、圓角流動、底端水頸與重力滴落共用同一個世界。捲動時同步卡片位置，水滴不穿過卡片正面。 |
| 120 Hz 動畫 | 先前只有主 Activity 的最高刷新率提示；部分視覺使用 33 ms／120 ms 計時器。 | 同解析度優先選擇 120 Hz，其他裝置合理回退。所有 Activity 及共用 Compose Dialog 設定刷新率請求，API 35 以上對 Compose View 投票。雨滴、沙塵、地震波圈、碼錶使用螢幕 frame clock；物理固定 240 Hz 步進，因此 60／120 Hz 不改變雨滴速度。 |
| 淺色模式資訊消失 | 雷達卡片切換為淺底，內文仍寫死白色。 | 標題與數值使用主題前景，次要資料和警示依深淺色配對；加入文字對比檢查。雷達圖片上的深色觀測標籤仍保留白字。 |
| 黏連和光效不夠 | 原先尾部跟隨較快，拉伸不明顯；背景只有模糊／飽和度與漸層邊框。 | 加入黏著錨點及較慢的尾部彈簧，拉長連接頸後才斷開。Android 13 以上使用 RuntimeShader 實際取樣背景，繪製局部放大、邊緣折射、細微色散與反光，焦點連續跟隨拖曳。天氣背景獨立捕捉，避免元件取樣自身形成循環。外觀的透明感、霧化及高光設定實際連動材質與 shader。 |
| 颱風動向不清楚 | 不同颱風資料同時畫在一起，預報點缺少方向及時間，時間軸以浮窗呈現。 | 一次聚焦一個颱風，預設看完整路徑，預報用虛線、箭頭和避讓後的時間標記；可切回臺灣、放大與平移。時間軸改用原生頁面返回轉場，不開颱風 Dialog。 |

## 天氣與物理的範圍

保留晴、毛毛雨、小雨、中雨、大雨、雷雨、大雷雨、霾、霧、大霧、沙塵暴、小雪、中雪、大雪的分類與環境音。雨勢分成六個強度；風的水平偏移使用實際風向／風速。雪保留卡片頂緣逐漸積累及轉天氣後的融化。明確標示的視覺演示不會更動天氣數據或發送虛構警報。

碰撞世界以 dp、秒和質量計算，使用固定 1/240 秒步進。水花只由碰撞產生，水量分配到表面水滴與反彈粒子；水滴沿真實圓角移動，底端液滴先形成連接水頸，再分離落下。使用粒子上限、離屏表面清理與生命週期暫停，沒有每張卡片各自跑的雨滴計時器。

## 120 Hz 與光學的驗證界線

120 Hz 是 App 提交的目標與刷新率請求，無法強制手機在省電、溫控或螢幕不支援時維持 120 FPS。設定健康檢查顯示裝置目前回報的螢幕更新率，沒有把它冒稱成實際繪製 FPS。桌面小工具、通知島和系統安裝視窗的刷新策略由 Android／手機系統管理。

光學為 Android 自製實作，沒有移植 Apple 的專有引擎。API 33 以上提供 shader 取樣折射；API 31–32 使用毛玻璃與高光回退。降低透明度／減少動態效果設定仍有效。圖示、文字不套用位移折射，保留可讀性。

完整 Android 建置、數值測試、shader 編譯及原始碼一致性紀錄隨更新包交付。這些不能代替手機上的 GPU、120 FPS、觸覺或長時間操作驗收。本次尚未代替使用者在 GitHub 正式發布。

## 主要程式

- `update/OtaVersionPolicy.kt`、`OtaRetainedSession.kt`、`AppUpdatePage.kt`：已安裝版本與下載狀態協調。
- `ui/RainCollisionWorld.kt`、`RainCollisionScene.kt`：真實排版碰撞、反彈、邊緣流動與滴落。
- `ui/HighRefreshWindow.kt`、`RefreshRatePolicy.kt`、`DisplayFrameClock.kt`：120 Hz 目標與各視窗 frame clock。
- `ui/LiquidOpticsEngine.kt`、`ThinnBackdrop.kt`、`AppleLiquidDockPaint.kt`：背景折射與黏著。
- `ui/RadarThreeHourCard.kt`：淺色資訊對比。
- `typhoon/DirectedTrackPaint.kt`、`TrackArrowGeometry.kt`：方向箭頭與時間。

## 官方技術參考

- Android 自動調整刷新率：<https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate>。
- Android Window API：<https://developer.android.com/reference/android/view/Window>。
- Compose frame clock：<https://developer.android.com/reference/kotlin/androidx/compose/runtime/MonotonicFrameClock>。
- Android AGSL：<https://developer.android.com/develop/ui/views/graphics/agsl>。
