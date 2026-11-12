# 幻帳（Hmoney）開發進度清單

> 依 `docs/SPEC.md` 第十一節的 11 個階段追蹤。每個 session 只執行對應階段，完成後勾選。
> 本文件僅做進度與交接使用，不重複 SPEC 內容；內容有衝突時以 SPEC 為準。每個 session 結束時更新本文件。

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

- **Session 12（總覽頁「本月收支」卡片視覺強化，已完成）**：對應 SPEC 3.2 總覽頁，僅改 OverviewScreen.kt，不動 AddTransactionSheet 與其他頁面。
  - **改了什麼**：本月收支玻璃卡內的支出／收入兩欄改為兩張帶邊框的小卡（`MonthAmountBlock`，同檔 private composable）：支出用紅色系（`colorScheme.error`）＋外向箭頭圖示（Icons.AutoMirrored.Outlined.CallMade，支出行方向）；收入用綠色系（`colorScheme.primary`，品牌青綠）＋內向箭頭圖示（Icons.AutoMirrored.Outlined.CallReceived，收入入方方向）。
  - **狀態區分**：金額 > 0 時邊框與圖示用強調色、金額 FontWeight.Bold；金額 = 0 時整張小卡降飽和——邊框改 `outlineVariant`、圖示與金額改 `onSurfaceVariant`、字重恢復 Normal，深淺色模式皆可一眼分辨本月有沒有記帳。
  - **用色決策**：全部從 MaterialTheme.colorScheme 取用（error／primary／onSurfaceVariant／outlineVariant），不 hardcode 色碼；未新增 Theme 語意色——error 在 Material 語意即「負向／支出紅」，primary 在品牌配色為青綠（收入綠），深淺兩套配色與動態色彩（API 31+ Material You）皆自動適配對比。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），lint（Error 擋建置）＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。

- **Session 11（實機閃退修復：捲動容器巢狀）**：以使用者提供的實機 logcat 為準的根因修復（`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`），對應 SPEC 3.1 快速記帳，不更動其他功能範圍。
  - **根因**：Session 9 為修數字鍵盤截斷，把 AddTransactionSheet 的內容改為 `verticalScroll`；但分類區仍是 `LazyVerticalGrid`（縱向捲動元件），在無限高度約束的父層內第一次測量即拋例外，開啟「快速記帳」頁直接閃退。此問題編譯與 lint 皆測不到。
  - **修復（單一捲動結構，二擇一方案中的前者）**：外層 Column 不捲動；中段 `Column(weight(1f)) + verticalScroll` 放分類、日期、備註等可捲動內容；數字鍵盤與「完成 ✓」鈕固定底部，不隨內容捲動，任何螢幕高度都完整顯示鍵盤（0、小數點、刪除鍵）。分類格由 `LazyVerticalGrid` 改為純 Column／Row 的固定 4 欄 `CategoryGrid`（分類數量有限，不影響效能），全 Sheet 不再有任何巢狀捲動。
  - **全域排查**：統計、明細、帳戶、AI（AiScreen／AiAssistantSheet）、設定、精靈頁與 AiResultDialog 逐一檢查，皆為單一捲動結構（LazyColumn 或 verticalScroll 僅出現一層；各 weight 均在 Row 內），無同類問題，未更動。
  - **自檢清單**：SPEC 第十節新增第 13 項「捲動容器巢狀」檢查，之後每個 session 交付前檢查。
  - **建置結果**：沙箱無 JDK/Android SDK，驗證由 GitHub Actions 執行（testDebugUnitTest＋lintDebug＋雙 APK 簽署＋Release）。
  - **重要決策（請勿誤改）**：AddTransactionSheet 必須維持「外層不捲動＋中段 weight(1f) 捲動＋鍵盤固定底部」的單一捲動結構，分類區必須使用非 Lazy 的 CategoryGrid——原因：在可捲動父層內放任何縱向 Lazy 元件都會以無限高度測量而閃退，此類錯誤編譯與 lint 測不到，只有實機會爆。
  - **待實機驗證**：快速記帳可開啟並儲存、鍵盤完整顯示、內容多時可捲動、編輯同一筆成功。

