# 雷達清晰度、黑色模式與 144Hz 更新

本次接續已上傳的 `509c95d8f99a468fb20a179801a12e665b1dac1c`，保留搜尋、環境音、28 款小工具、字體與滑桿修正。

## 雷達

- 底圖與回波分開選取圖磚。底圖隨手勢載入當前縮放層級的新地理細節，檢視可到街道層級（地圖縮放 16），不再與雷達共用上限 7。
- RainViewer 圖磚由 256 改成 512 像素，使用相同地理範圍的高密度圖磚，關閉伺服器模糊。這不是把每個像素改稱更精確的測量。
- 雷達來源最高層級仍為 7；超出後保留原觀測格點，介面顯示「回波細節已達資料來源上限」。不虛構街道級降雨測量、不把內插當成新資料。
- 深色、淺色及跟隨系統模式都由共用 MaterialTheme 決定；切換時不重建相機或時間軸。僅將底圖轉成深色，回波顏色及其雨勢意義不變。
- 兩層分別載入，播放歷史回波不重載底圖；回波載入完成後才進到下一張。更新與圖磚重試可同時重新嘗試失敗的圖資。
- 只請求目前可見範圍，不抓周邊預備圖磚。超大視窗若會超過 36 張，就降低圖磚層級覆蓋整個視窗，不裁掉最底列。
- 沿用 4 個並行請求、12MiB 位圖快取、48MiB 磁碟修剪、底圖七日與回波三小時快取。新回波快取識別與舊版 256 像素模糊圖磚區分。

## 更新率

- 共用目標從 120 改為 144Hz，支援 143.9 等實際硬體值；不切換螢幕解析度。裝置沒有該模式時選擇可用相近模式。
- Activity、Compose 主頁、鬧鐘、對話框、抽屜、地圖及彈出選單共用同一偏好。Android 16 使用子 View 傳遞，Android 15 處理已存在的子 View，較舊版本保留視窗層級偏好。
- 彈出選單額外更新自己的 WindowManager 根節點；3D 的獨立 Surface 使用相同目標與僅限無縫切換策略。
- 監聽螢幕模式變更並重新核對；不每幀重設視窗。診斷頁分別顯示 144Hz 目標與系統回報的螢幕更新率。
- 動畫仍依 Compose／Choreographer 的時間與螢幕同步運作，沒有用固定的 6.94ms sleep 假裝得到 144FPS，也沒有提高網路資料更新頻率。
- 支援 144Hz 不代表保證每幀都在 6.94ms 內完成。裝置、Android／小米政策、省電與溫控決定實際模式；桌面小工具與系統動態島由外部宿主控制，App 無法強制它們的更新率。

## 檢查與資料來源

新增高縮放圖磚／投影、地理覆蓋預算、快取隔離、原生深淺色圖層像素、過度放大記憶體、更新率選取與 Android 12／15 相容性測試。原本的七項裝置畫面測試閘門保留，新增編譯不代表在使用者手機跑過。

原生預覽使用測試資源中各一張 OSM 底圖與 RainViewer 回波；下載時間、來源 URL、圖層時間與 SHA 在 `app/src/test/resources/radar-hd/preview.json`，僅作重現測試，不標成現在天氣。

- RainViewer 官方 API：https://www.rainviewer.com/api/weather-maps-api.html
- RainViewer 使用條款與署名：https://www.rainviewer.com/api.html
- © OpenStreetMap contributors：https://www.openstreetmap.org/copyright
- OSM 圖磚政策：https://operations.osmfoundation.org/policies/tiles/
- OSM 渲染圖磚 CC BY-SA 2.0：https://creativecommons.org/licenses/by-sa/2.0/
- Android 更新率偏好：https://developer.android.com/media/optimize/performance/frame-rate
- Android ViewGroup 144Hz 請求與傳遞：https://developer.android.com/reference/android/view/ViewGroup
