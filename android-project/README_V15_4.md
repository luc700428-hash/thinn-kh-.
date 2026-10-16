# thinn-khì V15.4｜智慧天氣推播與地震警示

## 新增程式
- `domain/model/WeatherBriefingPlan.kt`：純 Kotlin 每週排程，跨日時區／下次提醒分段
- `briefing/WeatherBriefingStore.kt`：最多五組通知 JSON 儲存
- `briefing/WeatherBriefingScheduler.kt`：WorkManager 單次下一筆排程＋開機、時區變更重排
- `briefing/WeatherBriefingWorker.kt`：讀取 Open-Meteo 實際逐時資料，生成指定區間預報，缺資料不杜撰
- `briefing/WeatherBriefingSettingsPage.kt`：五組通知新增、修改、週期選取、停用／刪除與預覽

## 地震
- 持續保留 V15.3 的歷史快照回放、P／S 波示意、有效位置優先、語音與震後報告（第三方彙整）
- 高優先地震通知對接小米焦點通知候選。
- 可由使用者額外允許 Android 的全螢幕意圖特殊權限時嘗試顯示，不能繞過 Android 鎖定畫面。
- 臺灣震度最高 7 級；語音不會報讀不存在的震度 8。

## 驗證和限制
- 此環境未有完整 Android SDK，APK 完整編譯需使用 Github Actions。
- `WorkManager` 的時分排程是盡力執行，可能因 Doze／省電設定延遲。
- 串流／通知／地震警示的真實事件驗證尚未完成，不應在驗證前公開作為正式地震預警。
- 官方 CWA 出版核驗後才推播需要開發者經驗證的來源，不能拿第三方彙整冒充。
