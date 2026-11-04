# 地震速報：所在地補算與首屏資訊

2026-11-04。這次修正 UI 與資料消費端，沒有更改官方公告、速報來源篩選、門檻或通知權限。

## 已確認的問題

公開歷史資料 `1150080` 第 4 報包含 `eq.time/lat/lon/depth/mag/loc/max` 和頂層發布時間，但沒有 `countyIntensities` 或 `countyArrivalTimes`。舊畫面只讀後兩者，因此即使收到震源參數也顯示空白。地圖排列在當地震度、抵達、規模、深度之前，使這些資訊在手機首屏以下。這個歷史案例只用於驗證資料格式，不能認定就是使用者遇到的那次地震。

## 新行為

- 當地震度、抵達、規模與深度優先於地圖；寬螢幕以獨立捲動的資訊和地圖兩欄呈現。
- 逐欄優先使用經驗證的來源縣市值。沒有來源值時，使用有效的手機定位或使用者明確選擇的縣市中心做本機估算，並分別標示來源。
- 開啟畫面後取得或變更所在地會在同一速報更新。無所在地時可直接選擇地區；不會默認為台北或用其他天氣城市冒充所在地。
- 倒數錨定發震時間；換報、旋轉、重新進入畫面不重設為「收到訊息之後 N 秒」。來源時間已過顯示「已抵達」；模型時間已過顯示「可能已抵達」。本機估算顯示「約 N s」。
- 沒有可靠發震時間、座標、深度或超出模型範圍時，保留未知並說明原因，不拿發布時間冒充發震時間、不用零表示缺值。取消、測試或過期訊息不啟用即時補算。
- 修正扁平 JSON 的頂層 `time` 被誤當發震時間；解析巢狀 `eq.max` 的來源 0–9 級距索引；接受明示的 5−/5+/6−/6+ 文字。
- 來源縣市圖層及通知門檻保持來源資料；本機補算不填入縣市來源表、不用來抑制警報。灰色明示為未提供，不能解讀為無震。
- 語音優先宣告防護動作；補算震度會明說「本機初步估算」。歷史／演練不啟動真實速報警報音。

## 估算方法與限制

### S 波走時

離線表由 ObsPy 1.5.1 TauP 對 IASP91 全球一維模型計算 `s/S/Sn` 中最早抵達的數值；僅隨 app 內附數值，沒有新增執行時依賴或複製第三方演算法程式碼。深度 0–700 km、震央距離 0–1200 km，表內雙線性插值，超界不外插。表格與獨立計算的 102 個 holdout 點隨原始碼保存；這些比對只驗證插值，不代表真實地震的走時誤差。

IASP91 不能反映台灣各地的三維速度差異或實際震源破裂。地圖上的固定速度波圈仍是示意，未拿來當倒數。系統時間錯誤、網路延遲、定位誤差仍會影響顯示；這次沒有宣稱成為 CWA 校準的官方預警服務。

重建：安裝開發用 `obspy==1.5.1`，於 android-project 執行 `python3 tools/generate_eew_travel_times.py`。

### 粗略震度

採公開研究的經驗關係 `PGA = 1.657 × exp(1.533 M) × R^-1.607`，R 為震源距離 km；場址係數取 1，最小 R 為 10 km。只在 M 2.0–7.5、深度 0–100 km、震央距離 0–600 km 使用，並明示未校準場址。它不是根據即時測站量測得到的觀測震度，也未針對個別事件驗證預報準確度。

依 PGA 0.8/2.5/8/25/80 gal 門檻只給 0–4 級；高 PGA 而沒有 PGV 時，顯示「4 級以上、細級待確認」。官方新制流程的 PGV 步驟仍可得到 4 級，因此不能直接說至少 5 弱。絕不把 M 或來源最大震度當成使用者所在地震度。此估算不抑制通知，也不意味有時間可以等待。

## 主要依據

- CWA，強震即時警報說明（接收端依震源參數、所在地計算）：https://scweb.cwa.gov.tw/zh-TW/Guidance/FAQdetail/100 ；https://scweb.cwa.gov.tw/zh-tw/pws/info_d/2
- CWA 介接軟體操作文件：https://eew-web.cwa.gov.tw/EW/Document/document.pdf
- CWA，新震度分級與 PGV：https://edu.cwa.gov.tw/PopularScience/ec/ec_21.html
- CWA，2019-12-18 新制新聞稿，註 2 步驟 8–9（PGV 決定高震度，最低回到 4 級）：https://www.cwa.gov.tw/Data/service/Newsbb/CH/1081218earthquakepress.pdf
- 內政部建築研究所，《智慧全人居家預警災防系統之研究》，附錄九，印刷頁 136 的 PGA 公式與附錄八場址效應表：https://ws.moi.gov.tw/001/Upload/404/relfile/9489/277499/ae0080fb-b526-4ab8-8d6c-cd9261f37839.pdf
- TauP，走時表與全球模型限制：https://www.seis.sc.edu/webtaup/doc/taup_table.html ；https://www.seis.sc.edu/webtaup/doc/calculations.html
- IASP91 數值模型：https://github.com/obspy/obspy/blob/master/obspy/taup/data/iasp91.tvel
- ExpTech 公開 wire 欄位：https://github.com/ExpTechTW/DPIP/blob/main/lib/features/earthquake/domain/eew.dart
- ExpTech 新舊級距索引語意：https://github.com/ExpTechTW/DPIP/blob/main/lib/shared/seismic/intensity.dart
- 公開歷史樣本（只作離線測試）：https://api.core-tyo1.exptech.dev/api/v2/eq/eew/1793521249

## 驗證範圍

單元測試覆蓋實際公開格式、來源優先、欠缺欄位、過期／取消／測試、位置有效性、報次更新、絕對到時與強震不假造精確級數。原生 Robolectric 畫面測試涵蓋明暗、100/150% 字體、手機、平板及開啟後定位恢復。完整回歸與發布建置證據另附更新包。

Android 鎖屏／背景是否可彈出仍受系統授權、通知設定及廠商省電限制影響，需實體裝置驗證；不能以本次畫面測試保證所有裝置都能強制彈出。
