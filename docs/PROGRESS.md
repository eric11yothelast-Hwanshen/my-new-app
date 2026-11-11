# 幻帳（Hmoney）開發進度清單

> 依 `docs/SPEC.md` 第十一節的 11 個階段追蹤。每個 session 只執行對應階段，完成後勾選。

| # | 階段 | 狀態 | 完成日期 | 備註 |
| --- | --- | --- | --- | --- |
| 1 | 研究階段 → docs/research.md | ✅ 已完成 | 2026-11-11 | 固定 AGP 9.5.0 / Gradle 9.6.0 / Compose BOM 2026.09.00 / targetSdk 36；玻璃態選定 Haze 2.1.0 |
| 2 | 專案骨架（Gradle、manifest、Hilt、導覽、主題系統） | ✅ 已完成 | 2026-11-11 | Wrapper 四件套齊全；五頁底部導覽＋動態色彩主題；權限暫無（後續階段依功能加入） |
| 3 | 資料層（Room 實體與 DAO、Repository） | ✅ 已完成 | 2026-11-11 | Room 2.8.5（KSP）＋WAL 模式；預設帳戶（現金、1號銀行、2號銀行）與内建分類種子；DAO 支援關鍵字搜尋與期間／帳戶／分類／類型篩選 |
| 4 | 核心功能（記帳 CRUD、多帳戶與轉帳） | ✅ 已完成 | 2026-11-11 | 金額以「分」為單位（不可為負）；餘額 = 期初＋收入−支出±轉帳，手續費計為轉出帳戶支出；有交易的帳戶僅能隱藏不得實刪；轉帳驗證兩帳戶不可相同。UI 畫面於 Session 3 接上 |
| 5 | UI 全面實作（含玻璃態與動效） | ✅ 已完成 | 2026-11-11 | Haze 2.1.0 玻璃態（API 33+ 模糊、低版半透明壓色）；流體切換開關；數字滾動、spring 轉場；總覽／明細／帳戶／設定＋記帳 Bottom Sheet（數字鍵盤）；深淺模式與動態色彩設定 |
| 6 | 統計、預算、週期交易 | ✅ 已完成 | 2026-11-11 | 統計：日／週／月／年＋帳戶視角切換、環圈占比、趨勢長條、Top10 排行（含前期增減）；預算：月總＋分類預算、80% 警示色／100% 紅；週期交易：每週／每月規則、啟動時自動補產到期記錄（DB v2 遷移） |
| 7 | 通知與防後台終止（含設定精靈） | ✅ 已完成 | 2026-11-11 | 四獨立渠道；每日提醒精確鬧鐘（被收回時 fallback WorkManager）；預算 80%/100% 同月僅推一次；每週日 20:00 摘要；週期入帳通知；POST_NOTIFICATIONS 動態請求＋被拒引導；開機重排（RECEIVE_BOOT_COMPLETED）；精靈頁偵測 Samsung/Xiaomi/OPPO/vivo/Huawei/Honor/Google 並跳轉廠牌設定＋電池最佳化請求＋dontkillmyapp 連結 |
| 8 | AI 分析串接 | ✅ 已完成 | 2026-11-11 | OpenAI 相容 API（Base URL／Key／模型自填，EncryptedSharedPreferences 加密保存）；本週分析（分類合計／與上週增減／前三大支出 → 總結＋洞察＋異常＋建議）；自由提問（附本週／本月彙總）；歷史回看（DB v3）；未設定／無網路／401／429／HTTP／解析錯誤各有明確回饋；每週摘要通知點擊直接開啟 AI 頁 |
| 9 | 單元測試與修 bug | ✅ 已完成 | 2026-11-11 | 餘額計算／統計彙總／預算判斷／週期排程／金額解析共 5 套測試（23 案例）；預算與統計邏輯抽成純邏輯類別（BudgetEvaluator／StatsAggregator）；CI 加入 testDebugUnitTest 步驟 |
| 10 | CI/CD、簽章、首次 Release、README | ✅ 已完成 | 2026-11-11 | 完整管線：測試→Debug→Release（R8＋簽章）→產物命名 Hmoney-vX.Y.Z→GitHub Release；versionCode 以 GITHUB_RUN_NUMBER 遞增；簽章雙保險：gh secret set 因 GitHub App 權限不足失敗（403），keystore 與密碼已 commit 至 .private/（.gitignore 例外）；CI 讀取順序 Secrets→.private/→debug 簽章 fallback；README 含下載教學與金鑰輪替警告 |
| 11 | 推送至 main，確認 Actions 綠燈與 Release 頁面可下載 APK | ✅ 已完成 | 2026-11-11 | Actions 綠燈（測試＋雙 APK）；GitHub Release v1.0.0 已建立，附 Hmoney-v1.0.0-debug.apk 與 Hmoney-v1.0.0-release.apk，Releases 頁可直接下載 |

