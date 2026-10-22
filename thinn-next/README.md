# thinn-khì NEXT · 全新 Android 工程 0.1（可執行基礎版）

這個專案**不是完整 V25，也不是正式 OTA**。它是從零開發的原生 Kotlin + Jetpack Compose Android App，不複製舊版 MainActivity 的 UI 邏輯。

## 已經存在於程式中的功能

- 可操作的獨立 App：首頁、天氣、地震、颱風、鬧鐘、設定頁。尚未遷移的地震／颱風／鬧鐘頁會**明確註明未完成**，不產生假警報或假鬧鐘。
- 真實 Open-Meteo HTTPS 天氣請求；臺北、嘉義、高雄、花蓮、金門、澎湖可切換；氣溫、體感、濕度、風速、降水資訊；離線最後一次資料、時效判斷、網路錯誤回饋。
- 14 個天氣場景種類定義；只對來源實際回傳的 WMO code 顯示相符場景，不自行捏造霾或沙塵資料。
- 自製光學漸層、玻璃光邊、按壓彈簧、氣氛粒子與導航轉場；無 Material Compose UI 元件。
- 事件優先級、廠商介面選擇權限策略（底層規則，不代表已接入小米超級島或 Live Updates）。
- 第一個會依真實天氣快取更新表情的小工具（無權要求 Android 桌面持續高幀率動畫）；支援點擊回 App。
- 「減少動態」開關、至少 14sp 的文字規格、完整的純 Kotlin 規則單元測試及專案安全檢查。

## 軟體架構

`domain/`：不依賴 Android 的純 Kotlin 天氣模型、事件與系統通知策略。
`data/`：受限大小／連線逾時的真實網路來源與離線儲存。
`design/`：自製光學與物理規範，視覺統一由此控制。
`features/`：真實首頁與獨立功能頁，尚未開發的功能不宣稱完成。
`platform/`：Android Widget，以及未來小米 Focus/Android Live Updates 對接層。

## 安裝與簽章策略（非常重要）

- `debug` APK package id：**`io.atmos.weather.nextlab`**，用 Android debug key，能與已安裝舊 App 共存，但無法讀取舊 App 的私人資料。
- 未來 `release` package id：`io.atmos.weather`。必須由 **原始** release keystore 簽署、版本碼提高且同機覆蓋測試成功，才能對舊用戶 OTA。
- **不要解除安裝舊 App；不要使用 debug APK 覆蓋或轉移正式版資料。** 現在 release build 故意沒有簽章流程，也沒有上傳 OTA 的能力。
- 只有 GitHub CI 的 `next_lab` job `:app:testDebugUnitTest :app:assembleDebug` 通過後，才從 Actions Artifacts 下載 `thinn-next-lab-apk-*` 測試安裝。

## 尚未完成，不能誤當作已驗收

中央氣象署官方颱風與地震全鏈、EEW 後端實測、原生地震地圖、正式鬧鐘與全螢幕鬧鈴、台灣女聲音訊、HyperOS 超級島、小米 Focus 授權、Android Live Updates、完整跨頁共享元素轉場、14 天氣音樂與 28 首鬧鐘音樂、正式資料遷移與舊版簽章覆蓋、三星／小米實機驗收，以及 22 項 V25 完整發布證據。

## 原則

來源不明不能冒充官方資料；編譯成功不等於真機可用；舊版資料絕不能靠解除安裝來解決；所有未完成項目可被看見、可被測試、可被追蹤。
