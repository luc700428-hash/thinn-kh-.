# thinn-khì V10.3 — 焦點通知 / 音效預設 / 台灣 AQI

## 已完成的原始碼改動

- 保留 V10.2 的 HyperOS 焦點通知及超級島 `miui.focus.param`，先查協定及 App 權限，再套用通知資料；其他裝置使用標準 Android 通知。實機顯示須有系統及廠商支援，不能只靠通知 extras 保證「上島」。
- 天氣環境音：新安裝預設開啟、音量 50%；既有使用者先前自行儲存的設定不會被覆蓋。僅 App 前景播放。
- 台灣 AQI：新增環境部 AQX_P_432 官方測站資料查詢（每小時更新），優先以最近測站 AQI 呈現。此 API 需金鑰；設定頁可輸入金鑰。未提供時顯示「—」，不將 US AQI 改名為台灣 AQI。台灣以外仍註明「US AQI」。
- 台灣 AQI 不是「AIQ」。不能由一個美制 AQI 直接換算。環境部使用多污染物與不同平均期間計算。

## 設定環境部金鑰

至 https://data.moenv.gov.tw/paradigm 註冊取得資料存取金鑰，開啟 App「設定 → 台灣環境部 AQI」，在裝置內輸入。適用個人測試；正式商業上架時應改用後端代理，避免裝置端保存 API 金鑰。

## 建置與驗證

上傳 `thinn-khi_Android_Source_V10_3.zip` 至 GitHub Repo 根目錄；以隨附新版 `.github/workflows/build-apk.yml` 內容覆蓋現有檔案。GitHub Actions 執行成功後，於 Artifacts 下載 Debug APK。

目前尚未在 Android SDK 與小米實機測試，請勿宣稱焦點通知已確定顯示於超級島。
