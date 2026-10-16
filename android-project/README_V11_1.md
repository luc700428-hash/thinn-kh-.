# thinn-khì V11.1 — 原創品牌通知音「微光」

- 在 `res/raw/thinn_notify_chime.ogg` 加入 1.42 秒原創柔和和弦提示音。
- 中央氣象署單則特報、特報群組摘要、版本更新提醒、HyperOS 焦點測試全部使用新的通知通道。
- 新通道 id：`thinn_cwa_weather_chime_v1`、`thinn_release_updates_chime_v1`、`thinn_focus_demo_chime_v1`。
- 群組摘要避免重複響鈴，保留逐則警特報的提醒。
- 偏好設定新增「試聽通知音」與「發送測試通知」。
- 原本固定正式簽章、GitHub Releases OTA、液態玻璃、天氣資料、地標、Widget 均保留。
- 系統靜音、勿擾、音量、裝置通知授權以及個別通知通道設定仍由使用者與 Android 控制；不能強制有聲，也不能保證上焦點通知。
- 如未來加入 FCM，前景／資料訊息要通過既有 `WeatherNotificationDispatcher`，FCM `notification` 消息則需設定相同的 Android channel ID 才會使用品牌提示音。
