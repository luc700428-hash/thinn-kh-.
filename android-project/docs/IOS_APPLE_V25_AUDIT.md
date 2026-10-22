# thinn-khì V25 → Apple-style native Android 全域改版與驗收規格

版本：v1／工程基線：V25 測試分支（不改動正式 v25.0.236）

## 必須誠實區分

- 本工程目標是以 Android 原生能力**重新製作**接近 iOS 的資訊架構、視覺材質和操作手感；不能在 Android 使用 SwiftUI/UIKit/CoreHaptics 或強迫 HyperOS 以 Apple SystemUI 繪製。
- 不能聲稱僅憑源碼、GitHub CI 就達到逐像素 1:1。需要使用相同裝置實際錄影與可追溯的參考畫面逐頁比較，並對 Android 系統列、字體渲染、螢幕比例作透明的差異註記。
- 禁止直接複製 Apple 系統字型、受限制圖像、SF Symbols 圖檔及相關資產；圖示採單一風格的自製向量，保留用途、線重、24px 光學尺寸、明暗和狀態動畫一致。

## Apple 公開規範（有依據，不臆造 Apple 私有數值）

1. Motion：每個動態有功能意義，可中斷與停用，避免無意義反覆晃動。https://developer.apple.com/design/human-interface-guidelines/motion
2. WheelPickerStyle：中央選取值，附近相鄰值，全部選擇欄位同樣的滑動方式。https://developer.apple.com/documentation/swiftui/wheelpickerstyle
3. SwiftUI spring：切換中保留速度與適當的回彈；Android 以自製共同物理參數近似，不宣稱拿到 Apple 私有參數。https://developer.apple.com/documentation/swiftui/animation/spring
4. SF Symbols：線重、光學比例、單色/階層/調色/多色、選取及狀態動畫；Android 自製向量，不直接包入 Apple 專有資產。https://developer.apple.com/design/human-interface-guidelines/sf-symbols
5. Haptics：短促、用途明確、可關閉，不在滑動每影格連續震動。https://developer.apple.com/design/human-interface-guidelines/playing-haptics
6. App icons：明／暗／清晰／著色的材質與層次需平台適配。https://developer.apple.com/design/human-interface-guidelines/app-icons

## 全域元件驗收範圍（全部必做；本次並非已通過）

| 模組 | 視覺 | 動態/物理 | 功能及資料 |
|---|---|---|---|
| 天氣首頁 | 城市、大溫度、真實天氣動態場景、逐時與十日預報、柔和玻璃卡片 | 頁面縮放、拉動、哪來哪去、視差減弱 | 真資料與快取時效、CWA 告警、空氣品質單位 |
| 城市列表/搜尋 | 分組、頂端導覽、搜尋、地點卡 | 放大返回原城市卡位置 | 多城市、台灣地名與 GPS |
| 雷達、颱風、地震地圖 | 共用真實地理座標、標記、圖層、時間軸 | 平滑拖拽、縮放、時間軸吸附 | 官方來源、時間戳、風圈及中文方向、不得偽造事件 |
| 鬧鐘列表 | 深色分組、橘色操作、24/12 小時清晰 | 開關、加入、刪除、返回轉場 | 可靠排程、鎖屏、貪睡、系統鬧鐘音量 |
| **鬧鐘編輯** | **時、分、上下午共用中央選取帶；時→分→上下午排列** | **相同 41dp 步長、速度預測、140ms 歸零、刻度觸覺；AM/PM 上下僅兩值不循環** | **12 AM=00:xx、12 PM=12:xx、23:xx 正確儲存** |
| 系統設定 | 深色分組行、開關、導航層級、回退 | 共享元素與可中斷彈簧，返回原控制 | 權限真實狀態、資料保留、減少動態與高對比 |
| **系統更新** | **以 iOS 設定更新頁的資訊層級為目標：版本、更新狀態、安全安裝；目前藍色大玻璃卡片尚不符合** | **進度、驗證、失敗、返回狀態完整** | **正式簽章 SHA256、versionCode、原 App 資料與簽章相容** |
| 通知/小米超級島 | 單一狀態語意，圖示尺寸與線重 | 事件開始、進行、結束，動畫 fallback | 小米廠商批准／系統授權為真；不得冒充有上島 |
| Android Live Updates | Android 合規持續通知 | 更新頻率受 OS 控制，狀態一貫 | 使用者主動開啟的持續監控；不能把普通預報當 Live Update |
| Widget | 自製天氣角色表情、天氣圖示、清晰溫度 | 事件更新／有限影格，不聲稱常駐 60fps | 真資料、過期標記、桌面系統限制 |
| 語音/天氣音效 | 14 天氣情境 | 淡入淡出、不中斷使用者操作 | 真人感溫柔台灣女聲品質、離線備援、完整音效資產 |
| 全站圖示 | 統一向量母版、選取態和禁用態 | 事件對應 bounce/replace/rotate 等自製動畫 | 不匯入 Apple 私有 icon 檔案 |
| 全站觸覺 | 按壓、選取、吸附、完成、錯誤、警示 | 僅在刻度改變時提供離散反饋 | respect 系統震動設定與不同硬體 |

## 不可忽略的實機矩陣

- 小米 HyperOS + 三星 + Android 16，深/淺色，較大字體，TalkBack，減少動畫，節電模式，60/90/120Hz。
- iPhone 同操作錄影參考與 Android App 同操作錄影對照：相同順序的開關/滑動/返回操作，同步標記差異。
- 鬧鐘滑動：靜止單步、長滑、快速 fling、手勢中斷、保存立刻按下、午夜 12、正午 12、上午下午切換；不得選錯 PM 或重複跳動。
- 5 個未通過即禁止正式 OTA：資料未遷移、包名或簽章不一致、地震誤報、鬧鐘失準、OTA APK 哈希不一致。

## 本輪原始碼實際修改

- 取消與時/分不同的 `MeridiemWheel` 自有手勢和高亮動畫；改成共用 `Wheel` 滑動元件。
- iOS 方向的排列：小時、分鐘、AM/PM；中心固定選取帶，統一 41dp 格距、速度反饋與刻度震動。
- AM/PM 不顯示多個重複的上午下午字樣：有限兩行，上下滑動與小時/分鐘同手感。候選值在 save 前同步提交。
- 新增純 Kotlin `IOSWheelSelection` 供界面/測試共用；保留原有 `V25AlarmMeridiem.hour24` 轉換與鬧鐘資料格式。
- 舊的固定標籤動畫假設不再適用；更新受影響的回歸測試，並加新單元測試以證明滾輪的行為。

### 尚未做完

上述其他頁面的視覺換新、完整光學引擎、小米超級島廠商認證、所有動畫觸覺與實機校準尚未完成。此文是驗收規格，不是通過證書。`main` 與正式 OTA 不在本輪更改範圍。
