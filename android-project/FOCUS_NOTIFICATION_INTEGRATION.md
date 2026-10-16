# thinn-khì V10.2 · 小米焦點通知／超級島接入

## 直接接入的項目

- `XiaomiFocusBridge.kt`：判斷 Xiaomi/Redmi/POCO、讀取 HyperOS 焦點通知協定、透過小米官方提供的 `content://miui.statusbar.notification.public` 查詢本 App 的焦點通知授權。
- `CwaAlertWorker.kt`：**只對中央氣象署符合目前所在地的正式 RSS 通知** 附加 `miui.focus.param`，現有 Android 通知內容、群組通知與去重機制保留。
- HyperOS 2：焦點通知所需 ticker、baseInfo；HyperOS 3：含簡版 `param_island` 的超級島資料。使用官方 Notification API，沒有 root、Hook、白名單繞過或假冒系統警報。
- 設定頁：查詢焦點通知狀態、手動送一則明確標示為「測試」的通知。
- 非小米裝置、權限未取得、系統不相容：標準 Android 通知繼續運作。

## 正式上島前需確認

小米官方文件要求 App 端的焦點通知使用權限與場景模板通過核准。加入程式碼不等於系統會立即顯示在超級島，也無法由 App 自動取得權限。

- 官方開發指南：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
- 權限與開發者 Q&A：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2146
- 官方場景接入流程：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132

OPPO 原子島尚無已驗證的通用公開接入途徑，因此本版僅保留標準 Android 高優先級通知，沒有宣稱已接入 OPPO 原子島。

**仍使用原本的中央氣象署 RSS／WorkManager（非秒級推播）。** 開放資料實時推送與各類災害警報需要另接可信任官方來源。整體編譯與不同 HyperOS 版本測試仍必須在實機／CI 完成。

## V10.9 補充

本檔記錄 V10.2 最初只處理單則 CWA 特報的架構。V10.9 起實作通知統一發送入口 `WeatherNotificationDispatcher`，單則特報、特報摘要、焦點測試都會經過路由。詳細請見 `README_V10_9.md`。
