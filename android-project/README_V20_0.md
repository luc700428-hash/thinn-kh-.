# V20.0 差異與實測

本版以 V19.0 為基礎：台澎金馬共用經緯度、P/S 波前與歷史回放重整、玻璃控制元件動畫、版本差異式更新頁。

## 已完成原始碼修改

- `SeismicGeo.kt`：地圖投影涵蓋 118.05–122.45 E、21.65–26.55 N，並新增離島示意外形。
- `SeismicLiveMapPage.kt`：觸控平移縮放、使用者座標、P/S 波前、逐報封存速報回放、進度控制。
- `GlassControls.kt`、`GlassSheets.kt`、`GlassNavigation.kt`、`GlassClockDial.kt`：共用點按、跟手與進退場動畫。
- `AppUpdateDelta.kt`：依 GitHub Releases 分版本讀取新格式增量公告，無增量欄位時不假裝那是新版功能。

## 需要 GitHub Actions 與實機驗收

本環境缺少 Android SDK；原始碼尚未經 Android Gradle APK 編譯。原版 DPIP Flutter 程式未直接移植。
iOS 原生視覺材質與系統級動態島是平台能力；Android Compose 使用獨立自製的近似互動，無法百分之百相同。
台澎金馬外島輪廓為概略示意，測站與地震座標則取用有效來源提供的經緯度。
第三方 EEW 尚未驗證長期可靠性與實際地震事件；正式發布前必須手機測試，且不可取代官方警報。
