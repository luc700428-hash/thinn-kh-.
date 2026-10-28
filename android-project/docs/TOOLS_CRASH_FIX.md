# 工具頁第四分類閃退修正

GitHub Actions 第 303 次建置（commit `7cd331ebd97cc982c8f56b3bd7267688c0b0a8ee`）已完成 APK 編譯，在 7 項 Android 介面測試中有 1 項失敗。

失敗紀錄：
https://github.com/luc700428-hash/thinn-kh-./actions/runs/40614044833/job/122572242760

`weatherScrollAndRepeatedSettingsNavigationKeepVisibleText` 的堆疊指出 `GlassNavigation.kt:188`：工具頁新增「海洋與天文」後共有 4 個分類，卻仍用 `groupIndex` 讀取只有 3 筆的圖示色彩清單，造成 `ArrayIndexOutOfBoundsException: length=3; index=3`。Pager 在分頁過場中預先組合工具頁，因此點其他分頁也會觸發；這是實際 App 錯誤。

修正把標題、圖示底色與工具項目一起放入 `EntryGroup`，顯示與搜尋直接使用該分類的底色，新增、過濾或重排分類都不依賴另一份清單的位置。

新增驗證：

- `ToolsPageRenderingTest` 在本機實際組合與繪製完整工具頁，確認明亮／深色都能顯示全部四組圖示。
- `allToolsGroupsAndFilteredLastGroupRenderInBothAppearances` 加入 Android 介面測試，確認四組分類、搜尋「世界時鐘」以及清除搜尋後恢復分類，在明暗模式下均可操作。
- 原來失敗的 Dock／設定／天氣快速切換測試保留，CI 介面測試數量從 7 增加到 8；結果驗證清單也同步，必須八項全部完成且通過。

本機執行結果、原始失敗堆疊與編譯紀錄會放在修正包的 `validation` 資料夾。新的遠端模擬器結果，以套用修正後的 GitHub Actions 紀錄為準。

## 第 304 次建置：搜尋測試的重複文字定位

第 304 次建置（commit `04a1c0f83b57ac31c909da4354e8f9f336dca552`）的原有 7 項 Android 介面測試全部通過，包含先前觸發實際閃退的 `weatherScrollAndRepeatedSettingsNavigationKeepVisibleText`。

失敗紀錄：
https://github.com/luc700428-hash/thinn-kh-./actions/runs/40621204148/job/122595415974

新增的第 8 項 `allToolsGroupsAndFilteredLastGroupRenderInBothAppearances` 在輸入「世界時鐘」後，使用 `onNodeWithText("世界時鐘")`，同時匹配到搜尋輸入框與工具結果，因「Expected at most 1 node but found 2 nodes」失敗。報告沒有 App 閃退；這是測試定位錯誤。

現在分別驗證輸入框內容，以及具有點擊操作、沒有文字編輯操作的唯一搜尋結果。測試也會實際開啟世界時鐘、確認臺北與東京、關閉抽屜並清除搜尋，再驗證分類恢復。明亮、深色模式都走完此流程，8 項測試與嚴格的 CI 結果檢查均保留。

本機補充驗證在 Robolectric 原生 Android API 35 環境直接組合真實工具頁，確認舊文字定位實際會匹配兩個節點，而修正後同一個 matcher 只匹配一個可操作結果；再透過元件的語意操作開關世界時鐘、檢查城市內容、清除搜尋，明暗模式均通過。此驗證簡化動畫並採直接元件操作，未代替實際 Android 裝置的捲動、鍵盤與時序測試。兩份 APK 已編譯成功；完整 8 項遠端介面測試須在新一輪建置執行。
