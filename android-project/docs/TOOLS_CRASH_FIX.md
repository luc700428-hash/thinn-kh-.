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
