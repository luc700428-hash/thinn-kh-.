# thinn-khì V10.7｜空氣品質直連與 APK 更新不衝突

## 一、空氣品質已直接串接 Open-Meteo

V10.7 已移除 WeatherAPI 金鑰輸入和額外查詢。

- 地點定位後，程式的 `OpenMeteoProvider` 已透過 `https://air-quality-api.open-meteo.com/v1/air-quality` 自動取回 `pm2_5,pm10,aerosol_optical_depth`。
- 能見度透過 `https://api.open-meteo.com/v1/forecast` 的 `visibility` 取得，單位由公尺換算公里。
- 空氣品質是 CAMS 模型預報，不是台灣環境部 AQI 測站數值。
- 一般非商業用途不需要使用者 API Key；如作商業 App 使用，應先檢查 Open-Meteo 的商用方案和授權條款，並標示資料來源。

## 二、為什麼每次更新都有「App 衝突」？

既有 GitHub Actions 編譯 `:app:assembleDebug`。雲端 Runner 可能在每次執行時建立新的 `debug.keystore`，每次 APK 的**簽署證書**不同。即使 `applicationId=io.atmos.weather` 一樣，Android 也不允許不同簽章直接覆蓋。另需確保 `versionCode` 不得降低。

V10.7 新增 **固定簽署的正式 APK 工作流程**，每次使用同一把 JKS，並根據 `GITHUB_RUN_NUMBER` 自動遞增 `versionCode` (100000 + 執行次數)，避免更新時出現版本倒退。

> **既有舊版 APK 若使用不同簽章，無法藉由改版補救已裝 App 的簽名。** 如果沒有原本的簽章金鑰，首次換成固定簽章版時，仍需先備份重要資料、解除安裝一次。之後請只安裝固定簽章版本，才能直接覆蓋升級。

## 三、建立固定簽章（僅需首次設定）

在自己能信任的電腦（或私人受控的遠端開發環境）執行：

```bash
keytool -genkeypair -v -keystore thinn-khi-release.jks -alias thinn \
  -keyalg RSA -keysize 3072 -validity 10000
```

請備份 **JKS + 金鑰密碼**，切勿上傳到公開 GitHub Repository、公開聊天或提交至程式碼版本控制。

將 JKS 轉 Base64（Linux/macOS）：

```bash
base64 < thinn-khi-release.jks | tr -d '\n'
```

在 GitHub 專案中進入 `Settings → Secrets and variables → Actions → New repository secret`，設定以下四個：

| 名稱 | 值 |
| --- | --- |
| `APK_KEYSTORE_BASE64` | JKS 的 Base64 字串 |
| `APK_KEYSTORE_PASSWORD` | JKS 密碼 |
| `APK_KEY_ALIAS` | `thinn`（或你自己取的 alias） |
| `APK_KEY_PASSWORD` | 私鑰密碼 |

這四項不能省略，也不要把金鑰寫進公開 YAML。若任何一項缺失，建置會明確失敗，不會混充可升級的 Debug APK。

## 四、GitHub 上傳與建置

1. 把 `thinn-khi_Android_Source_V10_7.zip` 上傳至 Repository 根目錄。
2. 把 `thinn-khi-V10_7-build-apk-SIGNED.yml` 的**全部內容**貼到 `.github/workflows/build-apk.yml`，提交到 `main`。
3. 觸發 GitHub Actions 並在 Artifacts 下載 `thinn-khi-V10_7-SIGNED-APK`，裡面有 `thinn-khi.apk`。
4. 後續編譯請一直使用同一套 Secrets；不要再用 `app-debug.apk` 覆蓋正式版。
5. 要啟用 App 內 OTA：Actions → `Build thinn-khi V10.7 signed update APK` → `Run workflow` → 勾選 `publish_release`。成功後會產生 GitHub Release，App 的「檢查更新」即可偵測其版本。

## 五、限制

- 正式 APK 無法在 Android 上靜默強制安裝；系統會要求使用者確認。
- 這份程式仍需在 GitHub Actions 完成 Gradle 編譯，在實機測試覆蓋安裝。
- 簽署問題不是改 `versionName` 就能修好，必須是**同一把簽署金鑰**。
- 若 Open-Meteo 暫時無資料或網路不可用，畫面會顯示「目前無法取得空氣品質模型資料」而非虛構數據。