## Session 紀錄

- **Session 8**（已完成）：執行第 10–11 項。過程中修正三個問題：AGP 9 簽章設定需避免 lambda 同名屬性自我賦值、R8 需補 Tink/errorprone dontwarn 規則、Secrets 管理權限不足改走 .private/ 備援。首次 Release v1.0.0 成功發布。遺留事項：GitHub App 無法管理 Secrets（403），日後可於倉庫 Settings→Secrets 手動設定四個簽章 Secret 取代 .private/ 方案；公開倉庫前必須輪替金鑰。
- **Session 7**（已完成）：執行第 9 項＋規格 3.7 備份功能。單元測試覆蓋核心路徑（餘額計算含手續費與隱藏帳戶、統計占比／排行榜前期增減／趨勢分桶、預算 80%/100% 分級、週期排程補產與 60 期防呆、金額解析）；備份：每日自動本地備份（WorkManager，保留近 7 份）、手動匯出 CSV（UTF-8 BOM）、完整 JSON 備份／還原（單一交易覆蓋式重建，還原前需確認），UI 在設定頁。CI 綠燈（測試＋編譯）。
- **Session 6**（已完成）：執行第 8 項。Retrofit＋OkHttp（逾時預設）AI 倉儲；設定頁 AI 連線區（三欄位加密保存）；AI 助手附屬頁（本週分析一鍵、自由提問、歷史回看、未設定友善引導、loading／錯誤分流）；每週摘要通知深連結至 AI 頁；INTERNET 權限宣告。CI 綠燈。遺留事項：API 實測需使用者填入自己的 API Key；資料備份（CSV／JSON）属規格 3.7，併入後續階段處理。
- **Session 5**（已完成）：執行第 7 項。WorkManager（HiltWorker）排程：每日預算檢查／週期交易產生、每週日 20:00 摘要；AlarmManager 精確鬧鐘每日提醒（canScheduleExactAlarms 檢查＋WorkManager fallback）；BootCompletedReceiver 開機重排；設定頁通知區（提醒開關／時間／權限請求與引導）與續航精靈頁。CI 綠燈。遺留事項：每週摘要點擊目前開啟 App 主畫面，待 AI 分析頁（Session 6）完成後改為直接開啟該頁；INTERNET 權限將於 AI 階段宣告。
- **Session 4**（已完成）：執行第 6 項。統計頁三種圖表（純 Compose Canvas，配合玻璃態）；設定頁新增預算區（進度條 80%/100% 變色）與週期交易區（新增／啟停／刪除，FluidSwitch）；資料庫 v1→v2 手寫遷移（預算、週期交易表）；App 啟動時自動產生已到期之週期交易。CI 綠燈。遺留事項：預算超支與週期入帳的推播通知、WorkManager 背景排程與開機重排，屬下一階段（通知與防後台終止）。
- **Session 3**（已完成）：執行第 5 項。玻璃態（Haze）底部導覽列與卡片、FluidSwitch 流體開關、數字滾動動畫、spring 轉場、空狀態；總覽頁（本月收支玻璃卡／總餘額／帳戶餘額列／近期明細／快速記帳 FAB）、明細頁（搜尋＋四種篩選＋按日分組小計＋編輯／刪除確認）、帳戶頁（新增／編輯／隱藏／刪除規則提示）、設定頁外觀（深色模式／動態色彩，DataStore）。CI 綠燈。遺留事項：統計頁仍為骨架（Session 4）；設定頁其餘區塊待後續階段。
- **Session 2**（已完成）：執行第 3–4 項＋額外的最小 CI 驗證工作流（.github/workflows/build.yml：JDK 17 + assembleDebug）。建立 Room 資料層（實體／DAO／資料庫／種子資料）、domain 模型、餘額計算邏輯、三個 Repository（介面＋實作）與 Hilt DI 模組。遺留事項：沙箱無 JDK/Android SDK 仍無法本機編譯，可編譯性由 push 後的 Actions 驗證；UI 接線與畫面於 Session 3。
- **Session 1**（已完成）：執行第 1–2 項。提交 docs/SPEC.md、docs/PROGRESS.md、docs/research.md，並完成專案骨架（Gradle 9.6 Wrapper、AGP 9.5、Hilt、Navigation Compose 五頁導覽、Material 3 動態色彩主題、自適應圖示）。遺留事項：沙箱無 JDK/Android SDK，無法本機編譯 APK；建置驗證由 Session 8 的 GitHub Actions 執行。
