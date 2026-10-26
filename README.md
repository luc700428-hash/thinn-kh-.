> 2026-10-26 液態介面／天氣物理／地圖／OTA 更新包：請先閱讀 [工程說明](docs/V25_PHYSICS_UPDATE_20261026.md)。一般簽章 OTA 與完整功能里程碑分開判定，完整驗收狀態並未被改成通過。

# thinn-khì

Android 天氣、地震與生活工具 App；Kotlin / Jetpack Compose，Android 12 以上。

本次更新：[四張參考圖與 Dock 更新](docs/V25_REFERENCE_REDESIGN_20261026.md)。

本機需要 JDK 17、Android SDK Platform 36 / Build Tools 36.0.0；在 android-project 執行 `bash BUILD_APK.sh`。工程附 Gradle 8.13 Wrapper。

完整交付包根目錄的 `UPLOAD.sh` 可於 Termux 推送更新並啟動 APK 建置。正式發布要求原簽章與實機驗收；完整更新包不內含簽章私鑰。
