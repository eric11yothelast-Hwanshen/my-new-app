# 幻帳（Hmoney）專業記帳 App — 總規格書

> 使用協議：本文件是總規格書，不是單次提示詞。對應整個專案約 8 個 session 的工作量，禁止任何單一 session 一次執行完所有階段。每個 session 依「第十二節：分 Session 執行協議」只執行 1–2 個階段。

## 〇、角色與總綱

你是一名資深 Android 工程師。你的任務是在 GitHub 倉庫 `eric11yothelast-Hwanshen/my-new-app` 從零開發一款專業級記帳 App：幻帳（Hmoney），並交付可直接安裝測試的 APK。

最高原則：

1. 動工前必須完成「第一節：研究階段」，不得憑空想像任何技術細節，尤其 UI 設計。
2. 交付的每一個檔案都是可編譯、可執行的成品，禁止任何 placeholder、TODO、假資料。
3. 最終必須能透過 GitHub 直接下載 APK（詳見第七、八節）。
4. 本規格以「分 Session 推進」方式執行：每個 session 只負責第十一節中的一至兩個階段（協議詳見第十二節）。單一 session 硬跑全部只會導致後半段交差與偷渡 placeholder——禁止。

## 一、研究階段（動工前必做，不得跳過）

在撰寫任何程式碼之前，必須先連網搜尋並確認以下技術的最新現況（2026 年），並將研究結論記錄在 `docs/research.md`：

1. **UI 設計**：搜尋「Android UI design trends 2026」「Material 3 Expressive」「Liquid Glass / glassmorphism Android」。確認 2026 主流風格 = Google Material 3 Expressive（spring 物理動畫、大圓角、動態色彩）＋玻璃態視覺。官方部落格確認 Material 已 Compose-first（m3.material.io/blog/material-is-compose-first）。
2. **玻璃態實作方案**：評估並擇一（或組合）：chrisbanes/haze（Compose 背景模糊）、Mortd3kay/liquid-glass-android（AGSL shader，Android 13+）、skydoves/Cloudy。必須規劃低版本 API 的降級策略（半透明壓色），不得在舊機崩潰。
3. **防後台終止**：研讀 dontkillmyapp.com 各廠牌（Samsung、Xiaomi、OPPO、vivo、Huawei、Honor）的官方教學與常見 Intent 清單，以及 WorkManager、精確鬧鐘（exact alarm）的現行最佳實踐。
4. **版本確認**：查詢並固定專案所需版本——最新穩定的 Kotlin、AGP、Gradle、Compose BOM、Material3；以及 2026 年現行的 targetSdk 規範。將選定版本明確寫入 `docs/research.md`，之後所有 Gradle 設定必須與之一致，不得使用過時或猜測的版本號。
5. **通知與排程 API 現況**：確認 POST_NOTIFICATIONS（API 33+ 動態請求）、SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM 的現行規範與使用者引導做法。

## 二、專案基本資訊

| 項目 | 值 |
| --- | --- |
| App 名稱 | 幻帳（Hmoney） |
| 套件名稱 | com.hmoney.app |
| 語言 / UI | Kotlin + Jetpack Compose（Material 3） |
| 最低支援 | minSdk 26（Android 8.0），毛玻璃等特效在 API 33+ 完整呈現、以下自動降級 |
| 目標版本 | targetSdk / compileSdk 依研究階段結論取最新穩定 |
| 預設語言 | 繁體中文（values/strings.xml 全部字串，禁止任何 hardcode 文字） |
| 版本 | versionName 從 1.0.0 起 |

App 圖示：製作 adaptive icon（前景＋背景 vector drawable），以「錢幣＋抽象帳本」為視覺方向，現代簡潔。

## 三、功能規格

### 3.1 核心記帳

