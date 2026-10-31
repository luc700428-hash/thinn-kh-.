# 平板、折疊裝置與小工具更新

工作區使用常駐液態 Dock、可調整的列表／詳情與浮動表單。平板與展開的折疊裝置預設在右側顯示 Dock，四個主頁直接點選或拖動切換，不經過選單。

## 裝置與視窗

- AndroidX WindowManager 1.5.1 讀取目前視窗的 FoldingFeature。鉸鏈方向、遮擋範圍與半開姿態參與排版。
- 最大視窗尺寸、鉸鏈感測器特徵與已觀察的折疊資訊判斷裝置種類；目前可用寬高與字體大小決定這個視窗能放多少欄。平板分割到窄視窗時也可回到單欄。
- 平板使用常駐 Dock，可在設定選擇右側、底部、左側或上方。內容預留對應欄位；狹窄或過矮的視窗暫用底部，放大後恢復偏好。窄外螢幕維持手機底部 Dock。
- 書本姿態左側保留天氣概覽，右側操作內容與 Dock。桌面姿態上半部顯示地點及天氣概覽，下半部操作內容與 Dock。文字與主要操作避開遮擋區。
- 搜尋以置中的浮動面板呈現，依鍵盤與視窗調整。編輯表單與響鈴介面也避開遮擋鉸鏈。

## 導覽與狀態

內容根節點與雙欄內容使用固定的 composition 位置；尺寸切換只調整約束、位置及邊距，不搬移整個執行中的 pager／動畫樹。天氣雙欄使用固定子節點的 Layout，保留文字、滑桿與捲動狀態。

工具及設定在足夠寬時保留列表並於旁邊開啟詳情。開啟下一個功能前清理上一個外層詳情，防止舊頁面覆蓋新的目的地。事項、更新、地震設定、天氣通知及雷達在平板工作區中呈現；時間編輯、確認視窗與緊急速報保持獨立 modal 語意。

設定與工具列表顯示選取底色及 selected 語意。Dock 顯示目前分頁；四邊共用相同液態選取、光效、按壓與回彈。支援 Ctrl／Command + 1–4 切換主頁、Ctrl／Command + F 搜尋。保留 Android 返回及既有預測返回手勢。

## 時鐘＋Q 版天氣

新增「時光與天氣夥伴」，保留原有 28 種 receiver 身分。使用原生 TextClock 更新時間與日期，依系統 12／24 小時制；天氣夥伴沿用可縮放的原生向量及連續眼睛、漂浮與天氣動畫。大小改變時重新計算文字、時鐘與角色空間，不拉伸角色點陣圖。

支援小型、正方形、寬型及長型；較大尺寸顯示高低溫、降雨和更新狀態，並提供更新按鈕。快取與過期資料不冒充即時資訊。系統減少動態、App 減少動態與省電模式仍能停用角色動畫。

## 同步修正

小工具的選定地點與背景災防通知的 GPS 訂閱分開保存。選擇地點會更新 generation；背景載入只可發布同地點、同 generation 的結果，較舊資料不能覆蓋較新快取。主頁取得新資料後僅要求重繪，不再立即啟動一次使用不同定位的網路更新。

Glance 的 Preferences 狀態版本觸發執行中的小工具重新讀取快取。每個已放置實例獨立更新；失敗保留重試。安裝、還原、重開機、更新 App、時區及時間變更重新安排更新與快取重繪。

WorkManager 的週期更新最短 15 分鐘；Android 省電、斷網、強制停止及桌面啟動器仍會影響更新時機。原生時鐘不依賴天氣 Worker 每分鐘執行。這次修正不宣稱突破系統背景限制。

## 驗證範圍

新增原生 Robolectric 測試：完整 WeatherHomeScreen 的 Dock 導覽、工具詳情替換、事項、內外層設定、寬窄尺寸切換；注入真實格式的書本／桌面鉸鏈；編輯表單避讓；小工具多尺寸及高密度原生 RemoteViews；六種城市與更新競態。

完整頁面測試使用固定天氣 repository 與本機 HTTPS 回應，沒有傳送測試位置至外部服務。這是功能與原生排版驗證，並非實體平板、折疊感測器或模擬器的端到端驗收。原有八項 Android 裝置介面測試及嚴格 CI 檢查保留，另編譯測試 APK。

## 設計及 API 依據

- [Apple：Designing for iPadOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-ipados)
- [Android：Make your app fold aware](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware)
- [Android：Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)

查閱日期：2026-10-31。實際通過數量、APK 雜湊及建置結果以更新包中的 validation 紀錄為準。

Dock 位置、動效與音效初始化修正的細節見 [TABLET_DOCK_AND_AUDIO_FIX.md](TABLET_DOCK_AND_AUDIO_FIX.md)。
