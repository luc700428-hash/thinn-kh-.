# 自然天氣環境音重混

本次重新混製 17 種前景天氣環境音。晴天、多雲及夜晚改採有授權來源的鳥鳴、樹葉與夜間蟲鳴錄音；雨天沿用專案已有的公眾領域雨聲錄音。霧、霾與雪天採較低明亮度的自然風葉背景，並不表示霧或雪本身會發出特定聲音。未變更鬧鐘旋律及地震警報音。

## 聲音處理

- 每段 24 秒，3 秒循環交疊，PCM16／44,100 Hz／單聲道，沿用已修好的 AudioTrack 循環播放器。
- 非雨天原本的人工鳥鳴及蟲鳴、雪天鈴聲已移除；使用自然錄音的變化與停頓。
- 每種天氣分別設定濾波與混音比例，抑制低頻轟聲和尖銳的高頻。RMS 目標為 −32 至 −25 dBFS，峰值低於 −10 dBFS；夜晚較輕，強雨較飽滿。
- 天氣切換保留約 1.1 秒的淡入淡出。「試聽」遵守使用者設定的環境音量，不再強行提高至 65%。
- 音量、音訊焦點、靜音與前景狀態仍由原本的播放器管理。

這是環境氛圍示意，錄音地點不等同使用者所在地；不能據此判斷現場風雨。量測與自動測試可以驗證音量、完整性、循環接點及播放狀態，不能替代使用者在實機上的聆聽偏好。

## 錄音來源與授權

下列作者頁明載 CC0 1.0；2026-10-31 查核。使用公開 MP3 預覽作為重混來源，並非原始無損 WAV。原始來源檔、作者、來源頁、下載 URL 與 SHA-256 均收錄於 `tools/audio_sources/SOURCES.json`。

| 用途 | 作者與來源 | 授權 |
| --- | --- | --- |
| 鳥鳴 | Magnesus：[Forest birds – ambient seamless loop](https://freesound.org/people/Magnesus/sounds/723913/) | CC0 1.0 |
| 風與樹葉 | Mjeno：[Autumn forest – leaves falling close to pond II](https://freesound.org/people/Mjeno/sounds/405134/) | CC0 1.0 |
| 夜間蟲鳴 | Defelozedd94：[Crickets At Night – Clean sound](https://freesound.org/people/Defelozedd94/sounds/522298/) | CC0 1.0 |

授權條款：[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)。雨聲及雷聲的作者與公眾領域來源見 [WEATHER_AUDIO_SOURCES.md](WEATHER_AUDIO_SOURCES.md)。

## 重建

在 `android-project` 執行 `python3 tools/remaster_nature_ambience.py`；需要 numpy、scipy 與 FFmpeg。輸入來源皆隨程式碼提供，不需登入或下載。一般 APK 建置直接使用已提交的 WAV，不會執行重混工具。舊的作曲工具保留作為鬧鐘的歷史來源，若手動執行舊工具重生環境音，應最後再執行本工具。

完整音訊清單為 `app/src/main/assets/next_weather/nature_ambience_manifest.json`。