- 交易三類型：支出、收入、轉帳（帳戶間移動，不計入收支統計）。
- 每筆記錄必含：金額（不可為負、兩位小數內）、分類、帳戶、日期時間（可修改，允許補記過去）、備註（選填）、標籤（選填、可多個）。
- 分類：收入／支出分類獨立管理，內建常用分類（餐飲、交通、購物、娛樂、居住、醫療、薪資、投資等），可新增／編輯／隱藏，每個分類有顏色與 icon。
- 快速記帳：首頁 FAB 一鍵開啟記帳 Bottom Sheet，數字鍵盤為主的輸入流程，記完即存。
- 明細列表：按日分組顯示、當日小計，支援搜尋（關鍵字）與篩選（期間、帳戶、分類、類型），單筆可編輯／刪除（刪除需確認對話框）。

### 3.2 多帳戶資金系統（重點功能）

- 預設建立帳戶：「現金」，並預設建立「1號銀行」「2號銀行」兩個範本帳戶（名稱可改）。
- 帳戶可無限新增，每個帳戶有：名稱、icon／顏色、期初餘額、幣別（預設 TWD，可不支援多幣別換算但需標示）。
- 各帳戶即時餘額計算 = 期初餘額 + 收入 − 支出 ± 轉帳，餘額顯示於帳戶頁與總覽。
- 帳戶間轉帳獨立交易類型，選轉出／轉入帳戶，可備註手續費（手續費計為支出）。
- 刪除帳戶：有交易記錄的帳戶僅能「隱藏」不得實刪，避免資料孤立；無交易者可刪。

### 3.3 統計與報表

- 時間維度切換：日／週／月／年。
- 圖表：支出分類占比（圓餅／環圈）、收支趨勢（長條或折線）、分類支出排行榜（Top 10，含年比增減）。
- 圖表以 Compose 寫成（或研究後選用成熟 Compose 圖表庫），風格須配合整體玻璃態設計。
- 全部統計可切換「單一帳戶／全部帳戶」視角。

### 3.4 預算與週期交易

- 月總預算＋各分類預算；預算進度條（80% 變警示色、100% 轉紅），超支時推通知。
- 週期交易：固定支出／收入（房租、訂閱、薪水）可設每週／每月重複，到期由排程自動產生記錄，產生時推通知告知。

### 3.5 AI 分析（API 串接）

- 設定頁提供 OpenAI 相容 API 連線設定：Base URL、API Key、Model 名稱皆可自填（使用者可能使用 Mistral API 或自建 proxy），存入 EncryptedSharedPreferences，絕不寫入 Room 或明文檔。
- 「本週花費分析」：一鍵將本週結構化資料（分類合計、相較上週增減、前三大支出）組成 prompt 傳給 API，回傳：總結、分類洞察、異常支出偵測、具體省錢建議。分析結果存歷史可回看。
- 支援自由提問輸入框（把使用者問題＋本週／本月彙總資料一起送出）。
- 錯誤處理必須完整：未設定 API、無網路、HTTP 錯誤、額度用盡，各自有明確 UI 回饋，不得閃退。
- 呼叫一律在 ViewModel / repository 層執行，遵守 Clean Architecture 分層。

### 3.6 通知系統

- 每日記帳提醒：時間可自訂，精確排程（exact alarm）。
- 預算警示：月預算或分類預算達 80%／100% 時各推一次（同週期不重複轟炸）。
- 每週摘要通知：固定時間推播本週總支出與最大分類，點擊直接開啟 AI 分析頁。
- 週期交易入帳通知。
- 全部通知走獨立 channel（可個別關閉），實作 POST_NOTIFICATIONS 動態請求與「通知被拒時的引導畫面」。

### 3.7 資料安全與備份

- Room 資料庫啟用 WAL 模式。
- 每日自動本地備份（保留近 7 份），手動匯出 CSV、完整 JSON 備份／還原。
- 資料庫加密為選配（研究 SQLCipher 成本，若影響過大可記錄於 research.md 說明取捨）。

## 四、UI／UX 設計要求（2026 現代科技風格）

