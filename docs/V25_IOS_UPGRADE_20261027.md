# thinn-khì V25：時間、設定、超級島、小工具與天氣喚醒

本次接續 2026-10-26 工作存檔，將修改檔還原至完整 Android 工程，並補回未附上的音訊。底稿對應 GitHub main `45f4e0832cf30fcba7a648d6f4e7599399307dd2`；包內附完整原始碼、相對此底稿的更新檔、完整性清單與驗證紀錄。

## 本次修改

| 項目 | 實際行為 |
| --- | --- |
| 調整時間 | 鬧鐘、事項、天氣通知、日期、延遲響鬧和更新頻率共用圓柱滾輪；AM 永遠在 PM 上方，保留 24 小時儲存值、逐格提交、慣性、觸覺和邊界回彈。日期依閏年及每月天數校正。 |
| 設定 | 統一內縮分組、圓形返回、光學按鈕、滑動分段控制、開關和滑桿；地震、智慧生活、桌面與通知子頁跟隨深淺色外觀。輸入框和載入指示也使用自繪元件。 |
| 操作效果 | 設定列加入果凍按壓；開關支援左右拖動，滑桿鎖定水平手勢，避免攔截上下捲動；回彈依外觀設定變化，減少動態效果即時生效。 |
| 小米超級島 | 20 種語意、40 張狀態圖示；補齊鬧鐘時間、有效天氣、降雨機率、風速、真實倒數與下載進度；保留停止、稍後提醒與暫停下載等原通知操作。 |
| 更新通知 | 實際下載服務接到超級島，進度取自已下載位元組；下載及進行中的計時可向支援的 Android 系統請求 Live Updates。 |
| 桌面小工具 | 保留既有 25 個小工具身分，重新設計即時天氣、逐時預報、天氣時鐘與提醒、氣象數據、安心資訊五類，各五色。加入原生桌面時鐘、倒數及圖示轉場；安心小工具可直接開啟地震中心。 |
| 資料狀態 | 小工具顯示快取或過期狀態；缺少溫度不再顯示成 0°，過期天氣不繼續顯示為目前狀況。背景同步保留取消訊號。 |
| 天氣鈴聲 | 六款全部重作：84 組天氣旋律、6 組中性旋律、14 組天氣聲景；44.1 kHz／16-bit 單聲道 PCM 循環，另附 18 個 Ogg/Vorbis 備援鈴聲。 |
| 聲音生命週期 | 鬧鐘使用鬧鐘音量；一般天氣環境音只在前景播放，切換天氣途中進入背景時也會暫停，離開畫面後釋放音軌。音訊產生時先驗證暫存檔，再替換成品，避免空白備援鈴聲。 |

## 鈴聲研究與設計

PLOS ONE 2020 年的研究以 50 份自陳問卷發現，較有旋律的喚醒聲與較低的主觀睡眠慣性感受相關；不能由此推出某個 BPM 或本包鈴聲保證能叫醒所有人。本次採用清楚的短旋律、和聲回應與輕節拍，搭配較小聲的天氣聲景和約 18 秒漸強。旋律與聲景分軌，雷雨不以突發高音量雷擊掩蓋旋律。

| 鈴聲 | 主要音色 | 基準速度，屬設計選擇 |
| --- | --- | --- |
| 晨光序曲 | 鋼琴、暖和弦 | 104 BPM |
| 雨光節拍 | 柔鋼琴、回應旋律 | 102 BPM |
| 森間晨行 | 木琴、撥弦 | 108 BPM |
| 水漣初醒 | 豎琴式撥弦 | 106 BPM |
| 晴日躍動 | 明亮撥弦、輕節拍 | 112 BPM |
| 霜光鈴奏 | 鐘琴、和聲 | 110 BPM |

依天氣調整速度、亮度和聲部；缺少有效天氣時播放中性編曲。原創旋律及程序聲景可由 `android-project/tools/compose_weather_alarm_suite.py` 重建；雨聲來源延續 `docs/WEATHER_AUDIO_SOURCES.md` 的歸屬說明。數值音訊檢驗包括非靜音、峰值、取樣格式、循環接縫與檔案雜湊，不替代真人試聽與起床效果測試。

## 建置、使用與驗收

1. 解壓縮更新包後，在 Termux 執行根目錄的 `bash UPLOAD.sh`。它先檢查檔案與回歸測試，再上傳程式並啟動候選 APK 建置。
2. 候選建置沿用 GitHub 已設定的原始簽章。完成後從 GitHub Actions 下載該次建置產物安裝；這一步沒有把 OTA 發布給全部使用者。
3. `bash UPLOAD.sh --check` 僅檢查更新包，不登入或上傳。`--publish` 是另行明確選擇的正式 OTA 發布流程。
4. 完整編譯、測試數目、Lint 和套用檢查，請見更新包根目錄的 `VERIFY_REPORT.md`。

本包使用自製 Android 元件對齊 iOS 設計語言，沒有把 Apple 私有渲染器移植到 Android。未取得逐畫素、相同觸覺馬達與真機動態曲線的比對證據，因此不宣稱已驗證「與 iOS 27 100% 相同」。

桌面小工具使用系統允許的 TextClock、Chronometer、ViewFlipper 和光學底圖，無法任意讀取桌布作逐幀折射。小米系統控制上島授權、動畫與展開；不支援時保留普通通知與鬧鐘。真機鎖屏、重開機、覆蓋安裝資料保留、小米超級島及桌面相容性、觸覺與喇叭聽感仍須在手機上驗收。原有 22 項正式發行驗收狀態未被自動改為通過。

## 核對來源

- Apple iOS 27：<https://www.apple.com/tw/os/ios/>
- Apple Liquid Glass：<https://developer.apple.com/design/human-interface-guidelines/materials>
- Apple 控制與動效：<https://developer.apple.com/design/human-interface-guidelines/pickers>；<https://developer.apple.com/design/human-interface-guidelines/motion>
- 小米超級島開發指南與 2026-01-29 官方模板庫：<https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131>
- 小米展開與消失規則：<https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2144>
- Android RemoteViews：<https://developer.android.com/reference/android/widget/RemoteViews>
- Android Live Updates：<https://developer.android.com/develop/ui/views/notifications/live-update>
- McFarlane SJ 等，2020，Alarm tones, music and their elements：<https://doi.org/10.1371/journal.pone.0215788>
