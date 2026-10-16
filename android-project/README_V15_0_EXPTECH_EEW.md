# thinn-khì V15.0 · ExpTech 即時資料串流整合版

- 自行撰寫 Kotlin SSE 客戶端，串接 ExpTech DPIP API documented endpoint `https://api.lb-tpe1.exptech.dev/api/v2/eq/eew?sse=1&compress=1` 和 `https://api.lb-tpe1.exptech.dev/api/v2/trem/rts?sse=1&compress=1`，備援 `api.lb-khh1.exptech.dev`。
- SSE `event:g` -> base64+gzip，資料變動時更新；短線重連。來源參照：https://github.com/ExpTechTW/DPIP/blob/main/api.md
- **以官方/第三方來源標明**；不把一般震後報告冒充提前預警。收到即時資料 != 已具可保障抵達的手機警報；服務必須使用者自己啟動、授權通知，Android 可以中止背景服務。
- 只對具有事件 id、可解析時間戳，且距目前 120 秒內的非測試、非取消 EEW 進行提醒。未知格式丟棄（可能降低覆蓋，避免誤報）。預設全台速報，沒有未經測試的到達倒數。
- 高優先獨立音效 `thinn_eew_warning.ogg` 與通知通道，不會覆寫一般 8 款通知音。
- 原創程式碼，未直接引用 TREM-Lite AGPL 程式碼。第三方 API 的服務規範、使用量限制與授權需開發者自行確認；大量上架／發布前先向 ExpTech 取得許可。
- **限制**：目前工具環境無法連到真實 API，也沒有 Android SDK 編譯，沒有驗證上游現在回應的完整 EEW JSON schema。先做 GitHub Actions build（publish_release=false）與實機監測/事件演練，不應直接宣布正式防災預警已可可靠運作。
- 監測由使用者點擊「啟動」後使用前景服務執行；Android 14+ `specialUse` FGS 會受平台與上架審查政策約束，不能保證永遠持續。沒有 FCM 伺服器推播備援。