- **整體風格定調**：Material 3 Expressive 骨架 × 玻璃態（Liquid Glass / glassmorphism）質感。
- **毛玻璃／流體玻璃**：底部導覽列、Bottom Sheet、頂端資訊卡採用背景模糊＋高光描邊＋細微噪點質感；使用研究階段選定的函式庫（Haze / liquid-glass-android / Cloudy），API 33+ 完整效果、以下降級為半透明壓色。先搜尋實作教學再寫，不得憑記憶亂寫 API。
- **流體切換開關**：設定頁所有 Switch 重新設計——spring 物理動畫、thumb 按壓變形（squish）、拖曳超過閾值才切換的流體手感，做不出來就用研究找到的開源現成方案，不得交出陽春預設開關。
- **動態色彩與深色模式**：完整支援 Material You 動態取色（API 31+）與自訂主題色；深色模式全畫面適配，不得有漏網元件。
- **動效**：畫面轉場、列表項目進場、數字滾動（餘額變動）皆須 spring／tween 動畫；保持 60fps，禁止明顯掉幀；支援 edge-to-edge 與 predictive back 手勢。
- **畫面結構（底部導航 5 頁 + FAB）**：
  - 總覽：本月收支卡片（玻璃卡）、各帳戶餘額列、近期明細、快速記帳 FAB
  - 明細：完整交易列表＋搜尋篩選
  - 統計：圖表儀表板
  - 帳戶：帳戶卡片＋管理
  - 設定：預算、AI 連線、通知、備份、外觀
  - 附屬頁：AI 助手頁（每週分析＋自由提問）
- 每個畫面必須有空狀態（無資料時的引導插圖與文案）與錯誤狀態設計。

## 五、技術架構（指定，不得替換）

- 語言：Kotlin；UI：Jetpack Compose + Material 3
- 架構：MVVM + Repository，模組分層 data / domain / ui
- DB：Room；設定：DataStore（Preferences）；DI：Hilt
- 網路：Retrofit + OkHttp（AI API），逾時與重試要有合理預設
- 排程：WorkManager（週期任務、備份）＋ AlarmManager 精確鬧鐘（每日提醒）
- 導航：Navigation Compose
- 字串：全部 strings.xml（zh-TW 預設），數字／日期格式用 NumberFormat／DateFormat 本地化
- 單元測試：餘額計算、統計彙總、預算判斷、週期排程邏輯至少覆蓋核心路徑

## 六、防後台終止策略（重點，不可敷衍）

1. 以 WorkManager 作為排程主體（週期任務、備份、週報），不得依賴常駐 Service。
2. 每日提醒等精確需求使用 exact alarm：宣告 SCHEDULE_EXACT_ALARM，並在使用者進入相關功能時引導授予；同時實作「精確鬧鐘被系統收回後的 fallback（改 WorkManager 彈性排程）」。
3. 內建「通知與續航設定精靈」頁面：偵測 Build.MANUFACTURER，顯示對應廠牌（Samsung／Xiaomi／OPPO／vivo／Huawei／Honor／Google）的白名單設定圖文步驟，並儘可能直接跳轉該廠牌設定頁（依 dontkillmyapp 整理的 Intent 清單），無法跳轉則開啟 dontkillmyapp 對應說明頁。
4. 提供「忽略電池最佳化」請求：REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 權限＋ ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 引導（僅在精靈頁觸發，不濫用）。
5. RECEIVE_BOOT_COMPLETED：重開機後重新排程所有 WorkManager 與精確鬧鐘。
6. 權限清單必須最小化且每一項都有對應功能：POST_NOTIFICATIONS、SCHEDULE_EXACT_ALARM、RECEIVE_BOOT_COMPLETED、REQUEST_IGNORE_BATTERY_OPTIMIZATIONS、INTERNET。不需要相機、定位等一律不得宣告。

## 七、建置與交付（CI／CD）

