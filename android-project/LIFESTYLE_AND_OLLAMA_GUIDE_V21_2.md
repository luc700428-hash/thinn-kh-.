# thinn-khì V21.2 OTA 更新包操作說明

這是 **GitHub Actions 專案來源更新包**，不是已簽章 APK，也不能直接在 Android 安裝。**尚未在此環境完成完整 Android 編譯、簽署、Google Play 上架、實機測試或遠端 OTA 發布。**

## 套用到 GitHub

1. 在 repo `luc700428-hash/thinn-kh-.` 的 `main` 根目錄上傳 **完整原始碼 ZIP**：`thinn-khi_Android_Source_V21_2_Personalized_Lifestyle_OpenSourceAI.zip`，保持原始檔名與位元組完全相同。
2. 將 `thinn-khi-V21_2-build-apk.yml` 內容複製到 `.github/workflows/build-apk.yml`。它會對新版 ZIP 執行 SHA-256 白名單、單元測試與 Android build。
3. 確認 `APK_KEYSTORE_BASE64`、`APK_KEYSTORE_PASSWORD`、`APK_KEY_PASSWORD`、`APK_KEY_ALIAS` 四個 GitHub Secrets 已配置，且**與既有正式安裝版本使用完全相同的金鑰**。不要將任何密碼貼入程式或聊天。
4. 先以 `publish_release=false` 執行 Actions；查看編譯與 JUnit 結果。成功後再勾選 `publish_release=true`，**此舉才會**發出正式 GitHub Releases OTA。失敗不要發佈。
5. App 內更新中心須能取得新版 `v21.2.<run_number>`，下載並經 Android 使用者同意安裝，無法保證靜默更新。

## Termux 命令（上傳檔案和 workflow 後）

```sh
gh workflow run build-apk.yml --repo 'luc700428-hash/thinn-kh-.' --ref main -f publish_release=false
gh run list --repo 'luc700428-hash/thinn-kh-.' --workflow build-apk.yml --limit 3
# 先檢查成功，再執行：
gh workflow run build-apk.yml --repo 'luc700428-hash/thinn-kh-.' --ref main -f publish_release=true
```

## 新版智慧生活設定

- 天氣首頁：顯示推薦上衣／褲子／鞋／雨傘；可切換日常/步行/機車/戶外。
- 設定 →「智慧生活穿搭與開源 AI」：怕冷、一般、怕熱；日常休閒、通勤正式、運動機能；帶傘機率門檻 20%–90%。
- 開源 AI：預設關閉。自架 [Ollama](https://ollama.com/) 並執行 `ollama pull qwen2.5:1.5b`，經**受控 HTTPS** 代理提供相容 `/api/generate` 服務，App 才能可選連線。一般生活建議不需 Ollama。
- AI 輸出可能錯誤，只供生活參考，不代表中央氣象署防災資訊，也不屬於地震即時預警。

## 建置驗證

只驗證 ZIP 及工作流程完整性、Kotlin 本機規則的 8 項 JVM 情境。Android UI、Ollama 網路、FCM/全螢幕地震警報、政府資料授權、22 縣市城市建模仍需進一步開發和驗收。詳細請看 `thinn-khi-V21_2-五份藍圖實作稽核.md`。