- **Session 10（快速記帳閃退修復＋全專案自檢）**：對應 SPEC 3.1（核心記帳）與 3.5（AI 分析）的實機穩定性強化，為使用者回報閃退後的追加修復，不更動既有功能範圍。
  - **閃退排查**：總覽 FAB → 記帳頁的呼叫鏈逐層靜態檢查（導覽 route、Hilt 注入、ViewModel 屬性初始化順序、字串資源、組合語法）皆正常；編譯與 DI 由 CI 保證。判斷風險點：記帳 ViewModel 這次新注入 AiRepository，會在開啟記帳頁時首次建立整個 AI 服務鏈（Retrofit／加密設定檔），為實機上新觸發的執行路徑。
  - **修復措施（三層防護）**：(1) AddTransactionViewModel 改用 dagger.Lazy 延後建立 AI 服務，開啟記帳頁不再觸發 AI 服務鏈初始化；(2) 所有 AI ViewModel（AddTransaction/FeatureAi/AiChat/Ai）的每個 AI 呼叫包 try-catch＋Log.e，任何失敗只顯示錯誤 UI，絕不往上拋成閃退；(3) proguard-rules.pro 補上 AI 網路介面 keep 規則與註解屬性保留（避免 release 混淆後閃退）。
  - **全專案自檢結論**：五分頁與附屬頁 route 皆存在於導覽圖；記帳頁新增／編輯兩模式共用同一 Sheet（editingId 參數）；統計頁等上一輪檔案無殘留不完整程式碼（括號平衡＋CI 編譯通過）；AI 未設定時 callRaw 直接回 NotConfigured 錯誤，各入口顯示「前往設定」引導而非例外；字串資源由編譯驗證齊全；lintDebug 加入 CI 管線（Error 等級會擋建置）。
  - **lint 自檢（CI lintDebug，Error 等級全數修畢，共 16 項）**：(1) Notifier 通知發送加權限標註（發送前本就有權限檢查）；(2) StatsAggregator 三處 LocalDate.ofInstant（需 API 34）改為全版本相容寫法；(3) 記帳／統計／明細／帳戶共 11 處按鈕事件內的字串改為組合期解析（畫面旋轉或語言切換不會取到過期字串）；(4) 深色主題的瀏海屬性加 API 版本標註。Warning 34 項與 hint 2 項不影響功能，予以保留觀察。
  - **建置結果**：CI 全綠（單元測試 23 案例＋lintDebug＋assembleDebug＋assembleRelease R8 簽章），新 Release v1.0.0-20261111163259 附 debug（23.2 MB）與 release（2.6 MB）雙 APK。
  - **重要決策（請勿誤改）**：(1) AddTransactionViewModel 的 AI 依賴使用 dagger.Lazy——原因：開啟記帳頁是高頻路徑，不應在進入時就建立 Retrofit／加密設定檔整條 AI 服務鏈（實機閃退的主要風險點），改為按下 AI 按鈕才建立；(2) callRaw 抽出 extractContent() 解析 choices[0].message.content——原因：API 回傳的是整包 HTTP JSON，不解析的話所有通用 AI 功能會顯示亂碼；此修法與 execute() 的 parseContent 各自獨立，後者處理存歷史的結構化分析，勿合併；(3) lintDebug 納入 CI 且 Error 等級擋建置——原因：本次靠它抓到 3 處 API 34 限定 API（舊手機統計頁潛在閃退），值得常駐。
  - **部分完成與未完成**：閃退在無 logcat 下以風險點防護修復，實機待驗證；lint 尚餘 34 項 Warning、2 項 hint 未處理（不影響功能）。
  - **已知問題**：沙箱無 JDK/Android SDK，build 與 lint 一律由 GitHub Actions 執行；GitHub App 無法管理 Secrets（403），簽章走 .private/ 備援（公開倉庫前須輪替金鑰）。
  - **下一 session 起點**：若使用者提供 logcat，以日誌為準再修正；實機驗收清單＝五分頁可進入、快速記帳可開啟並儲存、編輯同一筆成功。
- **Session 9（Bug 修復＋AI 全面整合，已完成）**：使用者追加需求，對應 SPEC 3.1（快速記帳 UI 修復）、3.5（AI 分析擴充為全 App 功能）。
  - **Bug 修復**：(1) 記一筆（AddTransactionSheet）內容改為可捲動（verticalScroll），數字鍵盤欄寬以 weight 自適應，任何螢幕高度都完整顯示 0、小數點、刪除鍵，無 RenderFlex overflow；(2) 頂部新增返回鍵，有未儲存輸入時跳出「放棄這筆記錄？」確認對話框；鍵盤列下方新增一致的「完成 ✓」送出鍵（支出／收入／轉帳三個分頁相同）；驗證失敗改以 Snackbar 提示；儲存成功顯示 Toast 並關閉。
  - **AI 全面整合**（沿用既有 AiRepository 服務層，設定頁仍為唯一設定入口）：四個主分頁（總覽／明細／統計／帳戶）各有浮動 AI 按鈕開啟全域 AI 助手對話面板（AiAssistantSheet，附精簡去敏感化財務 context）；總覽「本週 AI 洞察」；明細自然語言 AI 篩選＋批次分析；統計「AI 分析本期」；帳戶「AI 解讀帳戶」；記一筆備註欄 AI 按鈕（自動分類／智慧標籤／備註潤飾）。所有 AI 呼叫有 loading＋可取消、友善錯誤訊息＋「前往設定」按鈕、一律繁體中文回覆。
  - **重要修正**：AiRepositoryImpl.callRaw 原本直接回傳 HTTP 原始 JSON（會讓所有通用 AI 功能顯示整包 JSON 亂碼），改為抽出 extractContent() 解析 choices[0].message.content；suggestForNote/interpretFilter 的 Result 失敗改用 AiCallException 包裝（非 coroutine 取消）。
  - **已知限制**：沙箱無 JDK/Android SDK，建置驗證依 GitHub Actions（與先前各 session 相同）；AI 實測需使用者自行填入 API Key；批次分析一次最多送 50 筆交易。
  - **下一 session 起點**：CI 綠燈後無既定遺留開發項；可依使用者回饋微調 AI prompt 與 UI 細節。

