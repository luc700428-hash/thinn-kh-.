# 地震設定、速報接收與颱風向量效能修正

基底：`90c16731ff0f1b3492de2de178365ed0b740d8bf`。日期：2026-10-29（臺灣時間）。

## 查證與設計依據

- [Android：時間敏感通知](https://developer.android.com/develop/ui/views/notifications/time-sensitive)：通知權限、高重要性分類；解鎖使用手機時，系統可能以 heads-up 顯示，即使通知帶有 full-screen intent。
- [AOSP：全螢幕通知限制](https://source.android.com/docs/core/permissions/fsi-limits)：Android 14+ 需檢查 `canUseFullScreenIntent()`，提供特殊存取權限入口；授權、鎖定、熄屏、AOD 與解鎖應分開驗證。
- [Android：背景 Activity 啟動](https://developer.android.com/guide/components/activities/secure-bal)：PendingIntent 的建立者與發送者規則不同，前景服務不等於可任意從背景開頁。
- [Android：通知分類](https://developer.android.com/develop/ui/compose/notifications/channels)：已建立分類的聲音與重要性由系統和使用者管理。
- [Apple HIG：Materials](https://developer.apple.com/design/human-interface-guidelines/materials)、[Notifications](https://developer.apple.com/design/human-interface-guidelines/notifications)：資訊保持可讀，玻璃材質用於操作層；緊急資訊以簡短標題和明確動作為主。本實作使用原創 Android Compose 元件，不宣稱是 Apple 原生實作。
- [中央氣象署：強震即時警報](https://scweb.cwa.gov.tw/zh-tw/guidance/eew)：地震已發生後才估計震度與震波到時；近震央有預警盲區，無法保證先於搖晃送達。
- [ExpTech DPIP 公開傳輸說明](https://github.com/ExpTechTW/DPIP/blob/main/api.md)及 [EEW 公開資料模型](https://github.com/ExpTechTW/DPIP/blob/main/lib/features/earthquake/domain/eew.dart)：頂層 `time` 為發布時間、`eq.time` 為發震時間，`author + id` 區分來源事件，`status=1` 為警報、`2/3` 為取消，`final` 不代表取消。僅參考通訊欄位語意，未複製 DPIP 的地震估算演算法、UI 或音效。

## 實際變更

1. 四個設定分頁保留與 Dock 一致的拖曳選擇、液態選取光效。移除外層固定高度，內層依圖示和字體實際高度排版。切換分頁時回到內容頂部。
2. 「一般」集中監測、位置、接收狀態；「警報」集中所在地震度門檻、速報、鎖定全螢幕、震後報告與進階測站檢知；「音效」集中短提示、語音、速報和測站音效。報告通知不再出現重複開關。
3. 所在地震度最低 1、預設 3，支援 5−／5+／6−／6+／7。門檻同時套用 SSE、經驗證 FCM、前景畫面與語音。當其他縣市震度較大時，不拿來代替使用者的所在地震度。來源缺值或位置不可靠時，預設仍提醒，使用者可獨立關閉「所在地震度未知時仍提醒」。已知所在地 0 級不提醒。
4. 所有頁面上的真實速報由主 Activity 的共用 host 處理，不再依賴使用者剛好開著地震地圖。背景通知改指向不匯出的專用 Activity；只接受本機驗證後、未過期的私有 ticket，才設定鎖定顯示及喚醒旗標。一般匯出首頁的任意 extras 不能喚醒螢幕。真實速報會結束正在進行的演練／回放，停止練習音與獨立演練語音，避免與真實警示重疊。
5. 使用 `author + id` 的雜湊鍵去重，保留首個達門檻的報次，避免低震度首報被過濾後，高震度更新也因「關閉更新提醒」而漏掉。演練、過期、取消、重複與倒序資料不能建立新警報；取消不會關閉另一來源或較新報次的警報。磁碟最多保留 8 個事件 ticket。
6. 全螢幕速報採可縮放的臺灣縣市向量震度圖，下方顯示所在地震度、來源抵達時間、震央、M、深度 km、最大震度及報次。明暗模式使用各自的可讀色彩。鄉鎮資料不足時不繪製假精度；無來源抵達時間就顯示「待來源提供」。P/S 圈是示意，不當作真實預估抵達。921 演練仍區分歷史最大震度、本機模型與演練倒數。
7. 震後報告預設改為原創三聲固定 1 kHz 提示，44.1 kHz 單聲道 PCM、總長 1.35 s，有淡入淡出並保留峰值餘裕，可由 `tools/generate_seismic_report_signal.py` 完整重建。無旋律、無天氣鬧鐘音樂。持續速報提示也使用通知音訊用途，通知音量為 0 時不另從媒體音量出聲。通知音使用穩定資源名稱 URI，遷移保留舊分類的封鎖、重要性、自訂聲音和震動偏好。
8. 颱風圖：臺灣數百輪廓合併成每幀兩次向量繪製；區域詳圖在背景工作準備並快取，拖曳只讀取已準備路徑，準備期間保留低細節向量底圖；相機更新合併，不堆積工作。時間標籤與筆刷快取，地圖獨立圖層，維持來源海岸、小島與 32 倍向量細節。沒有以放大點陣圖取代地圖。

## 上游資料範圍

標準公開 EEW 可能只提供震源參數，沒有縣市震度與當地到時。本次不把最大震度或固定波速當成使用者所在地的真實預估。若可信推播閘道已有縣市預測，新增可驗證的 `countyIntensities`、`countyArrivalTimes`、`maximumIntensity`、`agency` 等選填欄位；舊 payload 仍可用。後端原始碼有更新，未部署任何服務或發送警報。

查證時 v2 RTS 快照回傳 404，舊 v1 SSE 測試連線逾時；因此沒有僅依文件切換現有測站端點。這些觀察不表示所有地區的上游服務永久無法使用。

## 驗證與限制

驗證紀錄與原生圖像放在更新包 `validation/`。新增測試涵蓋門檻邊界、來源身份、取消與倒序、去重、程序重建、有效期、非匯出 Activity、喚醒旗標、未允許全螢幕時的通知備援、明暗模式及 100%／150% 字體、四分頁點選與橫向拖曳，以及每 144 次向量畫圖不在 UI 內重建路徑。

Robolectric 原生圖形與通知測試不等於實機鎖定畫面測試。本環境沒有連接使用者的小米手機，未宣稱通過 HyperOS 真機緊急送達、熄屏喚醒、DND 或 144 fps 驗收。GitHub 原有 8 項模擬器回歸與嚴格結果檢查完整保留；候選 APK 仍需通過既有工作流程。本包不是已發布的正式 OTA。
