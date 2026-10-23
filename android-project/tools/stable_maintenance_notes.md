# thinn-khì V25 · 正式 OTA 更新

本次版本透過 GitHub Actions 從 main 分支原始碼編譯、採用既有 Android 正式簽章，並以 GitHub Releases Latest 提供 App 內正式 OTA 更新。

發布前需通過 Kotlin 編譯、單元測試、現有安裝套件名稱與簽章相容性、版本碼遞增、APK SHA-256 與 ota.json 驗證。安裝仍需使用者透過 Android 系統安裝程序確認。

本次包含原生介面呈現層更新：液態 Dock 選取動畫、視差互動元件、
系統更新介面的真實進度動畫、颱風移動方向中文顯示、首頁警報與內容間距調整。

OTA 下載、APK 簽章、版本遞增與安裝確認仍遵循既有安全驗證。地震與颱風警報請以中央氣象署官方資訊為準。
