# thinn-khì V25｜完整 Android 原始碼整合候選包

**打包日期：2026-10-21**  
**交付型態：包含所有現有 Android 工程檔案的 ZIP；不是幾 KB 的修補腳本，也不是可直接安裝的 APK。**

## 這包具體包含

- `android-project/`：原有 Android Kotlin/Jetpack Compose 應用、Gradle 設定、Android Manifest、官方資料讀取、地震、地圖、颱風、鬧鐘、Widget、設定、工具、更新中心、後端 Functions，以及現存地標 GLB 素材。
- `android-project/app/src/main/assets/next_weather/`：14 種天氣環境 WAV 音效與兩套各 14 種編曲，共 **42** 個獨立 PCM WAV。
- `android-project/app/src/main/java/io/atmos/weather/next/`：完整 NEXT 動態背景、動畫、音效、視覺元件、鬧鐘播放器及 V25 首頁視覺模組。
- 固定「上午／下午」排列與上下滑動的 AM/PM 鬧鐘控制；時、分仍為慣性與吸附滾輪。
- CWA 颱風原有顯示，新增**繁體中文行進方向**與**App 內 CSV 匯入 Google Weather Lab 實驗預報資料**的功能。CSV 與官方警報分別呈現，對照前需要確認為同一颱風。
- `.github/workflows/build-apk.yml`：編譯被 Git 追蹤的原始碼、執行測試、加入 APK 來源驗證與簽章／版本檢查。正式 V25 發布**需先有 22 項驗收證據**，預設只能跑測試。
- `termux_test_build.sh`：將完整原始碼推送到 GitHub **測試分支**、觸發 GitHub Actions 編譯測試，**不會正式 OTA 發布或覆寫 main**。

## 此 ZIP 的真實狀態

此次可取得的最後一套完整 Android 基底，是 **V23 R11 完整工程候選**；後續 NEXT/V25 可取得的工程檔、音效、重點修正已直接接入基底，而非另附零散補丁。**我們無法從 GitHub 讀取你目前 `main` 的完整最新版內容**，因此本 ZIP 不能保證完全對應你手機目前所裝的 V25，亦不能保證已包含所有曾經提過的功能。

本地已完成：**111 項 Python 工程／回歸測試通過；15 項 Node 後端測試通過；42 個音效資產驗證通過；數個純 Kotlin 模組可用 Kotlin CLI 編譯。** 但本環境沒有 Android SDK 與 Gradle，因此**Android APK/Compose 全專案編譯尚未成功驗證；沒有 Android 實機、原版簽章或正式 OTA 安裝驗收。** 舊版檔案、現有 Play/OTA 簽章不能由 ZIP 取代。

正式發布前仍需：比對你 GitHub 的現行 main 原始碼並避免倒退覆蓋、CI `testDebugUnitTest` 和 `assembleRelease` 全程通過、簽章與既有 APK 相同、測試鬧鐘與動態島真機、天氣／地震推播可靠性、22 項 V25 完整驗收。部分需求（Google Weather Lab 自動實時 API、100% iOS 私有動畫、全臺每處高精度地標、系統未授權小米超級島）目前**沒有完成證據**。

## Android 手機只做編譯測試（不要正式 OTA）

1. 在手機下載並解壓本 ZIP，開啟 Termux，執行 `pkg install git gh -y`（GitHub CLI）。
2. 若還沒登入 GitHub，執行 `gh auth login`。
3. `cd` 到解壓後含 `android-project`、`.github` 的專案資料夾，執行：
   ```bash
   bash termux_test_build.sh 你的帳號/你的儲存庫
   ```
4. 腳本只會上傳到新建的 `test/v25-integrated-*` 測試分支，並觸發 Actions 的 `publish_release=false`；在 GitHub → Actions 看 `thinn-khi-debug-TEST-NOT-OTA` 或失敗記錄。**不要把此重建候選包直接合併覆蓋當前 main。**

## 已保留，但不能作出成功保證

- 更新後資料與簽名升級相容性：App package 保持 `io.atmos.weather`；是否可以覆蓋安裝，仍要核對舊版簽章。
- 地震預警與台灣地區過濾、天氣警報：保留來源限制與演練標示；不可取代中央氣象署、災防告警細胞廣播。
- Google Weather Lab：由使用者自行匯入**匹配欄位** `latitude`、`longitude`、`time` 的 CSV 檔（可接受程式中的欄位別名）；不是 Google 即時 API，也不保證任意 Weather Lab 下載檔格式可直接讀取。
- 小米超級島與動態光效：Android/OEM 平台對第三方 App 有授權限制，無法保證 100% iOS 物理行為。

這包是**可檢視、可上傳分支、可執行 CI 的完整整合來源**；不應宣稱「V25 所有功能 100% 完成」或「已成功發布 OTA」。
