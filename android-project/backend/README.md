# thinn-khì V22 FCM 地震推播後端（尚未部署）

本資料夾是一套可部署的 Firebase Functions v2 傳輸層，**不是**自行具備 EEW 取得權限或官方警報身份的預警系統。

1. 建立 Firebase 專案，啟用 Firebase Authentication 的匿名登入、Firestore、FCM、Cloud Functions，配置正確服務區域，並確認所需計費方案。
2. 在 Firebase CLI 登入授權自己的專案，在此目錄執行 `firebase use <your-project>`，在 `functions/` 執行 `npm ci`（需先生成並鎖定 package-lock）或 `npm install`、`npm test`。
3. 使用 `firebase functions:secrets:set ALERT_GATEWAY_HMAC_KEY` 建立至少 32 字元的隨機 HMAC 金鑰（絕對不可放進 APK、Git 或交付給客戶端）。
4. 在本目錄部署 `firebase deploy --only functions`；注意 IAM、Firestore rule、App Check、配額與用量費用。
5. 在 Android CI 提供五組 **公開 Firebase 專案設定**：`THINN_FIREBASE_APP_ID`、`THINN_FIREBASE_API_KEY`、`THINN_FIREBASE_SENDER_ID`、`THINN_FIREBASE_PROJECT_ID`、`THINN_PUSH_FUNCTIONS_REGION`；它們不是伺服器憑證。
6. **必須另行部署有合法即時 EEW 資料授權、可驗證的來源解析器及簽章發布器**，由其發送 HMAC 驗證請求給 `ingestTrustedEEW`。本套程式沒有把震後地震報告提升成 EEW；沒有取得合法來源前不得開啟正式地震速報推播。

後端只准許 EEW 白名單來源代碼、時間/有效期/事件 ID 驗證、單事件 revision 去重，並使用 high priority data-only FCM；不提供保證到達時間與強制喚醒權限。客戶端使用者明確啟用後才匿名註冊 Token；正式部署前仍必須做 Firebase Auth/AuthZ、App Check、Firestore Rules、負載、資安、Doze/鎖屏/HyperOS、推播延遲與錯誤恢復實機測試。

**產品安全聲明**：此產品是加值輔助，不是政府 PWS，不能取代中央氣象署與手機系統災防告警。
