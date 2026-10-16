# V22.0 Gradle 建置問題修正（2026-10-16）

## 使用者實際 CI 錯誤

`app/build.gradle.kts:23:9: Unresolved reference: buildConfigField`

`:app:testDebugUnitTest` **尚未進入 Kotlin/Android 測試**；此錯誤是 Gradle Kotlin DSL 在初始化專案時即中止。

## 改動

原始碼 `app/build.gradle.kts`：把 Firebase 公開設定值的 `buildConfigField` 宣告移入 `android { defaultConfig { ... } }`，保留 `buildFeatures.buildConfig = true`、原 package、Android 目標 API 36、Firebase 組件與 22.0 版號邏輯。新增字串換行跳脫，避免 CI 變數內意外換行破壞 BuildConfig.java。

發版流程採用新的 ZIP 檔名及固定 SHA-256，避免取得舊版有錯誤的原始碼。舊套件及舊 GitHub workflow 預設會繼續引用原始 V22.0 ZIP，不能只上傳修正 ZIP 卻不更新 workflow。

## 已檢查 / 尚未驗證

- 已核對使用者上傳的原始 GitHub Actions `build.log`，明確定位第一個失敗的 Gradle DSL 原因。
- 已進行 Kotlin DSL 原始碼結構檢查、ZIP CRC 檢查、YAML 解析、Node 7 項後端事件驗證測試、純 Kotlin 地震去重策略 smoke test。
- 尚未在本環境執行 Gradle Android 全套建置；**不能宣稱此修正後的 CI、APK 簽署或 OTA 發布已成功**。需將附件的 build-apk.yml 放回 `.github/workflows/build-apk.yml` 並上傳修正原始碼 ZIP，再執行 Actions，確認後續有無其他 Kotlin 編譯錯誤。
- 請先用 `publish_release=false` 測試。若 CI 成功，依同一套既有簽章金鑰重跑 `publish_release=true` 才會產生可正式升級的 APK；不可使用 debug APK 當作 OTA。
