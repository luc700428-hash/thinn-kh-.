# thinn-khì V22.0 實作紀錄（不是已上架的成品）

## 基礎
- 以 V21.2 原始碼為基礎，保留天氣、地震、工具、設定四分頁、天氣環境效果、智慧生活與 Ollama、歷史回放、OTA 更新中心。
- 升級 `compileSdk`、`targetSdk` 36；AGP 8.11.2，Gradle 8.13，需 GitHub CI 完整建置驗證。

## 已新增程式碼和資產
- FCM Android `FirebaseMessagingService`，**使用者預設不註冊**，可從設定頁明確啟用，缺 Firebase 專案設定時會顯示不可啟用；使用 Firebase 匿名身份搭配 HTTPS callable Function 註冊 Token。
- 推播事件來源白名單、發報時間／過期時間、報次／去重、輸入驗證、高優先級 heads-up 通知與來源標識；無任何『可無條件強行開啟鎖定畫面』的不實承諾。
- Firebase Functions v2 `registerDevice`、`unregisterDevice`、`ingestTrustedEEW`，登入檢查、HMAC 驗證、伺服器報次去重、按區域發送 high priority data FCM。
- Android Compose Design Tokens、新增降低透明度與減少動畫選項，滑桿改為 Android 無障礙支援的原生 Slider，開關提供 Switch semantics。
- 全台 22 縣市一縣市一模型：原創、低多邊形的 GLB + PNG，包含縣市/地標 JSON manifest；這些是**藝術風格示意，不是精準建築攝影測量**，首頁保留 2.5D 天氣/地標畫面，並載入 county PNG。
- 天氣/地震「安心」Glance Widget，城市溫度 + 最近一小時地震推播摘要；不取代系統正式緊急通知。
- 本機 Kotlin 8 項測試、後端 Node 7 項測試、Android JUnit 8 項同政策測試原始碼、CI OTA 工作流程升級。

## 尚未完成／上架阻斷
- **未完成 Android APK 編譯、同簽章驗證、實機／Doze／HyperOS／背景 FCM 延遲測試，未發布 OTA**。
- 未連接具授權的即時 EEW 上游資料或部署 Firebase；不能宣稱「地震發生時一定能強制跳出或即時通知」，官方 PWS 為主要避難警示。
- 未完成官方 CWA 天氣與 AQI 全路徑替換、完整保證精度 P/S 到時模型、iOS 全套視覺與所有子頁、22 縣市高精度實景模型、Play 商店 AAB/政策完整通關。
- 原始 V21.2 Android `REQUEST_INSTALL_PACKAGES` 適合直接 GitHub OTA 發行路線；要上 Google Play 需分離商店發行版、政策核對與 Play In-App Updates。
- Firebase 後端要有金鑰、Firebase Auth 匿名登入、權限、監測、合法來源；Firebase 用量與 Functions 不保證始終免費。
- 22 個地標模型為藝術化輪廓；若要逐棟真實外觀須有專業建模、實際地理資料和合法可再散佈的素材。

## 安全原則
- 不複製 DPIP/TREM-Lite 的程式碼、商標、音效；延續既有 thinn-khì 自有程式和原創資產。
- 不以地震歷史資料、演示事件或未授權 RSS 偽裝 EEW。
- Android 14+ 一般第三方天氣 App 不能保證 Full-Screen Intent 權限；此版本不宣稱繞過系統限制。
