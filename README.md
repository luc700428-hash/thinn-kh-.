本次穩定性修復與待驗收範圍：[V25_STABILITY_20261027.md](docs/V25_STABILITY_20261027.md)。

> 2026-10-27 接續完整工程與驗證：[時間、設定、超級島、小工具與天氣喚醒](docs/V25_IOS_UPGRADE_20261027.md)。

> 2026-10-26 共用日期時間、液態控制、小工具、超級島與天氣鈴聲升級：[工程與研究說明](docs/V25_IOS_CONTROLS_WIDGETS_WAKE_20261026.md)。

> 2026-10-26 更新狀態／碰撞雨效／120 Hz／液態光學修正：[工程與驗證說明](docs/V25_REFRESH_RAIN_GLASS_FIX_20261026.md)。

> 2026-10-26 液態介面／天氣物理／地圖／OTA 更新包：請先閱讀 [工程說明](docs/V25_PHYSICS_UPDATE_20261026.md)。一般簽章 OTA 與完整功能里程碑分開判定，完整驗收狀態並未被改成通過。

# thinn-khì

Android 天氣、地震與生活工具 App；Kotlin / Jetpack Compose，Android 12 以上。

本次更新：[四張參考圖與 Dock 更新](docs/V25_REFERENCE_REDESIGN_20261026.md)。

本機需要 JDK 17、Android SDK Platform 36 / Build Tools 36.0.0；在 android-project 執行 `bash BUILD_APK.sh`。工程附 Gradle 8.13 Wrapper。

完整交付包根目錄的 `UPLOAD.sh` 可於 Termux 推送更新並啟動 APK 建置。正式發布要求原簽章與實機驗收；完整更新包不內含簽章私鑰。