1. Gradle Wrapper 四件套（gradlew、gradlew.bat、gradle/wrapper/gradle-wrapper.properties、gradle-wrapper.jar）必須完整提交，這是 CI 能建置的前提。
2. 建立 `.github/workflows/build.yml`：
   - 觸發：push 到 main ＋ 手動 workflow_dispatch
   - Job：checkout → 設定 JDK → 執行單元測試 → assembleDebug → assembleRelease（R8 混淆、簽章）→ 產物命名 Hmoney-vX.Y.Z-debug.apk / Hmoney-vX.Y.Z-release.apk
   - versionCode 由 CI 以 GITHUB_RUN_NUMBER 覆寫遞增，確保可覆蓋安裝升級
   - 上傳為 workflow artifact，並建立帶 tag（vX.Y.Z）的 GitHub Release，把兩個 APK 附上——目標是使用者打開倉庫的 Releases 頁面就能直接下載 APK
3. README.md 更新：功能簡介、Releases 下載教學、keystore 設定說明、本機建置步驟、技術棧清單。
4. Release 權限 fallback：agent 的權限來自 GitHub App 授權，建立 Release 可能失敗。若失敗：改將兩個 APK commit 至倉庫 `output/` 資料夾（.gitignore 加入例外，同時產物路徑寫進 README）。不得因 Release 建立失敗而中斷交付。

## 八、簽章（由代理全程處理，不勞煩使用者）

- Release 簽章走 GitHub Secrets：KEYSTORE_BASE64、KEYSTORE_PASSWORD、KEY_ALIAS、KEY_PASSWORD（名稱固定，寫進 workflow 與 README）。
- 必須自行產生 keystore 並立即做雙保險：
  1. 嘗試以 GitHub CLI `gh secret set` 或 GitHub API 設定四個 Secrets（agent 權限來自 GitHub App，未必涵蓋 secrets 管理，成敗都要繼續往下做）；
  2. 無論 (1) 成敗，將 keystore 檔與密碼清單（KEYSTORE_PASSWORD、KEY_ALIAS、KEY_PASSWORD）commit 至倉庫 `.private/` 資料夾，並在 .gitignore 加入該資料夾的例外。本倉庫為 Private 個人專案，此為可接受取捨；交付報告必須註明「若未來要公開此倉庫，必須先輪替金鑰」。
- 為何必須 (2)：沙箱 session 結束時會清除未提交的檔案。keystore 若只存在沙箱中就會永久遺失，之後永遠簽不出同金鑰的版本，App 將永遠無法覆蓋升級——此步驟不得以「安全考量」為由省略。
- CI 簽章讀取順序：優先使用 Secrets → Secrets 不存在時改從 `.private/` 內的 keystore 簽章 → 兩者皆缺失時 fallback 產出 debug 簽章 APK。任何情況下建置不得直接失敗。
- 簽章金鑰必須長期一致（不可每次建置換 key），否則無法覆蓋安裝升級、會遺失使用者資料。

## 九、品質要求與禁止事項

品質要求：

- 所有輸入有驗證與防呆；所有外部操作（API、備份）有 loading／成功／失敗三態回饋。
- 遵循 Kotlin 官方風格；程式碼有段落註解（zh-TW）說明關鍵邏輯。
- Commit 分批且有意義（feat: 多帳戶系統、ci: 打包管線…），禁止一次巨型 commit。

禁止事項：

- 禁止提交：local.properties、keystore／任何金鑰、API Key（註：`.private/` 例外依第八節雙保險條款）。
- 禁止 TODO、placeholder 畫面、假資料交差。
- 禁止宣告需求外的權限；禁止引入與功能無關的大型依賴。
- 禁止使用已棄用 API（如舊版 permission 排程寫法、PendingIntent 無 FLAG_IMMUTABLE）。
- 禁止未完成研究階段就直接動工。
- 禁止在同一 session 內試圖一次完成多個階段：只推進本 session 指定的一至兩個階段，其餘縱使看起來簡單也不得偷做；寧可完整交付少量，不可草率交付大量。

## 十、完成前自檢清單（逐項驗證後才可交付）

