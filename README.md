# 幻帳（Hmoney）— 專業記帳 App

幻帳是一款以 Kotlin + Jetpack Compose（Material 3）打造的專業記帳 App，
採用 2026 現代設計：**Material 3 Expressive × 玻璃態（Liquid Glass）**，
支援深色模式與 Material You 動態取色。

## ✨ 功能簡介

- **快速記帳**：支出／收入／轉帳三類型，數字鍵盤一鍵記錄，日期時間可回補，支援備註與多標籤。
- **多帳戶資金系統**：預設「現金＋1號銀行＋2號銀行」，各帳戶即時餘額、帳戶間轉帳（手續費計為支出）；有交易的帳戶僅能隱藏不能刪除。
- **統計報表**：日／週／月／年切換，支出分類占比環圈圖、收支趨勢長條圖、分類排行榜（含前期增減），可切換單一帳戶視角。
- **預算與週期交易**：月總預算＋分類預算，進度條 80% 轉警示色、100% 轉紅；固定收支（房租、訂閱、薪水）每週／每月自動入帳。
- **AI 分析**：OpenAI 相容 API（Mistral、自建 proxy 皆可），一鍵本週花費分析（總結／洞察／異常偵測／省錢建議）＋自由提問，結果存歷史可回看。
- **通知系統**：每日記帳提醒（精確鬧鐘、時間自訂）、預算警示、每週摘要、週期入帳通知，四個獨立渠道可個別關閉。
- **防後台終止**：內建「通知與續航設定精靈」，偵測 Samsung／小米／OPPO／vivo／華為／榮耀等廠牌並引導白名單設定。
- **備份**：每日自動本地備份（保留近 7 份）、手動匯出 CSV、完整 JSON 備份／還原。

## 📱 下載 APK

1. 打開本倉庫的 **[Releases](https://github.com/eric11yothelast-Hwanshen/my-new-app/releases)** 頁面。
2. 選擇最新版本（tag 形如 `v1.0.0`）。
3. 下載 `Hmoney-vX.Y.Z-release.apk`（建議）或 `Hmoney-vX.Y.Z-debug.apk`。
4. 在手機上開啟檔案安裝（需允許「安裝未知來源應用程式」）。

> 若 Releases 頁面異常，APK 也會備份在倉庫的 `output/` 資料夾，可直接從該處下載。
> 每次 push 到 main 的 CI 產物也可在 **Actions → 最新執行 → Artifacts** 下載。

**覆蓋升級**：CI 以 `GITHUB_RUN_NUMBER` 遞增 versionCode，新版直接覆蓋安裝即可保留資料。

## 🔑 Keystore 與簽章

- Release 簽章只走 **GitHub Secrets**（`KEYSTORE_BASE64`／`KEYSTORE_PASSWORD`／`KEY_ALIAS`／`KEY_PASSWORD`），金鑰檔案不入庫。
- CI 無 Secrets 時 fallback 產出 debug 簽章 APK，任何情況下建置不會中斷。

## 🛠 本機建置

```bash
git clone https://github.com/eric11yothelast-Hwanshen/my-new-app.git
cd my-new-app
./gradlew assembleDebug     # Debug APK：app/build/outputs/apk/debug/
./gradlew assembleRelease    # Release APK（R8 混淆＋簽章）
./gradlew testDebugUnitTest  # 單元測試
```

需求：JDK 17、Android SDK（compileSdk 37）。

## 🧰 技術棧

| 項目 | 內容 |
| --- | --- |
| 語言／UI | Kotlin + Jetpack Compose（Material 3） |
| 架構 | MVVM + Repository（domain / data / ui 分層） |
| 資料庫 | Room 2.8.5（WAL 模式，KSP） |
| 設定 | DataStore Preferences |
| 依賴注入 | Hilt 2.60.1 |
| 網路 | Retrofit 3.0.0 + OkHttp 5.1.0 |
| 排程 | WorkManager 2.11.1 + AlarmManager 精確鬧鐘 |
| 導航 | Navigation Compose |
| 玻璃態 | Haze 2.1.0（API 33+ 模糊、低版本半透明壓色） |
| 測試 | JUnit 4（餘額／統計／預算／週期排程核心路徑） |
| 建置 | AGP 9.5.0、Gradle 9.6、compileSdk 37、targetSdk 36、minSdk 26 |

## 📄 授權

[MIT License](LICENSE)
