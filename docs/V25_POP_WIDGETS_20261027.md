# V25 選取狀態、流行天氣鬧鐘與桌面小工具重建

基底：`2ca1a8ff37f1b2635211b6cd587f9f4acf9e346b`。日期：2026-10-27。

## 選項辨識

智慧生活的溫感與穿搭、字體選擇共用 GlassChoiceGroup。選中項目以勾號、白字和深藍底表達，未選項目使用低彩度底色，並提供 RadioButton 角色、互斥選取群組與選取語意。文字放大或窄窗會換行。分段頁籤也加強選中對比，保留彈簧滑移。

## 六首原創流行短曲

| ID | 名稱 | 編曲 | BPM | 長度 |
|---|---|---|---:|---:|
| citypop | 城市晨光 | City Pop、電鋼琴、合成銅管與彈跳貝斯 | 104 | 36.9 秒 |
| tropical | 海風假期 | Tropical Pop、木琴、切分伴奏與沙鈴 | 110 | 34.9 秒 |
| indiepop | 晴空出走 | Indie Pop、吉他刷奏與輕快鼓組 | 116 | 33.1 秒 |
| rnb | 雨後慢拍 | Neo Soul / R&B、七和弦、搖擺節奏 | 86 | 44.7 秒 |
| futurepop | 極光派對 | Future Pop、鈴音、副歌分層與電子節拍 | 120 | 32.0 秒 |
| daybreak | 日出情歌 | Piano Pop、鋼琴旋律與溫暖和聲 | 92 | 41.7 秒 |

每首 16 小節，具開場、推進、完整主題及回應段落。每首有一般、晴天、雨天、雪天四個編曲，共 24 組 PCM 及 24 組 Ogg 備援。天氣未知／過期使用一般編曲，有效天氣才混入既有自然聲景；雷聲維持較低音量。新鬧鐘預設城市晨光，舊有 14 款與自選本機音樂維持相容。點選最長試聽 40 秒，再按右上勾號套用。

所有旋律與音色合成由 `tools/compose_pop_weather.py` 原創產生，不使用小米／Apple 錄音、旋律或第三方歌曲／樂器採樣。32 kHz mono PCM16 由既有 AudioTrack 精準循環，Ogg 為 44.1 kHz mono。保持漸強與音訊焦點處理。音檔峰值、非靜音、端點及備援解碼可客觀核對；尚未做人耳聽感或起床效果驗證，不以自動測試宣稱主觀好聽或人人有效。

## 桌面工具全數重建

舊有 26 款工具的內容樹已替換，保留 receiver 識別讓已放置的工具可更新，另新增第 27 款「光曜指針時鐘與天氣」。五種配色保留，版型完全重新配置：即時天氣、逐時預報、天氣時鐘、氣象數據、天氣與安心，以及新指針時鐘。

依啟動器提供的實際 dp 寬高及 App／系統字級，使用長條、方形、寬版、直式與展開配置。空間少時只放重要溫度／圖示／位置；逐時工具每列 1–6 筆，各有實際預報碼的圖示與雨率；大尺寸才加入氣象數值、鬧鐘、行程與同步時間。標示快取、過期或未知資料，未知天氣圖示使用中性雲，避免晴天誤導。預報日夜沿用有效資料的日出日落時間窗，跨日短預報可能有分鐘級誤差。

天氣圖案為原創 Android Canvas 圖層：漸層日月、立體雲、雨滴、雪、雷電及霧塵。位圖有大小與 LRU 記憶體上限，減少動態、省電及停用動畫會使用靜態圖案。一般 Android 桌面動畫由啟動器決定是否播放；不宣稱可以讀取桌布折射或維持高刷新率連續動畫。

指針鐘與數位鐘為桌面端的原生 AnalogClock／TextClock，持續走時不用 App 每分鐘喚醒；不顯示耗電的秒針，數位格式依手機 12／24 小時設定，日期亦由原生時鐘更新。點按數位或指針時鐘會直達 App 的天氣鬧鐘。

## 參考

- 小米官方 MIUI 11 手冊：自然鬧鐘保持統一風格、每天有變化的聲音設計方向。https://www.mi.com/tw/support/article/KA-17400/
- Apple Design great widgets：不同尺寸應逐步增加有用資訊及內容層次。https://developer.apple.com/videos/play/wwdc2020/10103/
- Apple Widgets HIG：https://developer.apple.com/design/human-interface-guidelines/widgets
- Android 實際尺寸與版型：https://developer.android.com/develop/ui/views/appwidgets/layouts
- TextClock 系統格式：https://developer.android.com/reference/android/widget/TextClock
- AnalogClock 仍支援 RemoteViews 桌面工具用途：https://developer.android.com/reference/android/widget/AnalogClock

## 驗證與交付

Robolectric SDK35 原生圖形測試直接組合 Glance、轉譯 RemoteViews 並用 Android Canvas 繪圖，量測各家族／尺寸／字級下文字是否超出卡片，且產生新工具預覽。新的 27 張桌面選擇器預覽為實際 RemoteViews 繪圖，不再使用舊圖；測試亦檢查卡片安全內距，避免字形在圓角處被遮住。這與手機啟動器實測分開記錄。

裝置畫面測試新增明亮／深色與 150% 字級的單選狀態、選中對比測試，既有滑桿透明、玻璃捕捉與天氣捲動回歸保留。編譯與已執行結果詳見更新包 VALIDATION.md；未執行的檢查不計為通過。

UPLOAD.sh 預設啟動候選建置，既有 GitHub 簽章與升級相容檢查保留。這份精簡來源更新包不包含原簽章 APK；安裝版本由既有 GitHub 流程產生。
