# thinn-khì V10.9｜APP 通知統一走焦點通知優先通道

## 已實作

- Android 原生通知全部集中由 `WeatherNotificationDispatcher.post()` 發送，保留原有的通知通道、通知內容與群組設定。
- APP 目前會建立的三種通知：正式中央氣象署單則警特報、群組摘要、手動焦點測試通知，全部經過同一通知路由。
- 在小米/Redmi/POCO 裝置上，檢查 `notification_focus_protocol`（HyperOS 2/3）及官方 `canShowFocus` 授權，符合才附加 `miui.focus.param` 與 `miui.focus.pics`。
- HyperOS 2 使用 `ticker` + `baseInfo`；HyperOS 3 額外帶 `param_island`，沿用小米官方 weather 文本/圖像示例組件。
- 未獲權限、未經官方核准的場景、一般 Android 手機或不同版本系統，都維持標準通知，絕不因無法上島而阻擋真正的天氣特報。
- `10 秒真實情境演示` 仍為 APP 內模擬天氣事件，不發送真的氣象警報，也不冒充系統緊急通知。
- 設定頁顯示焦點通知支援狀態，測試通知會在背景執行授權查詢，避免阻塞主執行緒。
- 承襲 V10.8 的獨立更新頁、單一觸覺 100%、10 秒情境演示與透明啟動畫面 LOGO。

## 為什麼不能保證所有通知都「直接出現在焦點通知」？

小米開發者官方文檔指出，第三方 APP 必須先提出通知情境方案、申請權限、完成上島測試與正式審核；新增未審核情境可能導致官方停用。程式能發出原生通知並附加焦點資料，但系統仍有最終決定權。不可透過私有 API、root、Hook 或模擬緊急訊息繞過。

- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132
- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2146

## 權限申請與實機驗證

1. 設定 App 的 Android 一般通知授權；在 APP 設定頁查看小米焦點通知狀態。
2. 以正式的包名 `io.atmos.weather` 向小米申請對應 weather warning、warning summary 等場景審核。若申請 APP 內一般更新公告等新情境，需要分別提交並核准。
3. 取得核准後，於 HyperOS 2 與 3 支援裝置上分別確認狀態列、鎖屏與島的顯示方式。實測測試內容必須明示非正式氣象警報。
4. 確認一般 Android 及未核准小米手機仍能正常收到正式警特報。

## 編譯與安裝

把 `thinn-khi_Android_Source_V10_9.zip` 放到現有 GitHub Repo 根目錄，`.github/workflows/build-apk.yml` 改用 `thinn-khi-V10_9-build-apk.yml`。到 GitHub Actions 執行建置。正式 OTA 需四個持續沿用的簽章 Secrets（APK_KEYSTORE_BASE64、APK_KEYSTORE_PASSWORD、APK_KEY_PASSWORD、APK_KEY_ALIAS），不能自行更換舊版金鑰。預設 Version Code 改為 1090000（Actions 會根據 run number 提高），顯示版本為 V10.9。

**驗證狀態：** 完成靜態原始碼檢查與通知路由覆蓋檢查；此環境無 Android Gradle SDK，尚無法完整編譯 APK，也未在 Xiaomi 手機驗證真正的焦點通知／超級島展示。