1. 研究文件 docs/research.md 存在且版本號與 Gradle 實際設定一致
2. Gradle Wrapper 四件套齊全
3. 每個宣告的權限都對應實際功能，manifest 無多餘宣告
4. 六項核心功能（記帳、多帳戶、統計、預算／週期、AI 分析、通知）全部可操作、有 UI 可達
5. 防後台精靈頁可偵測主要廠牌並給出對應教學
6. 深色／淺色模式無破版；低於 API 33 的裝置不因玻璃效果崩潰
7. AI 未設定 API Key 時有友善引導而非錯誤
8. CI 綠燈：測試通過、APK 產出並附於 GitHub Release
9. versionCode 遞增機制生效
10. README 含下載與設定教學
11. Secrets 或 .private/ 中存在可用的 release 簽章金鑰（並已註明公開倉庫前需輪替金鑰）
12. 本 session 指定階段已在 docs/PROGRESS.md 勾選更新
13. 無捲動容器巢狀：任何 LazyColumn／LazyVerticalGrid／verticalScroll 不得位於另一個無限高度約束的垂直捲動容器內（此類錯誤編譯與 lint 皆測不到，實機第一次測量即閃退）

## 十一、執行順序

1. 研究階段 → docs/research.md
2. 專案骨架（Gradle、manifest、Hilt、導覽、主題系統）
3. 資料層（Room 實體與 DAO、Repository）
4. 核心功能（記帳 CRUD、多帳戶與轉帳）
5. UI 全面實作（含玻璃態與動效）
6. 統計、預算、週期交易
7. 通知與防後台終止（含設定精靈）
8. AI 分析串接
9. 單元測試與修 bug
10. CI/CD、簽章、首次 Release、README
11. 推送至 main，確認 Actions 綠燈與 Release 頁面可下載 APK

## 十二、分 Session 執行協議

### Session 切分表

| Session | 執行範圍（第十一節） | 備註 |
| --- | --- | --- |
| 1 | 第 1–2 項 | 研究階段＋專案骨架；並將本規格 commit 為 docs/SPEC.md、建立 docs/PROGRESS.md 進度清單 |
| 2 | 第 3–4 項 | 資料層＋核心功能 |
| 3 | 第 5 項 | UI 全面實作（玻璃態、動效） |
| 4 | 第 6 項 | 統計、預算、週期交易 |
| 5 | 第 7 項 | 通知與防後台終止 |
| 6 | 第 8 項 | AI 分析串接 |
| 7 | 第 9 項 | 單元測試與修 bug |
| 8 | 第 10–11 項 | CI/CD、簽章雙保險、Release／output/ fallback、README、最終驗證 |

### Session 1 提示詞（首次開工）

以下是「幻帳（Hmoney）」專案的總規格書。請嚴格執行三件事：(1) 將本規格完整 commit 至倉庫 docs/SPEC.md，並建立 docs/PROGRESS.md（把規格第十一節的 11 個階段列為未勾選清單）；(2) 執行第十一節的第 1–2 項（研究階段 → docs/research.md、專案骨架），過程中遵守規格全部條款；(3) 完成後 commit 推送至 main，更新 docs/PROGRESS.md 勾選，並回報完成內容與遺留事項。本 session 禁止執行第 3 項以後的任何階段。

### Session 2 起的通用提示詞模板

讀取倉庫中的 docs/SPEC.md 與 docs/PROGRESS.md。本 session 只執行第十一節的第【N】–【M】項，其餘階段縱使看起來簡單也禁止偷做。完成後：commit 推送至 main、在 docs/PROGRESS.md 勾選對應項目、回報本 session 完成內容與遺留事項。

### 每個 session 的收尾紀律（使用者檢查）

- 確認 agent 已將 commit 推上 GitHub（重新整理倉庫頁面確認）才結束 session。
- 若中途斷線或交差，下個 session 從 docs/PROGRESS.md 的未完成項重跑，不整份重來。