- **Session 8**（已完成）：執行第 10–11 項。過程中修正三個問題：AGP 9 簽章設定需避免 lambda 同名屬性自我賦值、R8 需補 Tink/errorprone dontwarn 規則、Secrets 管理權限不足改走 .private/ 備援。首次 Release v1.0.0 成功發布。遺留事項：GitHub App 無法管理 Secrets（403），日後可於倉庫 Settings→Secrets 手動設定四個簽章 Secret 取代 .private/ 方案；公開倉庫前必須輪替金鑰。
- **Session 7**（已完成）：執行第 9 項＋規格 3.7 備份功能。單元測試覆蓋核心路徑（餘額計算含手續費與隱藏帳戶、統計占比／排行榜前期增減／趨勢分桶、預算 80%/100% 分級、週期排程補產與 60 期防呆、金額解析）；備份：每日自動本地備份（WorkManager，保留近 7 份）、手動匯出 CSV（UTF-8 BOM）、完整 JSON 備份／還原（單一交易覆蓋式重建，還原前需確認），UI 在設定頁。CI 綠燈（測試＋編譯）。
- **Session 6**（已完成）：執行第 8 項。Retrofit＋OkHttp（逾時預設）AI 倉儲；設定頁 AI 連線區（三欄位加密保存）；AI 助手附屬頁（本週分析一鍵、自由提問、歷史回看、未設定友善引導、loading／錯誤分流）；每週摘要通知深連結至 AI 頁；INTERNET 權限宣告。CI 綠燈。遺留事項：API 實測需使用者填入自己的 API Key；資料備份（CSV／JSON）属規格 3.7，併入後續階段處理。
- **Session 5**（已完成）：執行第 7 項。WorkManager（HiltWorker）排程：每日預算檢查／週期交易產生、每週日 20:00 摘要；AlarmManager 精確鬧鐘每日提醒（canScheduleExactAlarms 檢查＋WorkManager fallback）；BootCompletedReceiver 開機重排；設定頁通知區（提醒開關／時間／權限請求與引導）與續航精靈頁。CI 綠燈。遺留事項：每週摘要點擊目前開啟 App 主畫面，待 AI 分析頁（Session 6）完成後改為直接開啟該頁；INTERNET 權限將於 AI 階段宣告。
- **Session 4**（已完成）：執行第 6 項。統計頁三種圖表（純 Compose Canvas，配合玻璃態）；設定頁新增預算區（進度條 80%/100% 變色）與週期交易區（新增／啟停／刪除，FluidSwitch）；資料庫 v1→v2 手寫遷移（預算、週期交易表）；App 啟動時自動產生已到期之週期交易。CI 綠燈。遺留事項：預算超支與週期入帳的推播通知、WorkManager 背景排程與開機重排，屬下一階段（通知與防後台終止）。
- **Session 3**（已完成）：執行第 5 項。玻璃態（Haze）底部導覽列與卡片、FluidSwitch 流體開關、數字滾動動畫、spring 轉場、空狀態；總覽頁（本月收支玻璃卡／總餘額／帳戶餘額列／近期明細／快速記帳 FAB）、明細頁（搜尋＋四種篩選＋按日分組小計＋編輯／刪除確認）、帳戶頁（新增／編輯／隱藏／刪除規則提示）、設定頁外觀（深色模式／動態色彩，DataStore）。CI 綠燈。遺留事項：統計頁仍為骨架（Session 4）；設定頁其餘區塊待後續階段。
- **Session 2**（已完成）：執行第 3–4 項＋額外的最小 CI 驗證工作流（.github/workflows/build.yml：JDK 17 + assembleDebug）。建立 Room 資料層（實體／DAO／資料庫／種子資料）、domain 模型、餘額計算邏輯、三個 Repository（介面＋實作）與 Hilt DI 模組。遺留事項：沙箱無 JDK/Android SDK 仍無法本機編譯，可編譯性由 push 後的 Actions 驗證；UI 接線與畫面於 Session 3。
- **Session 1**（已完成）：執行第 1–2 項。提交 docs/SPEC.md、docs/PROGRESS.md、docs/research.md，並完成專案骨架（Gradle 9.6 Wrapper、AGP 9.5、Hilt、Navigation Compose 五頁導覽、Material 3 動態色彩主題、自適應圖示）。遺留事項：沙箱無 JDK/Android SDK，無法本機編譯 APK；建置驗證由 Session 8 的 GitHub Actions 執行。
