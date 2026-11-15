# 幻帳（Hmoney）開發進度清單

> 依 `docs/SPEC.md` 第十一節的 11 個階段追蹤。每個 session 只執行對應階段，完成後勾選。
> 本文件僅做進度與交接使用，不重複 SPEC 內容；內容有衝突時以 SPEC 為準。每個 session 結束時更新本文件。

## 1.3.0 結案總覽（Session 52）

**定位**：1.3.0＝AI 記帳與物品管理大版本——DB v5（transactions 加 source／barcode／receiptImagePath 欄位＋items 表）、文字／圖片／條碼三入口記帳（草稿預覽→逐筆確認寫入）、AI 查詢（含記錄功能）、AI 刪改交易（必經確認卡）、物品管理＋到期提醒、掃碼價格趨勢、流體玻璃分頁列（五分頁，inventoryEnabled 動態過濾）、新手引導六頁＋三步實機點擊引導。

**Session 38–51 結案狀態**（全部已結案、CI 綠燈、雙 APK 已交付）：
- S38（88036d0）：1.3.0 資料層擴充 DB v5——舊版 APK 覆蓋安裝（DB v4→v5 遷移）已實機驗證、不丟資料
- S39（3eabb45）：AI 記帳服務層（TransactionDraft、文字／圖片解析、vision 錯誤類型）
- S40（535326e）：條碼服務層（ML Kit 掃描封裝＋CameraX 依賴、價格趨勢純函式）
- S41（2ae34bd；fix 37340cb）：記帳三入口 UI＋DraftPreviewSheet＋掃碼實機三 bug 修復
- S42（8710bfb）：release 掃碼 R8 keep規則＋「此商品記一筆」收覆蓋層與 phase 白名單＋設定頁入口
- S43（89637e7）：條碼欄位全面整合（草稿卡顯示、手動記帳掃碼欄）＋版本號 1.3.0
- S44（b6cd50d）：帳戶列 FlowRow 顯示全部＋聊天室智慧記帳（三入口草稿組卡）
- S45（ca24590）：聊天草稿卡日期排版修復＋條碼列＋AI 查詢記錄功能
- S46（3b7d138）：DatePicker headline 截斷修復（共用 formatter）＋物品管理頁
- S47（deb8f45；fix 294689d）：流體水滴玻璃分頁列＋日期對話框全寬修復＋實機黑畫面修復
- S48（8d732e2）：日期對話框七欄等寬＋意圖閘門＋AI 刪改交易確認卡＋掃碼趨勢與物品到期時間軸
- S49（73e87ae）：新手引導 Onboarding（DataStore 旗標）＋穩定 key／相對期限／空泛刪除／水滴滑動四修復
- S50（422b96e）：水滴貼合內容＋滑動手勢移 Row＋物品第 5 分頁（映射函式）＋三步實機點擊引導＋條碼 targetRotation 動態更新
- S51（a0b5131）：分頁滑動速度改 VelocityTracker（放手亂跳修復）＋引導攔截層改四條遮罩條（洞內可點修復）

**殘留 watch items**（僅 2 條）：
1. 掃碼中轉橫 targetRotation 更新——S50 已實作（DisplayListener），尚未實機驗證（非阻擋項）。
2. release APK 體積可選優化——ML Kit bundled 全 ABI，未來可 abiFilters 瘦身（1.4.0+ 選項，非本版事項）。

**版本與建置覆核（Session 52，唯讀檢查）**：
1. app/build.gradle.kts：versionName＝1.3.0、versionCode＝GITHUB_RUN_NUMBER 遞增（無殘留實驗設定或註解掉的舊值）——已覆核 ✓
2. Room schemaVersion＝5（HmoneyDatabase.kt），app/schemas/com.hmoney.app.data.db.HmoneyDatabase/5.json 在庫，9 張表與 entity 現況一致——已覆核 ✓
3. versionCatalog：Haze 2.1.0、ML Kit barcode-scanning 17.3.0（bundled）、CameraX 1.5.1，與 PROGRESS S40 記錄一致——已覆核 ✓
4. CI workflow（build.yml）：testDebugUnitTest→lintDebug→assembleDebug→assembleRelease（R8＋簽署，Secrets→.private/→debug fallback）→雙 APK 命名→GitHub Release→output/ fallback，路徑與現況一致——已覆核 ✓
5. strings.xml：S51 相關字串（coach_step1/2/3_hint、coach_tap_hint、coach_skip）全部有使用、無殘留——已覆核 ✓。另發現 19 條早期 session 遺留的未使用字串（add_confirm、add_date_picker、add_delete、add_deleted、add_save、add_time_picker、ai_filter_failed、budget_total_not_set、budget_warning、items_ai_no_suggestion、notification_exact_alarm_desc、notification_exact_alarm_title、overview_see_all、recurring_edit、recurring_enabled、stats_description、transactions_filter_applied、transactions_filter_type、transactions_transfer_from）——與 S51 無關，僅記錄待辦（1.4.0 清理候選），本 session 不動。

**請勿誤改彙總（S41–S51 教訓逐條核對齊備；細節以各 session 紀錄為準，此處不重複展開）**：
1. 未存 DB 的 data class 嚴禁以物件身分或 System.identityHashCode 當 LazyColumn／remember key——必用建立時配發的穩定 key（S49）
2. 手勢 pointerInput 必須掛在命中目標的祖先（如 Row），嚴禁掛在被覆蓋的兄弟層（如 Canvas）——命中測試不派發給兄弟（S50）
3. 頂層分頁 index 一律走 route→index 映射函式（以過濾後清單計算），嚴禁 hardcode index（S50）
4. coach 引導只掛觀察掛鉤（FAB onClick 額外回呼＋root 觀察導航變化），嚴禁改 FAB／分頁列內部導航邏輯（S50）
5. 手勢速度嚴禁以 System.nanoTime().toFloat() 算時間差——一律 VelocityTracker＋uptimeMillis（S51）
6. 引導挖洞＝洞內區域零 pointerInput 節點——獨占命中下「未消費」不會穿透給下方元件；洞外攔截用四條遮罩條（S51）
7. 分頁列 bottomBar 固定 80dp＋navigationBars insets，水滴 56dp 貼合置中內容（S47／S49／S50）
8. 選擇器清單嚴禁 take(N) 截斷——帳戶列必須 FlowRow 顯示全部（S44）
9. 同症狀分層診斷：文字格式（formatter 截斷）與容器寬度（padding 疊加）是兩個缺陷，勿只修一層（S25／S46／S47／S48）
10. Dialog 型流程轉場必須先收掃碼覆蓋層＋onCameraFrame phase 白名單 guard——幀不得覆蓋 PREVIEW（S42）

**版本政策**：1.3.0 feature freeze——後續任何 1.3.x 修復一律走獨立 fix session，不夾帶功能或重構。

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
- **Session 53（公開前簽章金鑰去倉庫化：樹狀層，零功能改動，已完成）**：把 release 簽章金鑰從樹狀與建置邏輯移除——刪除 `.private/` 整個資料夾（hmoney-release.p12＋keystore-info.txt）；app/build.gradle.kts 簽章讀取由三層改兩層（GitHub Secrets 環境變數 → 都沒有則 fallback debug 簽章），刪除倉庫 .private 層與 keystoreInfo 讀取；.github/workflows/build.yml 刪除「無 Secret 時改用倉庫 .private」的 fallback 分支與訊息；.gitignore 刪除 !/.private/** 例外、新增 *.p12（!.output 例外照舊）；README「Keystore 與簽章」與 SPEC 第八節簽章說明同步改寫（金鑰檔案不入庫）。**歷史淨化（filter-repo 或 squash）由使用者本機執行，未完成前不得轉公開。**零 App 行為變更；release 簽章走 Secrets 不受影響。
- **Session 52（1.3.0 正式收尾：文檔總整理＋版本覆核，零功能改動，已完成）**：diff 僅 docs/PROGRESS.md——頂部新增「1.3.0 結案總覽」（定位一段話、S38–51 逐段一行結案狀態含主 commit 短 SHA、S38 舊版 APK 覆蓋安裝 DB v4→v5 已實機驗證註記、watch items 兩條、請勿誤改彙總 10 條）；Session 紀錄區塊本身未刪未改。版本與建置覆核（唯讀）五項全數通過：versionName 1.3.0／versionCode GITHUB_RUN_NUMBER、Room schemaVersion 5＋5.json 在庫且 9 表與 entity 一致、versionCatalog（Haze 2.1.0／ML Kit 17.3.0／CameraX 1.5.1）與記錄一致、CI workflow 步驟完整且 output/ 路徑一致、strings.xml 無 S51 殘留字串（另發現 19 條早期未使用字串，僅記錄待辦不動）。**1.3.0 feature freeze：後續任何 1.3.x 修復一律走獨立 fix session。**殘留 watch items 兩條：①掃碼中轉橫 targetRotation 更新（S50 已實作，尚未實機驗證，非阻擋項）；②release APK 體積可選優化（ML Kit bundled 全 ABI，未來可 abiFilters 瘦身，1.4.0+ 選項）。
  - **驗證方式**：零程式碼／UI／測試改動，沙箱無 JDK／Android SDK；CI（測試＋lint＋雙 APK）由 GitHub Actions 執行，綠燈即結案；output/ APK 無需更新（無程式碼變更）。
- **Session 51（分頁列滑動放手亂跳＋引導高亮點擊無反應，兩 bug 修復，已完成）**：只動 LiquidGlassTabBar.kt 的拖曳速度段與 CoachMarksOverlay.kt 的攔截層，其餘檔案零 diff；分頁列固定 80dp＋nav insets、route／rememberSaveable、FAB／分頁列 onClick 與 coach 掛鉤邏輯、versionCode 全部零變更。
  - **Bug 1 根因（滑動一放手必飛到第一個或最後一個分頁）**：onHorizontalDrag 以 `System.nanoTime().toFloat()` 算時間差——nanoTime 是開機奈秒數（約 10^14），轉 Float 後精度只有 8~16ms，比相鄰指標事件間隔還大 → dt 常算成 0 → 被 coerceAtLeast(1e-4f) 硬補 → 瞬時速度暴增為每秒數百個索引 → onDragEnd 的 fling 投影 `animatedIndex.value + dragVelocityIndex * 0.12f` 得 ±50 → 永遠夾到 0 或 tabCount-1。拖曳跟手（Job＋snapTo）本來就正確，未動。
  - **Bug 1 修法**：速度改用 Compose `VelocityTracker`（androidx.compose.ui.input.pointer.util）——onDragStart `resetTracking()`；onHorizontalDrag `addPosition(change.uptimeMillis, change.position)`（uptimeMillis 是小數值毫秒 Long，無精度問題），並把每次算出的 indexVelocity（calculateVelocity().x ÷ tabWidthPx）存進既有 dragVelocityIndex 供水滴拉伸用；onDragEnd 以 `velocityTracker.calculateVelocity().x / tabWidthPx` 取放手速度，fling 投影係數 0.12f→0.25f，`abs(indexVelocity) < 1f`（慢速）直接吸附 `animatedIndex.value.roundToInt()`；spring 吸附與 onSelect 照舊。刪除 dragTracker（floatArrayOf）與所有 System.nanoTime() 用法——拖曳中液態拉伸速度上限 0.38 現在就是同一個真實速度值，不再恆為最大。
  - **Bug 2 根因（引導高亮處點擊無反應，第①步無法完成）**：CoachMarksOverlay 的攔截 Box 是全畫面 matchParentSize().pointerInput，在洞內「不消費事件」期待穿透——但 Compose 命中測試對重疊兄弟層預設獨占：最上層攔截層被命中後，下方 FAB／分頁項不在命中路徑上，不消費也不會收到事件 → FAB 的 onClick（含 coach 掛鉤）從未觸發。
  - **Bug 2 修法**：攔截層改為四條遮罩條（洞內區域不得有任何 pointerInput 節點）——保留 Canvas＋BlendMode.Clear 挖洞（視覺不變）；刪掉全畫面攔截 Box，改放四個 Box（offset＋size 依 px 定位、密度換算）：①上條 (0,0) 寬＝螢幕寬高＝hole.top；②下條 (0,hole.bottom) 高＝螢幕高−hole.bottom；③左條 (0,hole.top) 寬＝hole.left；④右條 (hole.right,hole.top) 寬＝螢幕寬−hole.right；每條掛 `pointerInput(Unit) { detectTapGestures { } }`（消費即可，點洞外顯示「點擊高亮處」效果不變）。洞內零覆蓋 → 底層 FAB／分頁項自然成為最上命中者 → 真的可點、可滑。提示卡與「跳過引導」照舊。四條 bounds 計算抽成純函式 coachMaskBarRects（洞貼邊／超出螢幕時夾回、過濾零尺寸），新增 CoachMaskBarsTest 共 3 案（四條恰包住洞且面積＝螢幕−洞、貼邊不出現負／零尺寸、洞超出螢幕夾回）。
  - **請勿誤改**：(1) 手勢速度禁止以 `System.nanoTime().toFloat()` 算時間差——Float 對 10^14 量級只有 8~16ms 精度，dt 常為 0 會讓速度暴增、fling 永遠飛到端點；一律用 VelocityTracker＋uptimeMillis。(2) 引導挖洞＝洞內區域零 pointerInput 節點——Compose 兄弟層命中測試預設獨占，最上層節點「未消費」不會穿透給下方元件，全畫面攔截層會讓洞內點擊永遠無效；洞外攔截用四條遮罩條。
  - **驗收基準**：分頁列——慢拖到中間任一分頁放手停在該分頁、快滑可跨 1~2 頁、拖曳中液態拉伸隨速度變化不再恆為最大、點擊零誤觸；引導——第①步點高亮的記帳 FAB 記帳選單真的開啟且自動進第②步、第②步點任意分頁切頁並進第③步、第③步點物品分頁引導完成消失、洞外點擊被擋、跳過按鈕正常。
  - **驗證方式**：沙箱無 JDK／Android SDK（與先前 session 相同），單元測試（含新增 CoachMaskBarsTest）＋lint＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。

- **Session 50（分頁列兩修復＋物品第 5 分頁＋onboarding 實機點擊引導＋條碼 targetRotation，已完成）**：①水滴貼合修復——根因：LiquidGlassTabBarItem 的 Column fillMaxHeight＋padding(10,10)＋預設頂部對齊 → 內容偏上、下方約 28dp 空，水滴以 canvas 中心置中 → 文字下緣到水滴底約 20dp 空洞。修法：Column 加 verticalArrangement = Arrangement.Center（移除上下 padding，由置中消化）；水滴 baseHeight 64dp→56dp（寬 tabWidth×0.72 照舊），fontScale 1.0/1.3 下上下各約 5~8dp 貼合。②滑動手勢修復——根因：pointerInput 掛在 Canvas（Box 第一子層），Row 覆蓋其上；Compose 命中測試只沿「命中目標＋祖先」派發事件，Canvas 是 Row 的兄弟不在命中路徑 → 手勢永遠收不到事件（手勢碼本身正確）。修法：pointerInput 移到 Row modifier（fillMaxWidth 之後）；跟手改為 animatedIndex.value 直接賦值（setter 非 suspend，刪除 dragSnapJob/launch snapTo）；放手 spring 吸附＋fling 投影＋onSelect 照舊；拖曳過 slop 後 consume() 讓子層 clickable 取消 tap，點擊零誤觸。③物品第 5 分頁——TopLevelDestination 增列 ITEMS（統計之後、帳戶之前，route 沿用 items，NavHost destination 不動）；ItemsScreen 升頂層移除返回箭頭；帳戶頁物品入口改走 navigateToTab（與分頁點擊行為等價）；inventoryEnabled 動態過濾頂層清單（關閉→四分頁），route→index 一律走 topLevelTabIndex 映射函式（tabCount／selectedTabIndex／onSelect 全走同一過濾後清單，嚴禁 hardcode index）；水滴寬度與滑動吸附隨動態 tabCount 自動縮放。④Onboarding 六頁＋實機點擊引導——sheet 頁數 4→6（歡迎→手動表單→AI 對話→掃條碼→AI 查詢刪改與物品管理（含獨立分頁）→統計與帳戶＋完成預告）；「開始使用」→ startCoach（step 1，持久化 onboarding_coach_step，中斷續步），「跳過」→ 完成旗標永不再現。coach marks 覆蓋層（總覽頁最上層 Box）：半透明遮罩 Canvas＋BlendMode.Clear 挖洞（目標 bounds 外擴 12dp），洞外攔截觸控、洞內放行直達真實元件（FAB／分頁列可真的被點）；三步＝①高亮記帳 FAB（點到即 step 2）②高亮分頁列（偵測實際切頁；inventoryEnabled 關閉時直接完成）③高亮物品分頁（點到即完成寫旗標）；步驟完成偵測只加觀察掛鉤（FAB onClick 額外通知回呼＋root LaunchedEffect 觀察導航 route 變化驅動 HmoneyAppRootViewModel 狀態機；FAB／分頁列內部導航邏輯零改動）；目標 bounds 以 onGloballyPositioned 收集（旋轉／字級變化自動更新），物品分頁項 bounds 由分頁列 bounds＋過濾後清單推導（不侵入分頁列內部）。⑤條碼 targetRotation 小修——CameraPreview 以 DisplayManager Display.Listener 觀察 display rotation，即時更新 analyzer 的 targetRotation（AtomicReference 參照），掃碼中轉橫仍可偵測（Log.i 可證）；既有黑屏防護（LocalLifecycleOwner、HARDWARE 防禦、ResolutionSelector、finally close、in-flight 序列化、proguard keep）零變動。
  - **測試**：新增 TopLevelDestinationMappingTest（全清單映射／inventoryEnabled 過濾後索引前移與 fallback／非頂層 route→0，共 3 案）、HmoneyAppRootViewModelTest（coach 狀態機：FAB step1→2、非 step1 不推進、step2 離開總覽→3、inventory 關閉直接完成、step3 抵達 items 完成歸零、step3 其他分頁不完成、跳過寫旗標，共 7 案）、FeatureFlagsTest 補 onboarding_coach_step 預設 0 與 key 規格；兩個既有測試假 SettingsRepository 補 setOnboardingCoachStep 介面成員，既有測試零退化。
  - **請勿誤改**：(1) 手勢 pointerInput 必須掛在命中目標的祖先（Row＝各分頁項 clickable 的祖先）——禁止掛在被覆蓋的兄弟層（Canvas），命中測試不派發事件給兄弟；(2) 頂層分頁 index 一律走 topLevelTabIndex 映射函式（以過濾後清單計算，禁 hardcode index）；(3) coach 引導只掛觀察掛鉤（FAB onClick 額外回呼／root 觀察導航變化），禁改 FAB／分頁列內部導航邏輯；(4) 分頁列固定 80dp＋navigationBars insets 鐵律不變，水滴 56dp 高是為貼合置中內容。
  - **驗證方式**：沙箱無 JDK／Android SDK，lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行。實機驗收：①水滴上下各約 5~8dp 貼合、fontScale 1.3 不超出；②滑動跟手、吸附、fling、點擊零誤觸；③五分頁（inventory 開）／四分頁（關）即時切換且水滴／滑動／index 映射全正確，物品頁從分頁與帳戶頁入口皆可進、功能全通、無返回箭頭；④sheet 六頁＋三步點擊引導可真的點到 FAB／分頁；中斷續步；跳過寫旗標；完成後重啟不再現；⑤掃碼直握照常，掃碼中轉橫 targetRotation 更新（log 可證）。
- **Session 49（新手引導 Onboarding＋四個實機修復，已完成）**：①新手引導（主體）——DataStore 加 onboarding_completed 旗標（走既有 FeatureFlagSettings／SettingsRepository 模式，預設 false；完成或跳過皆寫入 true，之後永不再現；舊用戶旗標未存會看一次引導屬合理行為，不做遷移）。新元件 OnboardingSheet（ModalBottomSheet＋skipPartiallyExpanded＋HorizontalPager 四頁：歡迎→記帳三入口（手動／AI 聊天／掃碼）→AI 查詢與物品管理→完成），「跳過」與最後一頁「開始使用」皆寫旗標並關閉；內容為既有功能靜態說明＋示意，嚴禁真的觸發記帳流程；有限高度（heightIn 300~380dp）。OverviewViewModel 加 onboardingCompleted StateFlow（WhileSubscribed＋initialValue true 防首幀誤閃）與 completeOnboarding()；總覽頁出現時顯示。②鍵盤縮回修復（三卡一體）——根因：DraftPreviewSheet 的 LazyColumn key = System.identityHashCode＋卡內 remember(transaction)，每次按鍵 VM copy 出新物件→key 變→整卡重組失焦。修法：EntryViewModel.EditableDraft 與 AiChatViewModel.ChatDraftItem 加穩定 key（建立時配發單調遞增 Long，編輯 copy 保留）；DraftPreviewSheet／聊天草稿清單 LazyColumn key 改用穩定 key；三張卡的 remember(transaction) 全改穩定 key（掃碼草稿卡／聊天草稿卡＝穩定 key；AI 編輯交易卡＝transaction.id，編輯的是已存交易）。全倉庫嚴禁 System.identityHashCode 當 key（已清零）。③物品相對期限——根因：parseItemAction 只懂絕對日期或相對今天的「N天後」。修法：ItemActionIntent 加 expiryDeltaDays（可為負）；提示詞支援「增加／延長／縮短／提前 N 天」輸出 delta（絕對日期優先、delta 僅在無絕對日期時採用）；VM 於名稱匹配後換算 新到期日＝matched.expiry＋delta；ItemActionCard UPDATE 顯示「原到期日 → 新到期日」前後對照。絕對日期語意相容。④空泛刪除記帳——根因：空泛語意讓 parseTransactionAction 回 null 落回一般 chat。修法：TransactionActionIntent 加 recent（提示詞：「刪除／移除＋記帳／交易／支出／剛剛那筆／最新一筆／最近一筆」且無其他標的→isTransactionAction true＋recent true）；VM recent→取最近 5 筆（唯讀查詢後時間倒序）→0 筆回覆找不到、1 筆直接確認卡、2~5 筆候選卡（既有機制）；解析出意圖後必走卡或明確「找不到」，嚴禁落回 chat。⑤分頁列水滴＋滑動——根因：水滴 44dp 蓋不住約 62dp 的 icon+label 且無滑動手勢。修法：水滴高 64dp、寬 tabWidth×0.72（涵蓋整個選中項，仍在 80dp bar 內垂直置中）；液態拉伸／spring／rim／高光公式照舊。新增 detectHorizontalDragGestures：拖曳時水滴 snapTo 跟手、速度以每秒索引數估計（量綱照 S47-fix）驅動拉伸，放手後 spring 吸附最近分頁（含 fling 速度投影）並觸發 onSelect（與點擊同一導航參數組合）；點擊行為零變更；閉包以 rememberUpdatedState 防過時值。分頁 route／rememberSaveable 零變更。
  - **測試**：EntryViewModelTest＋1（草稿穩定 key 各筆相異且編輯不變）；AiChatViewModelTest＋4（聊天草稿穩定 key；UPDATE 相對期限＝現有到期日＋3 且確認後寫入；recent 候選卡選定刪除；recent 零筆回覆找不到不落回 chat）；AiItemActionParserTest＋1（expiryDeltaDays 正負數與絕對日期優先）；AiTransactionActionParserTest＋1（recent 解析）；FeatureFlagsTest 補 onboarding_completed key 驗證。既有測試零退化。
  - **請勿誤改**：(1) 未存 DB 的 data class（EditableDraft／ChatDraftItem 等 copy 每次換新物件）嚴禁以物件身分或 System.identityHashCode 當 LazyColumn key／remember key——必用建立時配發的穩定 Long key（已存交易用 transaction.id）；(2) 分頁列固定 80dp＋navigationBars insets 鐵律不變，水滴 64dp 高／tabWidth×0.72 寬是為涵蓋 icon+label，滑動手勢只動視覺層與手勢層，route／rememberSaveable 行為零變更；(3) AI 一切操作必經確認卡且空泛刪除（recent）解析出意圖後必走卡或明確「找不到」訊息，嚴禁落回一般 chat；(4) 物品期限「增加 N 天」＝相對現有到期日（expiryDeltaDays 於名稱匹配後換算），絕對日期語意優先且相容。
  - **驗證方式**：沙箱無 JDK／Android SDK，lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行。實機驗收：①首次安裝→引導出現→完成／跳過→重啟不出現；②三張卡金額／備註連續輸入鍵盤不縮、剔除一張其他卡輸入不歸零；③「將鮭魚的保存期限增加3天」→卡顯示原→新到期日且確認後生效；④「刪除記帳」→候選卡→確認→該筆刪除；⑤水滴包住 icon+label、滑動跟手、放手吸附、快滑 fling、點擊照舊。
- **Session 48（日期對話框七欄等寬＋意圖閘門＋AI 刪改交易＋自製圖表，已完成）**：四件事一次交付。①日期對話框——**路線決策：選「移除 Card」而非客製月曆格**。理由：M3 CalendarMonth 的星期列與週格走 BaseCalendarLayout，把可用寬度「嚴格均分 7 欄」（非文字測量 weight），欄寬不足只會換行／省略、不會裁切末欄；「星期六被壓縮」根因是 Card＋Column 共 64.dp padding 吃掉日曆寬度。修法：內容直接放 Dialog（Surface 提供形狀底色、零額外 padding），水平 padding 降到 8.dp——360dp 螢幕＋fontScale 1.3 下每欄約 49dp，單一中文字綽綽有餘，任何廠牌／字體縮放皆等寬。四個呼叫點介面零變動。②意圖分流閘門——AiChatViewModel.send 前置本地關鍵字：含刪改動詞（刪除／移除／改成／修改／編輯）→ parseTransactionAction；含「物品」或刪改動詞 → parseItemAction；含「物品」→ 不觸發記帳 parse；其餘照既有（記帳→物品→查詢→chat）。防護第二層：parseTransactionsFromText 提示詞加「物品管理或交易刪改→回空陣列」、parseItemAction 提示詞加「記帳或交易刪改→isItemAction false」。③AI 刪改交易——AiRepository.parseTransactionAction（模式照 parseTransactionQuery：request()＋extractDraftContent()，嚴禁經 extractAiContent）→ TransactionActionIntent（DELETE／UPDATE；target 重用 TransactionQuery 欄位；updates 金額／備註／分類／帳戶／日期）；解析器 parseTransactionActionFromContent 純函式。匹配走既有唯讀 searchTransactions（上限 5 筆）：0 筆→助理回覆找不到；1 筆→直接確認卡；2~5 筆→候選卡（每筆日期／金額／分類／備註＋「選這筆」）→選定進確認卡。TransactionActionCard（模式照 ItemActionCard）：DELETE＝完整交易資訊＋紅色不可逆警示；UPDATE＝金額／備註／分類／帳戶／日期可編輯（分類帳戶限定清單選取＋既有 fallback 慣例）。確認只呼叫既有 updateTransaction／deleteTransaction；一次僅一筆；終態唯讀不得重複執行。④自製 Canvas 圖表（零新依賴）——掃碼資訊卡趨勢資料 ≥2 筆時畫迷你折線（座標抽純函式 lineChartPoints，首尾標日期、最新價標記）；物品頁頂部到期時間軸（未來 30 天有到期日子畫點、密度＝當日數量，點擊捲動到對應分節；無到期物品隱藏；分佈純函式 expiryTimelineCounts）。
  - **測試**：新增 AiTransactionActionParserTest（DELETE／UPDATE／updates／非操作→null／action 無效→null／markdown 圍籬，共 7 案）、ChartGeometryTest（空序列／單點置中／x 均分 y 反轉／padding／全同值／非正尺寸，共 5 案）、ItemExpiryTimelineTest（密度／邊界排除／空清單／分節對應，共 4 案）、AiChatViewModelTest 新增 9 案（含「物品」訊息斷言不觸發記帳 parse——fake 呼叫計數；刪改動詞先交易操作；皆 null 續走記帳；DELETE 0／1／多筆；UPDATE 併入 updates 寫入斷言；取消零寫入；終態不可重複；未決只提示）；四個既有 FakeAiRepository 僅補 parseTransactionAction 介面成員，既有測試零退化。
  - **請勿誤改**：(1) 日期對話框不得再加 Card／雙層 padding——七欄等寬是硬性需求，M3 BaseCalendarLayout 嚴格均分 7 欄，唯一風險是外層 padding 吃寬；(2) 意圖閘門順序（物品關鍵字→刪改動詞→交易操作→物品操作→記帳→查詢→chat）是刻意設計，勿改動；(3) AI 刪改交易一次僅一筆、必經確認卡、嚴禁批次刪除；(4) 交易寫入只准呼叫既有 updateTransaction／deleteTransaction，不得新增寫入路徑。
  - **驗證方式**：沙箱無 JDK／Android SDK（與先前 session 相同），lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。實機驗收：四個日期對話框星期「六」欄完整等寬（系統字體調大再驗）；「新增物品 鮮奶 放冰箱 3天後到期」→物品確認卡；「刪除昨天的鮮奶那筆」→候選／確認卡→確認後明細消失；「把昨天那筆改成 2000」→編輯卡→確認後明細更新；「你好」→一般對話；掃碼趨勢卡折線、物品頁時間軸、分頁列／記帳三入口／AI 查詢零退化。
- **Session 47（流體水滴玻璃分頁列＋日期對話框全寬修復＋AI 聊天室物品管理，已完成）**：①分頁列視覺升級——**liquid-glass 函式庫評估結論：不採用，自製**。ntvien/liquid-glass 雖 minSdk 26 相容，但要求 Kotlin 2.4+（本專案 2.3.21，升級 Kotlin 超出本次範圍且風險高）、且要求整個 app 包 LiquidGlassTheme／每個畫面包 LiquidGlassScene——違反「S47 只換分頁列視覺層」鐵律。取捨：自製 `ui/components/LiquidGlassTabBar`（Canvas 繪製液態水滴選中指示器：四段 Bézier 路徑＋spring 位移動畫，移動速度越快水滴沿移動方向拉長變扁＝液態變形；頂部白色漸層高光），背景沿用既有 glassSurface（API 33+ Haze 毛玻璃、API 26–32 半透明壓色降級——水滴為純 Canvas 繪製，全版本不崩潰，動畫保留）。深淺色皆取 MaterialTheme.colorScheme（primaryContainer／secondaryContainer／primary／onSurfaceVariant），點擊無漣漪延遲。**僅替換視覺層**：HmoneyAppRoot 的 navigate onClick、route、狀態保存零變動。②日期對話框欄重疊修復——四個日期對話框（AddTransactionSheet.DateFieldButton／DraftPreviewSheet／ChatDraftCard／ItemEditSheet 到期日）容器從 AlertDialog／DatePickerDialog 改為新共用元件 `ui/components/FullWidthDatePickerDialog`：`androidx.compose.ui.window.Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`（照 SimpleScanDialog 全寬模式）＋16.dp 水平 padding＋頂部標題（沿用既有字串）＋DatePicker fillMaxWidth＋共用短格式 formatter＋底部取消／確定按鈕列（沿用既有字串與回呼，行為零變動）。根因：AlertDialog 平台預設寬度壓縮 7 欄日曆，星期五六重疊。③AI 聊天室物品管理——`AiRepository.parseItemAction(text, existingItemNames)`（模式照 parseTransactionQuery：request()＋extractDraftContent()，**嚴禁經 extractAiContent**；解析器 parseItemActionFromContent 純函式，日期模式沿用 suggestItemFields：expiryDate（yyyy-MM-dd）優先、expiryInDays 相對天數擇一）；AiChatViewModel 訊息管線＝①記帳意圖 parse（既有）→②物品操作 parse→有→處理→③查詢意圖 parse（既有）→④一般 chat；**inventoryEnabled（FeatureFlagSettings）關閉→②整段跳過（省 AI 呼叫）**。UPDATE／DELETE 先名稱匹配（domain/logic/matchItemByName 純函式：equals→contains 皆 ignoreCase）：無匹配→助理訊息「找不到物品」；多筆→列出候選請使用者講明；單筆→確認卡。新元件 `ItemActionCard`（ChatItemActionCard，模式照 ChatDraftCard）：CREATE 顯示將建立欄位（名稱／數量／存放位置／到期日，可編輯）；UPDATE 顯示前後對照（原值列＋可編輷新值）；DELETE 唯讀＋紅色警示語意。同時僅一個未決物品操作（有未決再觸發→提示列，模式照 draftBlockedHint）；確認→ItemRepository 對應操作→卡轉「已完成」；取消→「已取消」；**終態不得再寫入（VM 端防護）**；**確認前嚴禁寫入 DB**。
  - **測試**：新增 AiItemActionParserTest（CREATE／UPDATE／DELETE／相對天數／明確日期優先／markdown 圍籬／非物品操作→null／action 或 name 無效→null／null 與零值容錯，共 8 案）、ItemNameMatcherTest（完全相等唯一／大小寫／包含／多筆 Ambiguous／同名多筆／無匹配 NotFound，共 6 案）、AiChatViewModelTest 新增 10 案（意圖分流順序：記帳優先物品 parse 不被呼叫、CREATE／UPDATE／DELETE 寫入斷言（fake ItemRepository）、找不到回覆零寫入、多筆列候選、inventory 關→解析整段跳過、取消零寫入且終態不得再寫入、未決再觸發只提示、物品落空續走一般對話）；四個既有 FakeAiRepository 僅補 parseItemAction 介面成員，既有測試零退化。
  - **請勿誤改**：(1) 分頁列水滴效果只換視覺層——分頁行為／route／selectedTab 狀態保存／總覽切頁不重播動畫的既有修復不得動；(2) 日期對話框必須全寬 Dialog（usePlatformDefaultWidth = false）——AlertDialog 平台預設寬度壓縮 7 欄日曆、星期五六重疊的根因，勿改回；(3) 物品操作確認前嚴禁寫入 ItemRepository（物品非交易，寫入唯一路徑＝確認卡確認後的 addItem／updateItem／deleteItem）；(4) inventoryEnabled 關閉時，物品操作解析（parseItemAction）必須整段跳過、不得發出任何 AI 呼叫，直接續走查詢／一般 chat；(5) **bottomBar 子樹嚴禁無限高度／fillMaxSize**——LiquidGlassTabBar 內容必須固定 80.dp＋系統導覽列 insets（比照 M3 NavigationBar 規格）、分頁項 Column 只准 fillMaxHeight；Scaffold 給 bottomBar 的最大高度是全螢幕，子樹一旦 fillMaxSize 分頁列吃掉全螢幕高度、內容區被壓成 0（實機全黑畫面＋水滴畫在螢幕正中央，勿改回）。
  - **驗證方式**：沙箱無 JDK／Android SDK（與先前 session 相同），lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。實機驗收：四個日期對話框小／大字體下七欄星期完整不重疊；四分頁水滴流動順滑、FAB 正常、深淺色正常（API<33 驗證降級路線）；聊天「新增物品 鮮奶 放冰箱 3天後到期」→確認卡→確認後物品頁出現；「刪除鮮奶」「把鮮奶改成 5 罐」同理；「你好」一般對話不受影響；inventory 關閉→物品訊息變一般對話。

  - **fix（實機黑畫面修復，已完成）**：根因——LiquidGlassTabBarItem 的 Column 帶 fillMaxSize()，Scaffold 給 bottomBar 的最大高度是全螢幕，分頁項滿高→整個分頁列吃掉全螢幕高度→頁面內容區被壓成 0（實機全黑、只剩中央一顆實心水滴）。修法：①高度固定——根 Box 不設 fillMaxSize（glassSurface 照舊最外層，背景延伸到系統導覽列後方），Canvas 維持 matchParentSize＋navigationBars padding＋上下 8.dp，Row 改 fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(80.dp)，分頁列總高度＝80dp＋系統導覽列 insets（比照 M3 NavigationBar 規格）；②分頁項 Column 改 modifier（weight(1f)）＋fillMaxHeight()＋clickable＋padding，嚴禁 fillMaxSize；③水滴拉伸量綱修正——velocity 單位是每秒索引數（切頁約 1~5），stretch = min(0.38f, abs(velocity) / 6f)，切頁飛行中明顯拉伸、停止歸零；④玻璃質感——水滴主體漸層 alpha 上 0.65f／下 0.55f，上緣高光保留，新增外緣 rim（同一條 path drawPath，Color.White.copy(alpha = 0.3f)、strokeWidth = 1.dp、Stroke 描一圈）；水滴仍在 Row 之下（Canvas 先畫），是選中指示器背景，不擋圖示文字與點擊。


- **Session 46（DatePicker 截斷修復＋物品管理，已完成）**：①DatePicker headline 截斷修復——新增共用 DatePickerFormats（ui/common/，shortDatePickerFormatter：選中日期 yyyy/MM/dd，月年與年份沿用系統預設），三個呼叫點全部傳入：AddTransactionSheet.DateFieldButton、DraftPreviewSheet、ChatDraftCard（三檔僅 DatePicker dateFormatter 參數＋import 各一行，其他零 diff）。②物品管理頁 ItemsScreen（新 route items，自帳戶頁標題列「物品」icon 進入，僅 inventoryEnabled 開時顯示）：清單依到期狀態分節（已過期→7 天內到期（含當天）→其他，各節升冪、無到期日排最後，純函式 groupItemsByExpiry），每列名稱／數量／存放位置／到期日，點擊編輯、滑動刪除（刪除需確認）、空狀態引導；ItemEditSheet（新增／編輯共用，ModalBottomSheet＋skipHalfExpanded）：名稱必填、數量預設 1、存放位置、到期日（DatePicker＋共用 formatter）、條碼欄唯讀＋掃碼鈕（重用 SimpleScanDialog，掃描節流照抄 AddTransactionViewModel 模式）；物品寫入一律走 ItemRepository（新增 findWithExpiryBefore 唯讀到期查詢；ItemDao 僅加該查詢，observeAll 零變動）。③AI 智慧填選：AiRepository.suggestItemFields(name, barcode)（模式照 parseTransactionsFromText 的 request()＋extractDraftContent() 基建，嚴禁經 extractAiContent、嚴禁寫入 DB；解析器 parseItemSuggestionFromContent 為純函式），建議以預覽形式逐欄「採用」才寫入表單，不自動覆寫使用者輸入；AI 未設定時沿用既有引導（提示＋前往設定）。④到期提醒通知：新 @HiltWorker ItemExpiryWorker（每日，WorkScheduler.scheduleAll 啟動時重排——既有排程模式比照新增）、檢查 7 天內到期（含當天）與已過期物品發本地通知（新渠道 item_expiry 比照建立，沿用既有權限處理）；已提醒物品當天不重複（lastNotified 日期記 DataStore flag，items schema 不動）；inventoryEnabled 關閉時工作檢查直接跳過。無 schema／Room 版本變更；TransactionDao／交易寫入路徑零變動（ConfirmDraftsUseCase 仍為唯一）。
  - **測試**：新增 ItemRepositoryTest（CRUD＋到期查詢：門檻含當天、升冪、無到期日排除）、ItemExpirySectionsTest（三節分類升冪、第 7 天邊界屬即將到期、空節不輸出）、AiItemSuggestionParserTest（完整建議／相對天數／明確日期優先／markdown 圍籬／null 與零值／無法解析回全空）、ItemsViewModelTest（分節列表、名稱空→錯誤不寫入、合法儲存、編輯走更新、刪除、AI 建議預覽不自動寫入、全空建議不顯示、AI 未設定引導）；既有三個 FakeAiRepository 僅補 suggestItemFields 介面成員，既有測試零退化。
  - **請勿誤改**：(1) 三個記帳表面的 DatePicker（AddTransactionSheet／DraftPreviewSheet／ChatDraftCard）必須傳共用短格式 formatter shortDatePickerFormatter——長中文日期 headline 必截斷（Session 25 同款問題勿重蹈）；(2) items 表 schema S38 已定案，不得擴欄位——物品到期提醒狀態（lastNotified）存 DataStore flag，不存 DB；(3) AI 填選是「建議預覽→逐欄採用」，不得自動覆寫使用者已輸入的欄位；(4) 物品寫入走 ItemRepository（物品非交易，嚴禁經 ConfirmDraftsUseCase——寫入路徑鐵律僅約束交易）。

- **Session 45（聊天草稿卡 UI 修復×2＋AI 查詢記錄，已完成）**：①ChatDraftCard 排版修復——分類＋帳戶同一列各 weight(1f)（maxLines=1），日期獨立全寬一列（fillMaxWidth，maxLines=1），帳戶名再長（如「Redmi Watch 6 (悠遊卡)」）日期也永遠完整顯示（模式照 AddTransactionSheet 日期欄，該檔零 diff）。②聊天草稿卡補條碼列：transaction.barcode != null 時顯示「條碼：〈號碼〉」（照 DraftPreviewSheet S43 模式、字串沿用 entry_draft_barcode，掃碼草稿一掃完即在卡上看到號碼）。③新功能「AI 查詢記錄」：新 DataStore 開關 aiQueryEnabled（預設 true，SettingsRepository.AiAssistantSettings 第五成員＋設定頁「AI 助手」區第五開關「AI 查詢記錄」）；AiRepository.parseTransactionQuery 把問題解析為結構化 TransactionQuery（domain 純資料類：日期起訖／關鍵字（對備訃／標籤模糊比對）／分類／帳戶／類型／金額上下限／筆數上限預設 50；非查詢語意回 null；模式照 parseTransactionsFromText 的 request()＋extractDraftContent() 基建，嚴禁經 extractAiContent）；TransactionDao 新增唯讀 searchTransactions（note／tags LIKE＋欄位過濾＋日期新→舊＋LIMIT，既有方法零變動）；查詢結果格式化（日期／金額／類型／分類／帳戶／備訃／標籤）餵 AiRepository.summarizeQueryResult 綜整回答，零結果直接回「沒有符合的記錄。」（不浪費 AI 呼叫）；AiChatViewModel 管線＝記帳意圖 parse（既有）→ 無草稿且開關開 → 查詢意圖 parseTransactionQuery → 有 → 唯讀檢索 → AI 綜整回答；null 或開關關 → 一般 chat 回覆（體感不變）；明細頁標題列（齒輪旁）新增「AI 查詢」入口（僅開關開時顯示，點擊開啟既有 AiAssistantSheet）。
  - **測試**：新增 AiQueryParserTest（JSON 解析：日期範圍／關鍵字／分類帳戶類型／金額上下限／markdown 圍籬／非查詢→null／條件全空→null）；新增 TransactionSearchTest（fake DAO searchTransactions：關鍵字命中 note 與 tags、欄位過濾、筆數上限、日期新→舊）；AiChatViewModelTest 新增 4 案（查詢意圖→結果→綜整回答、零結果、非查詢→fallback chat、開關關→解析整段跳過）＋ fakes 補 searchTransactions／parseTransactionQuery／summarizeQueryResult／aiQueryEnabled；既有測試零退化（EntryViewModelTest／AddTransactionViewModelTest／ConfirmDraftsUseCaseTest 的 fake 僅補上新增介面方法）。
  - **請勿誤改**：(1) TransactionDao.searchTransactions 為唯讀查詢，嚴禁接入任何寫入流程（寫入路徑仍僅 ConfirmDraftsUseCase 一條）；(2) 聊天草稿卡（ChatDraftItemCard）的分類／帳戶／日期排版模式不可改回單列三顆按鈕——分類＋帳戶同列各 weight(1f)、日期獨立全寬；(3) aiQueryEnabled 開關關閉時，查詢意圖解析（parseTransactionQuery）必須整段跳過、不得發出任何 AI 呼叫，直接進一般 chat。

- **Session 44（帳戶列修復＋聊天室智慧記帳，已完成）**：①AddTransactionSheet 帳戶選擇列（AccountChipRow）由 Row＋take(4) 改 FlowRow 顯示全部帳戶（根因：硬截斷＋非換行 Row——5 帳戶只見 4、長名稱被右緣裁切、無法換行；照同檔 RecentNotesChips 的 FlowRow 模式，chip maxLines=1 維持，零捲動；轉帳「轉入帳戶」同元件一併修復）。②聊天室智慧記帳：文字訊息先走 parseTransactionsFromText（帶使用者分類／帳戶清單），有草稿→訊息流彈出草稿組卡、不進一般對話；空或失敗→接續既有 chat 回覆（一般對話零變化）。③圖片按鈕（PickVisualMedia 免權限）→ ReceiptImageStore 暫存縮圖＋parseTransactionsFromImage（確認寫入存 receiptImagePath；取消／session 清空／VM 銷毀刪暫存檔，嚴禁孤兒——ReceiptImageStore 新增 delete 預設方法）。④掃碼鈕→重用 SimpleScanDialog（零修改）；AiChatViewModel.onScanFrame 為 ScanThrottle＋in-flight 模式第三處使用點（照抄 AddTransactionViewModel.onScanFrame）；掃到碼→BarcodeRepository 查趨勢→草稿組金額預填上次價（無紀錄 0），source=barcode。⑤寫入架構：EntryViewModel.confirmAll 的逐筆寫入邏輯抽成 domain/logic/ConfirmDraftsUseCase（草稿唯一寫入路徑；EntryViewModel 以既有 repo 建構、對外行為與既有測試零變動）；聊天草稿組卡（ChatDraftCard，與 DraftPreviewSheet.DraftCard 平行實作——該卡為私有且與 Sheet 流程耦合，抽共用會動 S41 鐵律檔案）逐筆可編輯／剔除＋全部確認／取消；確認→「已記 N 筆」唯讀終態、取消→「已取消」；同時僅一組未決，有未決再觸發記帳意圖→提示先處理不產生新組。AiAssistantSheet 僅輸入列加兩按鈕＋訊息流加草稿卡，weight(1f)／skipHalfExpanded／imePadding 捲動結構未動。無 schema／DB 變更；chat_sessions／chat_messages 零變更。
  - **草稿組僅存 VM UI state 的取捨**：草稿組不落 DB、不寫 chat_messages——切 session／重啟即消失。理由：草稿是「未確認的暫存意圖」，落 DB 會違反「TransactionDraft 嚴禁寫 DB／寫入前必經確認」鐵律，且需要額外狀態欄位與清理機制；UI state 已足夠支撐單次確認流程。
  - **測試**：AiChatViewModelTest 新增 8 案（記帳意圖→草稿組、一般訊息 fallback、確認寫入 source=ai_text、取消零寫入、同時僅一組未決、圖片確認帶 receiptImagePath、取消圖片草稿刪暫存檔、掃碼金額預填上次價 source=barcode）；新增 ConfirmDraftsUseCaseTest（多筆寫入、單筆失敗不中斷）；EntryViewModelTest 零改動零退化。
  - **請勿誤改**：(1) 選擇器清單嚴禁 take(N) 截斷——帳戶列必須 FlowRow 顯示全部；(2) ConfirmDraftsUseCase 為草稿唯一寫入路徑，EntryViewModel 與 AiChatViewModel 的確認按鈕都必須走它；(3) 聊天草稿組僅存 UI state 不落 DB（取捨理由如上），嚴禁為草稿加表或寫入 chat_messages；(4) AiChatViewModel 掃碼為 ScanThrottle 模式第三處使用點（EntryViewModel／AddTransactionViewModel／AiChatViewModel），修改節流行為須三處同步。

- **Session 43（條碼欄位全面整合＋版本號 1.3.0，已完成）**：掃碼入口一掃完草稿卡即可見條碼；手動記帳新增選填條碼欄（唯讀顯示＋掃碼按鈕＋清除鈕）；掃碼 Dialog 抽共用 CameraPreview 供兩處重用；versionName 1.2.0→1.3.0（app/build.gradle.kts 僅此一處，versionCode＝GITHUB_RUN_NUMBER 不變）。無 schema／DB 版本變更（transactions.barcode S38 已存在，本次只接 UI）；AiAssistantSheet／StatsScreen／EntryFlowHost 零 diff；ScanOverlayScreen 僅抽走私有 CameraPreview 改引用共用元件；寫入路徑零變動（手動走 AddTransactionViewModel.save、草稿走 EntryViewModel.confirmAll）。
  - **source 語意決策**：手動記帳（含掃碼填入條碼者）source 一律保持 manual——條碼是資料欄位不是來源；barcode 語意保留給掃碼入口流程（EntryViewModel source=barcode）。存檔建構 MoneyTransaction 只帶 barcode，source 用模型預設 manual。
  - **DraftPreviewSheet**：每張草稿卡在備註欄後顯示「條碼：〈號碼〉」（唯讀小字，僅 transaction.barcode != null 時顯示）。
  - **AddTransactionSheet**（授權例外範圍內）：備註／標籤之後新增「條碼」欄——唯讀文字＋右側掃碼（QrCodeScanner）圖示鈕＋已填時清除（×）鈕；編輯模式 loadForEdit 載入既有交易 barcode；捲動結構鐵律不變（外層不捲動＋中段 weight(1f)＋非 Lazy CategoryGrid＋鍵盤固定底部）。hasUnsavedInput 納入條碼欄。
  - **掃碼 Dialog 重用**：CameraPreview 自 ScanOverlayScreen 抽成共用元件（ui/screens/entry/CameraPreview.kt，行為零變更——1280×720＋targetRotation＋HARDWARE 防禦＋LocalLifecycleOwner 綁定）；新增 SimpleScanDialog（ui/screens/add/）——全螢幕 Dialog＋CameraPreview＋權限引導＋掃到碼顯示號碼與「使用此條碼」，不含趨勢／歷史卡片。
  - **AddTransactionViewModel**：注入 BarcodeScannerSource；UiState 加 scannedBarcode／scanError；onScanFrame 450ms 節流（重用 shouldScanAt）＋in-flight 序列化＋協程內掃描（模式照抄 EntryViewModel.onCameraFrame；Dialog 關閉即 clearScanState 不再吃幀）；useScannedBarcode 填入表單欄；存檔寫 MoneyTransaction.barcode（可留空）。
  - **單元測試**：新增 AddTransactionViewModelTest——掃碼幀→scannedBarcode 設置、使用條碼→表單填入、存檔→fake repo 收到 barcode 且 source==manual、未填條碼存檔 barcode=null；既有測試零退化。
  - **請勿誤改**：(1) 手動記帳 source 恆為 manual、條碼是資料非來源——嚴禁因填了條碼把 source 改成 barcode；(2) CameraPreview 已為共用元件，ScanOverlayScreen 與 SimpleScanDialog 兩個掃碼 Dialog 不得各自複製相機程式碼；(3) AddTransactionSheet 捲動結構鐵律在條碼欄加入後依然成立（外層不捲動＋中段 weight(1f)＋非 Lazy CategoryGrid＋鍵盤固定底部）。

- **Session 42（release 掃碼修復＋「此商品記一筆」修復＋設定頁四開關，已完成）**：僅動 proguard-rules.pro、EntryFlowHost、EntryViewModel（onCameraFrame guard）、SettingsScreen／SettingsViewModel、strings.xml、EntryViewModelTest；AddTransactionSheet／AiAssistantSheet／StatsScreen／app/schemas 全程零 diff；無 schema 變更；versionCode／versionName 不動。
  - **Bug A（release 掃碼失效）根因**：debug 可掃、release 不可掃＝R8 混淆（isMinifyEnabled＋isShrinkResources）破壞 ML Kit bundled 掃描器，其未附 consumer keep 規則。修法＝proguard-rules.pro 補 keep（com.google.mlkit.**、com.google.android.gms.internal.mlkit_vision_**）＋dontwarn。
  - **Bug B（「此商品記一筆」無反應）根因**：①EntryFlowHost 的 onRecordThis 路徑沒設 scanVisible=false，相機不停；②onCameraFrame 無條件把 phase 蓋回 SCANNING，PREVIEW 被 ≤450ms 內的下一幀打掉、預覽 Sheet 立即拆除。修法＝onRecordThis 改 lambda 先收掃碼覆蓋層再 prepareScanDraft（scannedBarcode 在 state 中不會丟失）；onCameraFrame 開頭加 phase 白名單 guard（僅 IDLE／SCANNING 吃幀；Dialog 拆除與相機 unbind 有時間差，in-flight 幀可能在路上）。
  - **設定頁「記帳入口與物品」區**：四個 Switch 綁既有 SettingsRepository feature flags（aiNlEntryEnabled／aiImageEntryEnabled／barcodeEnabled／inventoryEnabled，setter 皆已存在），UI 慣例照抄「AI 助手」開關區（含說明文字）；關閉任一入口後快速記帳選單即時不再列出（visibleEntryOptions 已吃 flags）；inventoryEnabled 尚無消費端 UI（之後 session 接），開關只存設定。
  - **單元測試**：新增「掃碼草稿預覽期間不吃幀」（prepareScanDraft→PREVIEW→onCameraFrame→仍 PREVIEW）與「無購買紀錄條碼記一筆金額為零可編輯」（trend=null→金額 0→updateDraftAmount 可改）；測試環境無 Android framework，Bitmap 以 Unsafe.allocateInstance 配置（phase guard 在任何 bitmap 存取前返回）。
  - **請勿誤改**：(1) proguard ML Kit keep 規則勿刪——刪了 release 掃碼必失效；(2) onCameraFrame 的 phase 白名單 guard 勿移——幀不得覆寫 PREVIEW＋（含 WRITING／DONE）；(3)「此商品記一筆」路徑必須先收掃碼覆蓋層（scanVisible=false）再產草稿，否則相機不停、預覽被打掉。

- **Session 41-fix2（掃碼偵測不到條碼——幀內容層修復，已完成）**：實機回報相機預覽正常但對準 EAN-13 永遠「尚未偵測到條碼」；驗收方逐環審查確認掃描管線邏輯全部正確（rotation 有傳入、格式含 EAN-13、節流正確、client 重用、幀有進 ML Kit），問題在「幀內容層」——解析度不足／旋轉錯置／內部例外被吞。僅動掃碼相關檔案＋strings.xml；AddTransactionSheet／AiAssistantSheet 零 diff；無 schema 變更；versionCode／versionName 不動。
  - **根因**：(1) 頭號——ImageAnalysis 未設解析度，預設約 640×480，preview 與 analysis 串流各自協商，預覽清楚不代表分析幀夠大，EAN-13 條碼模組在 640 寬度下僅 1~2px 無法解析；(2) 次疑——analysis 未明確設定 targetRotation，rotationDegrees 可能錯置，ML Kit 收到旋轉 90° 的幀、1D 條碼垂直；(3) 三疑——MlKitBarcodeScannerSource 的 catch(Exception) 靜默回 NoBarcodeFound、無 log，內部出錯完全不可見。
  - **修法**：(1) ImageAnalysis.Builder 用 ResolutionSelector＋ResolutionStrategy(Size(1280,720), FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER) 明確要求解析度（CameraX 1.5 慣例；setTargetResolution 已棄用勿用）；bind 時把 analysis.targetRotation 設為當前 display rotation（取不到就 Surface.ROTATION_0 並 Log.w）；(2) catch(Exception) 保留回 Failure(NoBarcodeFound) 給 UI，但必配 Log.e("MlKitScan")；偵測結果為空時低頻 log（約每 2 秒一筆：bitmap 尺寸＋rotation＋結果數）；(3) analyzer 內 proxy.toBitmap() 後若 bitmap.config==HARDWARE 先 copy 成軟體 ARGB_8888 再送；(4) EntryViewModel.onFrame 加 in-flight 旗標——前一次 scan 未返回前跳過新幀（明確序列化，避免 ML Kit 併發呼叫），450ms 節流維持，reset() 一併清旗標；(5) 掃描卡引導文案下加小字「將條碼置中、距離拉近、保持穩定」（scan_hint_steady）。
  - **請勿誤改**：(1) ImageAnalysis 解析度勿降回預設——條碼偵測依賴幀寬度，640 寬度下 EAN-13 模組僅 1~2px 必然失敗；(2) 服務層 catch(Exception) 一律配 Log.e，不得靜默——UI 行為可回 fallback，但例外必須留痕。

- **Session 41-fix（實機測試三 bug 修復，已完成）**：僅修三個根因＋必要測試；AddTransactionSheet／AiAssistantSheet 零 diff；無 DB schema 變更；versionCode／versionName 不動；不加新依賴。
  - **Bug 1 掃碼全黑**：根因＝ScanOverlayScreen 的 CameraPreview 在 Dialog 內以 `viewContext as LifecycleOwner` 綁定相機——AndroidView factory 拿到的 context 是 ContextThemeWrapper，不是 LifecycleOwner，ClassCastException 被 runCatching 吞掉，相機從未綁定。修法＝composable 層取 `androidx.lifecycle.compose.LocalLifecycleOwner.current` 傳入 factory 閉包綁定；runCatching 保留但失敗時 Log.e，不得靜默。
  - **Bug 2 子流程關閉後 FAB 無反應**：根因＝EntryFlowHost 的 chooserVisible 於 onSelect 後設 false 從未設回 true，子流程取消只做 scanVisible=false／viewModel.reset()，OverviewScreen 的 showEntryFlow 卡在 true，FAB onClick 變 no-op。修法＝文字對話取消、掃碼覆蓋關閉、相片挑選取消（uri=null）、解析失敗（回 IDLE）一律把 chooserVisible 設回 true；confirmAll 完成（DONE）自動 onDismiss 關閉 host；防禦——host 可見但所有子畫面皆不可見（phase=IDLE 且無對話／掃碼）時自動重開選單。
  - **Bug 3 草稿分類／帳戶永遠 fallback「其他」「現金」**：根因＝parseTransactionsFromText／parseTransactionsFromImage 提示詞沒帶使用者既有分類／帳戶清單，模型發明名稱，DraftTransactionMapper 精確比對對不上。修法＝介面變更（理由：與既有 suggestForNote／interpretFilter 同慣例，讓模型只能選對名字，不改映射邏輯）——兩個 parse 方法新增 expenseCategories／incomeCategories／accountNames 參數；提示詞明確規定 category 只能從對應 type 清單選、account 只能從帳戶清單選，對不上填 null（既有 mapper fallback 接手），嚴禁發明清單外名稱；EntryViewModel 呼叫解析前以 CategoryRepository／AccountRepository 取最新清單傳入；所有 fake 實作同步更新，新增測試斷言收到的清單參數非空。
  - **請勿誤改**：(1) CameraX 綁定嚴禁 context cast LifecycleOwner（Dialog 內 context 是 ContextThemeWrapper，一律用 androidx.lifecycle.compose.LocalLifecycleOwner）；(2) EntryFlowHost 子流程取消必須重開選單（chooserVisible=true）或關閉 host（onDismiss），不得讓 showEntryFlow 卡 true 導致 FAB no-op。

- **Session 41（1.3.0 記帳三入口＋草稿預覽→確認寫入，已完成）**：僅交易寫入，物品表完全不碰；AddTransactionSheet／AiAssistantSheet 檔案零 diff；無 DB schema 變更。
  - **入口選擇**：總覽頁記帳 FAB 點擊後，若三個開關（ai_nl_entry／ai_image_entry／barcode）任一開啟，彈出小型選擇清單（EntryChooserSheet，skipHalfExpanded）：手動記帳（常駐選項，直接開 AddTransactionSheet）／AI 打字記帳／拍照記帳／掃碼記帳（只列開啟者）；全部關閉時不出選單、直接開 AddTransactionSheet（與現行為完全相同）。顯示邏輯抽成純函式 visibleEntryOptions(flags)。
  - **狀態機（EntryViewModel，@HiltViewModel）**：idle → scanning（掃碼幀）／parsing（文字／圖片解析）→ preview（草稿預覽）→ writing → done；AI／掃描呼叫皆在 viewModelScope 協程，不阻塞 UI thread。
  - **寫入映射（draft→entity 欄位表）**：TransactionDraft 經 domain 純函式 resolveDraftToTransaction 解析——type→type；amountCents→amountCents；note→note；categoryName→categoryId（名稱比對，對不上 fallback「其他」／「其他收入」，再 fallback 該類型第一個分類）；accountName→accountId（名稱比對，fallback 第一個帳戶）；dateEpochMillis→dateEpochMillis（null 用現在時間）；tags→tags；來源由入口帶入 source（打字→ai_text／拍照→ai_image／掃碼→barcode）與 barcode（掃碼時）；拍照額外帶 receiptImagePath（解碼縮圖存 filesDir/receipts/）。
  - **寫入唯一入口＝DraftPreviewSheet 的「全部確認」按鈕**：confirmAll() 走既有 TransactionRepository.addTransaction（UI 不碰 DAO）；解析完成（PREVIEW）時嚴禁寫入（測試驗證 fake repository 零呼叫）；剔除的草稿不寫入。
  - **掃碼記帳（ScanOverlayScreen 新檔案，全螢幕 Dialog 覆蓋）**：CameraX PreviewView（AndroidView）＋ImageAnalysis（STRATEGY_KEEP_ONLY_LATEST）；ImageProxy.toBitmap() 路徑（與 S40 scan(bitmap) 介面吻合），分析結束 finally close（否則畫面卡死）；scanner client 為 MlKitBarcodeScannerSource 的 lazy 重用欄位（@Singleton，禁止逐幀新建）；幀節流 450ms＋同碼去重（ScanThrottle 純函式）；掃到震動提示，下方價格趨勢卡（上次買／最低價／無購買紀錄），「此商品記一筆」以趨勢預填單筆草稿（金額參考上次價格）進入草稿預覽確認（source=barcode、預填 barcode）。相機權限 rememberLauncherForActivityResult 要求，拒絕時顯示引導（BarcodeScanError.CameraPermissionDenied）；Manifest 新增 CAMERA 權限。
  - **介面變更註明（S40 遺留的介面簽名）**：BarcodeScannerSource 新增 scan(bitmap, rotationDegrees) 重載（原 scan(bitmap) 保留為預設 0 度）——理由：CameraX 分析幀為感應器方向，ML Kit 需旋轉資訊才能穩定辨識；旋轉換算用 S40 的 resolveImageRotation 純函式。
  - **拍照記帳**：系統相片挑選器（PickVisualMedia，不需權限）→ ReceiptImageStore（新類，@Binds 注入）解碼縮圖存檔＋base64 → parseTransactionsFromImage → 草稿預覽（source=ai_image、receiptImagePath）。
  - **打字 AI 解析**：小型輸入對話 → parseTransactionsFromText → 草稿預覽（source=ai_text）。
  - **錯誤回饋**：相機權限拒絕（引導＋重試）、模型不支援 vision（AiError.VisionUnsupported→既有 ai_error_http 字串，專屬字串待 S42）、解析失敗（ai_error_parse／空草稿）皆有明確 UI 回饋，不閃退。
  - **單元測試**：EntryViewModelTest（PREVIEW 時零寫入／確認後呼叫次數與 source 正確／剔除不寫入／掃碼草稿帶 barcode／解析失敗不寫入，fake repository 驗證）、DraftTransactionMapperTest（source／barcode／receiptImagePath／分類 fallback 其他／帳戶 fallback／日期 fallback）、EntryOptionsTest（開關組合）、ScanThrottleTest（節流與去重）。
  - **請勿誤改**：(1) 寫入唯一入口＝草稿預覽的「全部確認」按鈕，確認前嚴禁任何 DB 寫入；(2) scanner client 為 MlKitBarcodeScannerSource 的重用欄位，禁止逐幀新建；(3) DraftPreviewSheet 是獨立新元件，與 AddTransactionSheet 無結構耦合，勿合併或改其捲動結構；(4) ScanOverlayScreen 的 ImageProxy 必定 finally close；(5) EntryFlowHost 的入口清單由 visibleEntryOptions 純函式決定，全關時直接開 AddTransactionSheet 的行為勿改。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），單元測試＋lint＋雙 APK 簽署由 GitHub Actions 執行。

- **Session 40（1.3.0 條碼服務層：唯讀查詢＋掃描封裝，已完成）**：僅資料層與掃描封裝，UI 零變更（掃描畫面與入口留 S41）、無 DB schema 變更（不加 v6、不動 5.json）、無任何 DB 寫入。
  - **依賴**：com.google.mlkit:barcode-scanning:17.3.0（bundled 版——離線可用、APK 約 +2~3MB；本 App 不上架 Play 且需離線可用，故不選需 Google Play Services 的 unbundled）；CameraX 1.5.1 三件（camera-camera2/camera-lifecycle/camera-view，本 session 僅鋪依賴，preview 留 S41）。
  - **條碼查詢介面（domain/repository/BarcodeRepository）**：getPurchaseHistory(barcode)→Flow<List<PurchaseSummary>>（金額／日期／備註／帳戶名，日期新→舊）；getPriceTrend(barcode)→Flow<PriceTrend>。
  - **PriceTrend 欄位**：lastPriceCents／lowestPriceCents／highestPriceCents／averagePriceCents（HALF_UP 取整）／distinctPriceCount／firstPurchaseEpochMillis／latestPurchaseEpochMillis；空資料全 null／0。
  - **DAO 唯讀查詢**：TransactionDao.purchaseHistory 觀察查詢（WHERE barcode = :barcode，LEFT JOIN accounts 取帳戶名）；不加 @Index（避免 schema 變更，資料量大時未來再議）；統計計算放 domain 層純函式 computePriceTrend（PriceTrendCalculator.kt），不用 SQL 彙總。
  - **掃描封裝**：domain/scanner/BarcodeScannerSource 介面（scan(bitmap)→BarcodeScanResult）；錯誤 sealed class BarcodeScanError（CameraPermissionDenied／DetectionTimeout／NoBarcodeFound——前兩者由 S41 掃描流程層判定，本 session 定義類型）；data/scanner/MlKitBarcodeScannerSource 以 ML Kit 實作（EAN-13/EAN-8/UPC-A/QR/Code-128，Task 以 suspendCancellableCoroutine 橋接不引入額外協程依賴），Hilt @Binds 注入；CameraX ImageProxy→InputImage 尺寸與旋轉換算為純函式（resolveImageRotation／resolveDisplayedSize／toImageInputSpec）。
  - **單元測試**：PriceTrendCalculatorTest（空／單筆／多筆／同價重複／平均四捨五入）、ImageInputConversionTest（旋轉正規化／寬高對調）、BarcodeRepositoryTest（fake DAO 驗證列→摘要對應與趨勢組裝；SQL 正確性由 Room KSP 編譯期驗證，沙箱無 Android SDK 故不引入 Robolectric，與既有 JVM 測試慣例一致）。
  - **請勿誤改**：(1) 趨勢計算必須保持純函式（可單元測試），唯讀查詢不得變成寫入；(2) BarcodeScannerSource 介面簽名 S41 會用，改動需於 PROGRESS.md 註明理由；(3) barcode 欄位查詢本 session 不加索引，未來若加需走 schema v6 遷移。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），單元測試＋lint＋雙 APK 簽署由 GitHub Actions 執行。

- **Session 39（1.3.0 第二階段：AI 記帳服務層，已完成）**：僅服務層，不碰 UI、不接開關（S41/S42 才接）、不加新依賴、無任何 DB 寫入。
  - **TransactionDraft 草稿模型**：type（EXPENSE/INCOME）、amountCents、note、categoryName（對不上既有分類時 null，由 UI fallback「其他」）、accountName nullable、dateEpochMillis nullable（模型只給自然語言日期時由服務層解析，無法解析回 null）、tags、rawText（原始對應片段，debug 用）。
  - **AiRepository 兩個新功能**（介面＋實作，Result<List<TransactionDraft>>＋AiCallException，風格比照 suggestForNote/interpretFilter）：parseTransactionsFromText（自然語言→草稿陣列，支援一次多筆，金額容錯「1,200」「120.5」「NT$300」「450元」，日期解析今天/昨天/前天/大前天/上週五/ISO／斜線日期，一律系統時區換算 epoch）；parseTransactionsFromImage（OpenAI 相容 vision content 陣列：text＋image_url data URI base64，解析截圖／發票／明細；HTTP 4xx（401/403/429 除外）回新錯誤 AiError.VisionUnsupported，字串資源之後 session 再接）。
  - **錯誤與邊界**：單筆欄位缺失不讓整批失敗（該筆以預設值照樣產出，確認流程補）；content 抽取失敗或 JSON 陣列完全無法解析才回 AiError.Parse。
  - **關鍵決策（Session 39）**：草稿回應元素以 {"type":"expense"...} 開頭，會被既有 sanitizeAiText／aiJsonFragmentRegex 誤判成思考區塊而清空——因此抽出共用 rawAiContent()（choices[0].message.content 三形態取用），草稿路徑走 request()＋extractDraftContent()（不做防禦性清理），chat／分析路徑的 extractAiContent() 行為完全不動。
  - **單元測試**：AiDraftParserTest 十二案例——金額格式、日期解析（固定基準日 2026-11-13 與 Asia/Taipei）、單筆／多筆／欄位缺失／markdown 圍籬容錯／完全無法解析、vision payload 形狀與 data URI、區塊清單 content 抽取、純字串 content 不清理。
  - **請勿誤改**：(1) TransactionDraft 是草稿模型，服務層嚴禁寫入 DB——寫入一律走之後 session 的「草稿預覽→使用者確認」流程（S41），這是防幻覺亂寫的底線；(2) vision 訊息 payload 形狀（content 陣列＋image_url data URI）是 API 相容關鍵，改形狀前先查目標 API 文件；(3) 草稿路徑不得改走 extractAiContent（會被防禦性清理清空，見上）。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），單元測試＋lint＋雙 APK 簽署由 GitHub Actions 執行。

- **Session 38（1.3.0 第一階段：資料層擴充，DB v4 → v5，已完成）**：里程碑「AI 記帳（自然語言／圖片／條碼寫入）＋物品管理」啟動，本 session 僅做資料層，不碰任何 ui/ 檔案、不做任何 AI 呼叫、不加任何新第三方依賴（掃描器／相機留待之後 session）。
  - **TransactionEntity 三新欄位**：source（TEXT NOT NULL，遷移預設 'manual'；語意值僅四個：manual = 手動輸入、ai_text = 自然語言記帳、ai_image = 圖片記帳、barcode = 條碼記帳）、barcode（TEXT nullable，商品條碼）、receiptImagePath（TEXT nullable，憑證圖片路徑，之後 session 使用）。Domain 層對應 TransactionSource enum（MANUAL／AI_TEXT／AI_IMAGE／BARCODE），mapper 對未知字串容錯為 MANUAL，MoneyTransaction 新欄位皆有預設值，既有 UI 呼叫處零改動。
  - **items 新表（物品管理）**：id PK autoGenerate、name NOT NULL、barcode nullable、storageLocation nullable、quantity INTEGER NOT NULL DEFAULT 1、expiryDateEpochMillis nullable、purchaseTransactionId nullable（FK transactions.id，onDelete = SET_NULL——交易刪除時物品保留、關聯歸 NULL）、createdAtEpochMillis／updatedAtEpochMillis；索引：barcode、expiryDateEpochMillis。對應 Item domain model（quantity 不可為負、名稱不可空白）、ItemDao（observeAll／findByBarcode／findById／insert／update／delete）、ItemRepository ＋ ItemRepositoryImpl（@Binds 綁定於 RepositoryModule）。
  - **TransactionDao 補方法**：observeByBarcode(barcode)（依日期新→舊排序的歷史交易），TransactionRepository 對應 observeTransactionsByBarcode。
  - **DataStore 四個功能開關（SettingsRepository，預設皆 true，本 session 不接 UI）**：ai_nl_entry_enabled（自然語言記帳）、ai_image_entry_enabled（圖片記帳）、barcode_enabled（條碼）、inventory_enabled（物品管理）；key 常數與純函式 resolveFeatureFlags(prefs) 定義於 SettingsRepositoryImpl companion（JVM 單元測試直接驗證讀寫與預設值），observeFeatureFlags ＋ 四個 setter。
  - **遷移與 schema**：MIGRATION_4_5 為標準 Migration 物件（ALTER TABLE 加三欄＋CREATE TABLE items＋兩索引），SQL 定義為 MIGRATION_4_5_SQL 常數供單元測試結構驗證；資料庫版本 4 → 5，exportSchema = true，CI 於建置後將 app/schemas/ 的 v5 JSON commit 回倉庫。舊版 APK（DB v4）覆蓋升級不丟資料（既有欄位不動、新欄位給預設值）。
  - **單元測試**：ItemMapperTest（Item entity↔domain 往返、交易三新欄位映射與未知 source 容錯）、Migration4to5SqlTest（遷移 SQL 七條語句結構、source 預設 'manual'、items 欄位齊全、quantity 預設 1、FK SET_NULL、雙索引、無破壞性語句）、FeatureFlagsTest（四開關預設 true、寫入 false 讀回 false、單一開關獨立、key 命名）。
  - **請勿誤改**：(1) source 欄位四個語意值（manual／ai_text／ai_image／barcode）是之後 AI 寫入來源標記的基礎，不得未經規劃擴充語意；(2) fallbackToDestructiveMigration 禁用（既有鐵律重申，遷移一律用標準 Migration 物件）；(3) 四個開關之後由設定頁 UI 接上，本 session 不接，勿提前動 ui/。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），單元測試＋lint＋雙 APK 簽署由 GitHub Actions 執行。

- **Session 37（settings_version_footer 錯字修正：U+5E37 → U+5E7B，已完成）**：Session 36 的版本資訊字串「幻帳」誤植為「帷帳」（帷 U+5E37）——上輪排查時誤把 U+5E37 當成「幻」，誤判無錯字；經使用者指正後以 Unihan 官方資料查證：U+5E37 kMandarin=wéi（tent; curtain, screen）＝帷、U+5E7B kMandarin=huàn（illusion, fantasy, mirage）＝幻，確認錯字存在並修正。
  - **修正**：strings.xml 的 settings_version_footer 第一字元 U+5E37（帷）→ U+5E7B（幻），僅動該字元；同一行註解與 %1$s／%2$d 格式參數未動。修改後以碼點驗證輸出 ['0x5e7b', '0x5e33', ...]。
  - **教訓（請勿重蹈）**：判斷相似字元一律以碼點對照 Unicode／Unihan 表（kMandarin 讀音＋kDefinition 釋義），勿憑印象或輸出轉義猜測字形；CJK 擴充名稱（CJK UNIFIED IDEOGRAPH-XXXX）不含讀音，不能作為判定依據。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），單元測試＋lint＋雙 APK 簽署由 GitHub Actions 執行。
- **Session 36（設定頁尾端版本資訊：versionName + versionCode，已完成）**：曾發生「使用者拿舊 APK 實測、誤判修復失敗」的事故——在設定頁清單最尾端（備份與還原 section 之後，不新開 section）加一個不可點擊的小字版本資訊「幻帳 1.2.0 (build <流水號>)」，實測時可直接對照 build 版本。
  - **版本取得**：context.packageManager.getPackageInfo(context.packageName, 0)，versionName ＋ versionCode，VersionFooter composable 內 remember 取一次；versionCode 以 Build.VERSION_CODES.P 分界取 longVersionCode／versionCode（minSdk 26 相容）。
  - **樣式**：bodySmall＋onSurfaceVariant（colorScheme 取用）＋置中＋上下 8dp padding；不可點擊、無互動。
  - **字串資源**：settings_version_footer「幻帳 %1$s (build %2$d)」（values/strings.xml，繁中預設），無 hardcode 字串與色碼。
  - **請勿誤改**：versionCode 即 GITHUB_RUN_NUMBER build 流水號，勿改為固定值。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），單元測試＋lint＋雙 APK 簽署由 GitHub Actions 執行。
- **Session 35（versionName 1.2.0 收尾：體驗優化里程碑完成，已完成）**：1.1.0 的體驗優化里程碑全部驗收完成——UI/UX 十項改進＋AI 對話升級（reasoning_effort／區塊去重／全文去重）＋Session 34 重複顯示修復（MarkdownText 渲染層根因），依里程碑收尾慣例將 versionName 1.1.0 → 1.2.0。
  - **改動（僅版本字串）**：app/build.gradle.kts versionName "1.1.0" → "1.2.0"；versionCode 維持 GITHUB_RUN_NUMBER 覆寫邏輯不動。
  - **全庫排查 "1.1.0"**：docs/research.md 與 gradle/libs.versions.toml 的 1.1.0 是 androidx.security:security-crypto 函式庫版本，與 App 版本無關，未動；PROGRESS.md Session 18 為歷史紀錄保留；SPEC.md／README 未列 App 版本號，無需更新。
  - **驗收**：GitHub Actions 全綠，Release tag v1.2.0-<流水號>，雙 APK 可從 Release 頁下載。
- **Session 34（AI 回覆「整段／開頭句重複」最終根因修復：MarkdownText 渲染迴圈 Plain 段雙重 append，已完成）**：多輪追查（Session 31–33 皆往模型端／內容層修）後，於渲染層查實真兇——MarkdownText.kt 的 segment 渲染迴圈中，when 的 MdSegment.Plain 分支內先 append(segment.text) 一次，迴圈下方又無條件 append(segment.text) 一次，每個純文字段落因此顯示兩次；DB 原文僅一份，模型端與 parseMarkdown 解析層皆正常。
  - **修復（僅改 MarkdownText.kt 與 MarkdownParserTest.kt）**：when 的 Plain 分支改為 Unit（不做事），保留迴圈下方統一的 append(segment.text)——每個 segment 恰好 append 一次；Bold／Italic／Code 分支只 pushStyle、靠下方 append，行為完全不變。parseMarkdown 一字未動。
  - **可測性重構**：單行 → AnnotatedString 的組字邏輯抽成 internal 純函式 buildMdAnnotatedString(line, codeBackground, codeForeground)，MarkdownText composable 改呼叫它（remember 快取照舊）。
  - **單元測試**：新增 MarkdownRenderTest 六案——單一 Plain 段輸出恰等原文不重複、「abc **bold** def」輸出 "abc bold def" 且 bold 與粗體 span 各一次、bullet 前綴（•）與正文各一次、有序清單編號加粗一次正文一次、標題行正文一次且套標題樣式、行內程式碼文字與 Monospace 樣式各一次；既有 parseMarkdown 測試未動。
  - **勿改警示（已寫入 MarkdownText.kt 檔頭與 buildMdAnnotatedString）**：渲染時每個 segment 恰好 append 一次——when 分支只負責 pushStyle，不得在分支內 append（Session 34 修正的重複渲染根因，請勿誤改回）。
  - **防禦性設計保留**：Session 31 三形態解析與 sanitize、Session 32 reasoning_effort=none 與 text 區塊重疊去重、Session 33 collapseFullTextDuplication 全部保留——模型端防禦仍有價值，本次根因在渲染層。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），單元測試＋lint＋雙 APK 簽署由 GitHub Actions 執行。
- **Session 33（AI 回覆整段重複兩次修復：全文層重複摺疊＋chat 溫度分流，已完成）**：實測截圖確認 AI 回覆整段文字在同一則訊息內重複兩次；區塊層 mergeBlocksWithOverlapDedup 無效，因為重複發生在單一 text 區塊內部——Mistral 上 reasoning 模型（尤其 GLM 5.3 家族）的已知模型端 bug：reasoning 洩漏進 text 通道後退化成重複迴圈，官方 reasoning_effort="none" 對 GLM 系也壓不掉（content 永遠是 chunk 清單）。參考：github.com/mawoh/pi-mistral-native/issues/27、zai-org/feedback#819、docs.mistral.ai/studio/conversations/reasoning。用戶端唯一解法是最後一道的防禦性全文去重。
  - **修復（僅改 AiRepositoryImpl.kt 與 AiContentExtractTest.kt）**：extractAiContent() 的輸出端（sanitizeAiText 之後、回傳前）新增全文層 collapseFullTextDuplication()——(1) 整段兩半完全逐字相同（段落或中點切分，容許中間一個分隔字元）只保留一份；(2) 段落序列去重，與先前完整段落完全相同者丟棄；處理後空白回 null。
  - **請勿誤改**：全文層去重的「完全逐字相同」比對＋「重複段落長度 >= 20 字元」門檻是刻意保守設計，不可改成相似度模糊比對、不可刪短句（避免誤刪「好」「1」等短行與列點開頭語等模型刻意的重複表述）。Session 31 三形態解析、Session 32 區塊層去重、sanitizeAiText 只碰「[」開頭陣列的行為全部保留未動。
  - **溫度分流補落地**：request() 的 temperature 改為參數（預設分析類 0.4，Session 30 既有值不變）；chat() 類呼叫傳 0.65（對話類較高溫讓口語回覆更自然）；分析類（polishNote/suggestForNote/interpretFilter/analyzeData/analyzeWeek/askQuestion）維持 0.4。400 階梯重試邏輯不變。
  - **單元測試**：AiContentExtractTest 補四案——單區塊內整段重複兩次摺疊為一份、兩個完全相同長 text 區塊仍只出一份（防回歸）、列點內容含重複開頭但語意不同的行不可被誤刪、純字串 content 帶段落級重複正常摺疊。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行。

- **Session 32（AI 回覆開頭句子重複治本：reasoning_effort 階梯＋text 區塊重疊去重，已完成）**：mistral-medium/large-latest 是推理模型，message.content 是「thinking 區塊＋text 區塊」清單；多個 text 區塊內容重疊（短區塊是長區塊的前綴）時，原串接邏輯造成回覆開頭同一句連續重複兩次。
  - **reasoning_effort 階梯（請勿誤改）**：request() 新增 reasoningEffort 參數（預設 null 不帶參數）。對話類 chat() 兩條路徑（純聊天／附記帳資料）都帶 `reasoning_effort="none"`——medium/small 系列支援，content 直接變純字串、thinking 完全關閉、最乾淨。400 階梯（最多兩層重試）：none → "low" → 移除 sampling 參數的裸 body；再失敗才回 AiError.Http(400)。分析類呼叫（analyzeWeek／analyzeData 等，reasoningEffort=null）行為不變：帶 sampling 參數，400 時改裸 body 重試一次。
  - **text 區塊重疊去重（請勿誤改）**：extractAiContent() 串接多個 text 區塊前，用 mergeBlocksWithOverlapDedup() 檢查——後一塊以已累積內容為開頭（或完全相同）時丟棄較短者只保留最長；這是區塊層去重，不做任意句子層去重，避免誤刪模型刻意的重複表述。
  - **三形態解析與 sanitize 防禦全部保留**（Session 31 決策延續）；請求參數除 reasoning_effort 外未動，prompt 未動。
  - **單元測試**：AiContentExtractTest 補三個 fixture——重疊 text 區塊去重保留最長、相同區塊只出現一次、reasoning_effort=none 純字串回應行為不變。
  - **驗收**：mistral-medium-latest 與 mistral-large-latest 同一問題各問 3 次，回覆開頭不再出現連續重複句子，無 thinking 洩漏；大測試問題（如「我這個月花最多的是什麼？」）與閒聊問題都正常。

- **Session 31（AI 回覆洩漏 thinking 區塊與原始 JSON 修復，已完成）**：mistral-medium-latest／mistral-large-latest 回傳的 choices[0].message.content 可能是「內容區塊陣列」或「內容區塊物件」而非純字串，原 extractContent() 只以純字串處理，拿到區塊結構時整包洩漏到 UI。修復（延續 Session 10「解析 choices[0].message.content」決策的相容性擴充，只改 AiRepositoryImpl.kt）：
  - **三種 content 形態支援**：a. 純字串 → 直接使用（Session 10 既有行為保留）；b. JSON 陣列 → 只串接 type == "text" 區塊的 text 欄位；c. JSON 物件 → 只取 text 欄位。thinking 區塊一律丟棄，絕不顯示。
  - **防禦性清理**：取出正文後，殘留「{"type":」開頭的可疑 JSON 片段——能解析就抽 text，無法解析則移除該片段；整段為雙重編碼的內容區塊陣列字串（「[」開頭且可解析為 JSONArray）也會結構化抽出正文。只處理「[」開頭的整段字串，不碰「{」開頭者——AI 篩選／分類建議本來就回傳 JSON 物件，不可誤傷。
  - **單元測試**：新增 AiContentExtractTest（app/src/test/.../data/repository/），涵蓋純字串／thinking+text 陣列／物件形態、雙重編碼陣列、純字串殘留 JSON 片段抽取、thinking 專屬陣列回 null、無法解析回 null。
  - **測試依賴**：app/build.gradle.kts 加 `testImplementation(libs.org.json)`（org.json:json 20240303，version catalog 新增 orgJson = "20240303"）——僅測試 classpath，JVM 單元測試需要真實 org.json 實作（android.jar stub 會擋）；App 執行期仍用平台內建，零新執行期依賴。
  - **驗證方式**：沙箱無 JDK/Android SDK，lint（Error 擋建置）＋單元測試＋雙 APK 簽署由 GitHub Actions 執行；實機驗收＝mistral-large-latest 與 mistral-medium-latest 各測一次，回覆只顯示正文、無 JSON 片段、無內部思考文字。

- **Session 30（AI 回覆片語重複修復：取樣懲罰參數＋400 相容性重試，已完成）**：修復實機 AI 回覆出現片語層級重複（例：「建議確認是否有未記錄的交易。建議確認是否有未記錄的交易。」）。App 端 chat()→callRaw()→extractContent()→MarkdownText 渲染鏈逐層檢查無重複拼接邏輯，重複來源為模型輸出本身退化；原 request() 請求 body 只有 model / messages / temperature(0.4)，無任何重複懲罰參數。
  - **懲罰參數（請勿誤改）**：AiRepositoryImpl.request() 的 body 新增 `frequency_penalty: 0.3`、`presence_penalty: 0.2`、`max_tokens: 1024`（對話與分析類呼叫一律生效，含 analyzeWeek 的 execute() 路徑，JSON 輸出解析不受影響）。**勿調高或移除**——0.3/0.2 為抑制片語重複又不傷正常內容的保守值；max_tokens 1024 對本 App 回覆長度已足夠，過長回覆本來也無必要。
  - **400 相容性重試（請勿誤改）**：部分 OpenAI 相容端點對不支援的參數會回 400。request() 收到 400 時，移除 frequency_penalty / presence_penalty / max_tokens 後**只重試一次（不迴圈）**，重試再失敗才回 AiError.Http(400)。勿改成無限重試或對其他狀態碼也重試——401/403/429 的既有錯誤分流不受影響。
  - **chat() prompt**：systemPrompt 尾端加「同一句話或相似句子不要重複出現。」（僅 chat()；polishNote / suggestForNote / interpretFilter / analyzeData / analyzeWeek 的 prompt 未動）。
  - **範圍控制**：僅改 AiRepositoryImpl.kt（request() 與 chat()）與本文件，其他檔案未動；AddTransactionSheet 與 AiAssistantSheet 的捲動／sheetState 結構未觸碰。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），lint（Error 擋建置）＋單元測試＋雙 APK 簽署由 GitHub Actions 執行；實機驗收＝同一問題（如「我這個月用了多少錢」）重複詢問 2–3 次，回覆不再出現片語重複。

- **Session 29（AI 對話 session 生命週期重構：急切建立→延遲建立＋洞察 badge 已讀語意，已完成）**：修正「對話刪不掉／清空後又冒新對話／越積越多」「不自動命名」「✨ 圓點關不掉」三組實機症狀的共同根因。
  - **延遲建立（核心決策，請勿誤改）**：currentSessionId == 0 定義為「無進行中對話」——UI 顯示空狀態、輸入照常可用；**只有送出第一則訊息（send 路徑的 ensureSessionForSend）才建立 session**；newSession() 只把 DataStore 的 currentSessionId 歸零、絕不建 DB 記錄；deleteSession 刪目前對話→歸零不補建；clearAll 全刪→歸零不補建。DataStore currentSessionId 為單一事實來源（syncActiveSession 以它同步，多 VM 實例不會競態各建）。**勿把 session 建立加回 newSession/deleteSession/clearAll/init**。
  - **髒資料清理**：VM init 一次性呼叫 chatRepository.deleteEmptySessions()（SQL：刪除零訊息的空 session），之後因延遲建立不會再產生空 session。
  - **自動命名修正**：改為送出第一則 USER 訊息時以「使用者問題首行截 20 字」命名（ChatTitle.from(question)，比 AI 回覆更有意義）；命名前直接 chatRepository.getSession() 查 DB 判斷 title 是否為空（不讀 UI state，避免競態）；既有空 title 舊對話 UI fallback 顯示「新對話」（AiAssistantSheet ifBlank）。
  - **交易保護**：clearAll 的兩表刪除包進 SessionDao @Transaction clearAllTables()；deleteSession 走 @Transaction deleteByIdWithMessages()（訊息由 FK CASCADE 一併刪）。
  - **洞察 badge 已讀語意**：圓點＝「有尚未查看的洞察」——OverviewViewModel.insightUnread = combine(observeLastSeenInsight, aiRepository.observeHistory){ 最新洞察時間 > 最後查看時間 }；AiResultDialog 顯示過（含按關閉，LaunchedEffect 於 UiState.Insight 時）即 markInsightSeen() 寫入 DataStore last_seen_insight；重啟不重現、重新分析產生新洞察才再出現。
  - **新增測試依賴（測試專用）**：kotlinx-coroutines-test 1.10.2（Kotlin 官方、testImplementation，App 執行期零新依賴）——JVM 測試 ViewModel 需 Dispatchers.setMain。
  - **測試**：新增 AiChatViewModelTest 7 案例（啟動清理空 session、send 延遲建立＋以問題命名、既有標題不覆寫、newSession 歸零不建、反覆 newSession 零空 session、刪目前對話不補建、clearAll 不補建），以記憶體 fake 的 Chat/Settings/Ai Repository 驗證生命週期；既有 ChatTitleTest 等全數保留。
  - **零回歸確認**：跨頁同步（DB flow）、鍵盤行為（weight(1f)＋imePadding）、skipPartiallyExpanded = true、四開關即時生效皆未動。
  - **驗證**：沙箱無 JDK，括號平衡以 Python 檢查；lint、單元測試、雙 APK 由 GitHub Actions 執行。實機驗收：清空後列表為空無殘留、刪目前對話後可直接發問、反覆新對話零空 session、第一則提問自動命名、✨ 圓點看完即消失且重啟不重現。

- **Session 28（任務 B：AI 對話持久化＋跨頁同步＋對話管理＋設定開關；任務 A：總覽／明細／統計三頁重排，已完成）**：DB v3→v4 遷移、AiChatViewModel 改 DB flow 驅動、AiAssistantSheet 全開修復與對話管理 UI、設定頁四開關、三頁空間利用率重排。
  - **B0（首次開啟即全開，請勿誤改）**：AiAssistantSheet 的 ModalBottomSheet 改傳 `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`——M3 預設半開會讓輸入列落在裁切範圍外。**勿移除此 sheetState 設定**；既有 weight(1f)＋imePadding＋navigationBarsPadding 鍵盤行為全部保留（Session 26/27 的修復零回歸）。
  - **B1（DB v4 schema）**：新增 `chat_sessions`（id/title/createdAtEpochMillis/updatedAtEpochMillis）與 `chat_messages`（id/sessionId FK CASCADE/role USER|AI/text/createdAtEpochMillis，sessionId 有索引）；HmoneyDatabase version 4＋手寫 MIGRATION_3_4（對照 MIGRATION_2_3 模式）併入 MIGRATIONS；SessionDao／ChatMessageDao；ChatRepository（domain 介面＋ChatRepositoryImpl＋Hilt @Binds）；保護：每次 addMessage 後 trimToLatest(200)——單 session 超過 200 則刪最舊。
  - **B2（單一資料來源同步架構，請勿誤改）**：AiChatViewModel 訊息改由 ChatRepository 的 DB flow 驅動——各頁 AiAssistantSheet 觀察同一份 flow 天然跨頁同步；目前 session id 存 DataStore（settings DataStore 的 ai_current_session_id），關掉重開仍在同一對話；send() 寫 USER 訊息→呼叫 AI→寫 AI 訊息，AI 回覆首行截 20 字為 title（ChatTitle.from，純邏輯可測試；空 title＝未命名，UI 顯示「新對話」）。**記憶體路徑清單**：AiChatViewModel 原本純 StateFlow 訊息路徑已完全移除；唯一例外是「保留對話」關閉時（keepHistory=false）刻意走純記憶體（ ephemeralCounter 負數 id、不寫 DB、離開即消失），這是設定開關的預期行為，非殘留。
  - **B3（對話管理 UI）**：頂部功能列＝新對話（AddComment）＋對話選擇 dropdown（Forum，可切換、尾端垃圾桶刪除單一對話附確認框）＋清空所有對話（DeleteSweep，確認框）；保留關閉時隱藏對話選擇；訊息氣泡長按 combinedClickable→DropdownMenu 複製（LocalClipboardManager）／刪除單則；快捷提問 4 chips（我這個月花最多的是什麼？／本月收支概況／檢查這週有沒有異常支出／給我省錢建議）點擊帶入輸入框可編輯，受設定開關控制顯示。
  - **B4（設定頁「AI 助手」區塊，DataStore 開關）**：對話時附上我的財務資料（預設開；關閉＝chat 不組財務 context，純聊天）、保留對話記錄（預設開；關閉＝純記憶體）、自動清除 30 天前對話（預設開；ChatCleanupWorker 每日跑，沿用 WorkScheduler 排程，關閉時 worker 直接 return）、顯示快捷提問（預設開）。AiRepository.chat 簽名改為 `chat(question, includeContext)`。
  - **A1 總覽頁**：「本週 AI 洞察」大卡與未設定引導卡移除，改為標題列右側 ✨ 圓形圖示入口（與齒輪並排）——有新洞察（UiState.Insight）時顯示小圓點，點擊歸零並觸發 weekInsight，結果照舊 AiResultDialog（未設定 AI 時由對話框引導前往設定）；預算卡「剩餘／每日可用」兩列合併為同一列左右對照；帳戶餘額維持單行 LazyRow 小卡；近期明細上移（首屏 10 秒內可見收支與最近幾筆）。數字滾動、玻璃卡、AutoHideFabColumn、FAB 行為零改動。
  - **A2 明細頁（篩選收合，請勿誤改）**：首屏只剩「搜尋列＋篩選按鈕（FilterList，有篩選時主色＋圓點）」，已套用篩選另以一列摘要 chips 呈現（類型／帳戶／分類／期間，點 chip 即移除該條件）；全部篩選條件集中於 ModalBottomSheet（skipHalfExpanded，沿用既有 FilterChip 與 AI 自然語言篩選、期間對話框短日期格式 widthIn(160.dp)）；批次分析移入篩選 sheet。**TransactionsViewModel 與篩選邏輯完全未動**，僅呈現層重排。
  - **A3 統計頁**：空狀態整併為單一置中 StatsChartEmptyState（hasData = slices/ranking/trend 皆無資料），無資料時三張圖表卡不渲染；時間切換 SegmentedButton(weight 1f)＋帳戶下拉（FilterChip＋DropdownMenu）＋AI 分析本期縮為工具列右側 ✨ 圖示按鈕，壓縮為一條工具列；排行榜卡 contentPadding 縮 12dp；CategoryPalette 與圖表點擊詳情（Snackbar）沿用。
  - **驗證**：沙箱無 JDK/Android SDK，括號平衡與引用檢查以 Python 完成；lint、單元測試（新增 ChatTitleTest 3 案例，既有 23 案例不受影響）、雙 APK 由 GitHub Actions 執行。實機驗收：DB v3 舊版升級不丟資料（MIGRATION_3_4）、全新安裝正常、AI sheet 首開即全開輸入框可見、跨頁同一份對話、四開關即時生效（保留關閉不寫入、附資料關閉提問不含個資）、深淺色與 fontScale 1.3。
  - **遺留事項**：實機驗證上述清單；「自動清除」首次清理於排程觸發後生效。

- **Session 27（AI 助手輸入框被鍵盤遮擋最終修復：weight(1f) 取代固定高度，已完成）**：只動 AiAssistantSheet.kt，其他檔案未碰。
  - **根因（請勿誤改警示）**：訊息列表用固定高度 `heightIn(min = 200.dp, max = 420.dp)` 而非 `weight(1f)`——訊息一多列表撑高，Column 總高超過鍵盤彈出後的可見空間，Column 本身不捲動，底部輸入列被推出畫面外。imePadding 位置沒問題，**高度策略才是根因；勿把固定 heightIn(max) 加回來**。
  - **修復**：訊息 LazyColumn 高度改 `Modifier.weight(1f).heightIn(min = 160.dp)`——Column 內有 weight 子項後會填滿 sheet 剩餘高度，鍵盤彈出（imePadding 生效）時列表自動壓縮，輸入列永遠可見；移除 max = 420.dp 固定上限。空狀態 min = 160.dp 不會讓 sheet 空一大段。Column 底部 padding 由 20dp 縮為 12dp（輸入列與 IME 間距）。
  - **自動捲底強化**：LaunchedEffect 觀察鍵改為 `(state.messages.size, state.loading, imeBottom)`，其中 `imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)`——鍵盤彈出時也捲到最新一則（需 @OptIn ExperimentalLayoutApi）。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡以 Python 檢查；lint＋單元測試＋雙 APK 由 GitHub Actions 執行；實機請驗證連問 3–5 則後點輸入框，輸入框完整可見、最新訊息可見、橫豎屏正常。

- **Session 26（三項 UI 修復：明細期間列壓縮、AI 對話鍵盤遮擋、設定移出底部分頁，已完成）**：只動 UI 顯示層與 Manifest，不動資料層、AddTransactionSheet 捲動結構與 CategoryGrid。
  - **Bug 1（期間選定後批次分析按鈕被壓縮，請勿誤改）**：根因＝「期間篩選與批次分析」同列（普通 Row），期間 chip 完整日期字串把「全部」與「批次分析」擠壓截斷。修復＝拆成兩列（外層 `Column(verticalArrangement = Arrangement.spacedBy(8.dp))`）：第一列期間 FilterChip＋「全部」TextButton；第二列批次分析 AssistChip（永遠完整可見可點）。期間 chip label 改短格式 `11/12–11/23`（Formats 新增 `shortDate(epochMillis)`，例：11/12）＋`widthIn(max = 160.dp)`＋`maxLines = 1`＋`TextOverflow.Ellipsis`。**勿把兩列合回單一 Row、勿移除 widthIn/Ellipsis**——否則長日期又會擠壓批次分析按鈕。選定日期後點 chip 重新開啟既有期間對話框，流程不變。
  - **Bug 2（AI 對話輸入框被鍵盤遮擋，請勿誤改）**：修法四件套缺一不可——(1) AndroidManifest MainActivity 加 `android:windowSoftInputMode="adjustResize"`（edge-to-edge 專案搭配 imePadding 的前提）；(2) AiAssistantSheet 外層 Column 的 `navigationBarsPadding().imePadding()` 保留（ModalBottomSheet content 直接內層）；(3) LazyColumn 改 `rememberLazyListState()`＋`LaunchedEffect(state.messages.size, state.loading)` 自動 `animateScrollToItem(totalItemsCount - 1)`（鍵盤彈出與新訊息都捲到底）；(4) 訊息列表高度 `heightIn(min = 200.dp, max = 420.dp)`（鍵盤彈出時列表壓縮而非輸入列被頂出）。**勿依賴特定鍵盤高度 hardcode**——跨廠牌（Samsung/Gboard）自適應靠 insets 系統而非固定 dp。
  - **任務 3（設定移出底部分頁，請勿誤改）**：底部分頁五改四（總覽、明細、統計、帳戶）——TopLevelDestination 移除 SETTINGS 項；設定 route 以頂層常數 `SETTINGS_ROUTE = "settings"` 為準（ui/navigation/TopLevelDestination.kt），**勿再加回 SETTINGS 分頁**。四分頁標題列右側各加齒輪 IconButton（Icons.Outlined.Settings，contentDescription = tab_settings）導向設定頁；SettingsScreen 新增 `onBack` 參數＋返回箭頭列（ArrowBack，popBackStack）。各頁既有 onGoSettings 導航流程（AI 未設定引導等）全部保持可用。
  - **新字串**：common_back（返回）。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡與 import 檢查以 Python 完成；lint＋單元測試＋雙 APK 由 GitHub Actions 執行；實機請驗證 Gboard 與三星鍵盤下輸入框可見、四頁齒輪進設定、設定頁返回正常。

- **Session 25（期間選取器「結束日期」右側截斷最終修復，已完成）**：只動 TransactionsScreen 的期間對話框與 strings.xml，功能流程完全不變。
  - **根因（請勿誤改警示）**：寬度被三層邊距疊掉——AlertDialog 內建 padding＋自加 16dp＋picker 內建邊距；且 DateRangePicker 的內建 headline（開始–結束日期）是 M3 內部固定排版，窄螢幕或大字體（fontScale）下右側結束日期被截斷。**勿把 AlertDialog 的 horizontal 16dp padding 加回去**——與內建 padding 疊加正是寬度不足主因。
  - **修復 1（自繪 headline）**：DateRangePicker 傳 `title = {}` 與 `headline = {}` 捨棄內建排版，改為 picker 上方自繪 `DateRangeSelectionRow`：Row 內左右兩個欄位各 `Modifier.weight(1f)`（任何寬度與 fontScale 皆等寬、不重疊），左「開始」右「結束」（labelSmall＋onSurfaceVariant），中間 ArrowForward 小圖示；日期以 Formats.date 顯示、未選顯示「尚未選擇」；文字 maxLines=1＋TextOverflow.Ellipsis（最多省略號、絕不溢出）。
  - **修復 2（容器改自組）**：捨棄 AlertDialog，改 `Dialog(usePlatformDefaultWidth = false)`＋`Surface(fillMaxWidth().widthIn(max = 480.dp), shape = extraLarge, color = surfaceContainerHigh, tonalElevation = 6.dp)` 自組容器——平板寬度上限 480dp 不過寬，手機全寬無三層邊距疊加；內部 Column padding(16.dp) 統一留白，標題「期間」titleLarge，底部取消／確定 TextButton 行為與 setDateRange 流程未動（含結束日 23:59 換算）。
  - **新字串**：transactions_filter_start（開始）、transactions_filter_end（結束）、transactions_filter_not_selected（尚未選擇）。
  - **記帳頁**：確認無 DateRangePicker（單日 DatePickerDialog），不需改。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡以 Python 檢查；lint＋單元測試＋雙 APK 由 GitHub Actions 執行；實機請以 fontScale 1.3（設定→顯示→字體最大）驗證雙欄完整顯示。

（期間選取器寬度重疊修復＋MarkdownText 擴充：有序清單／分隔線／標題分級，已完成）**：只動 TransactionsScreen 的期間選取對話框與 MarkdownText，不動資料層與 AddTransactionSheet。
  - **Bug 1（期間選取器雙欄標頭重疊）**：根因＝M3 AlertDialog 預設對話框寬度上限，DateRangePicker 內部「開始日／結束日」雙欄標頭在窄螢幕互相擠壓。修復＝該 AlertDialog 加 `properties = DialogProperties(usePlatformDefaultWidth = false)`＋`modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)`，DateRangePicker 維持 `fillMaxWidth().heightIn(max = 480.dp)`，並設 `title = {}` 移除與 AlertDialog「選取日期」重複的內建標頭。全專案僅明細頁有 DateRangePicker（記帳頁是單日 DatePickerDialog，無此問題），setDateRange 流程未動。
  - **Bug 2（MarkdownText 擴充，請勿誤改）**：parseMarkdown 新增支援——(1) 分隔線：整行僅 3 個以上連續「-」「*」「—」（允許前後空白）→ MdLine.divider，渲染 HorizontalDivider 上下 8dp padding，不印字元；(2) 有序清單：行首「數字. 」→ MdLine.orderedNumber 保留編號（渲染粗體「1. 」前綴），內容走既有行內解析，**不疊圓點**；(3) 標題收尾 hash：「### 標題 ##」行尾以空白分隔的裝飾 hash 剝除（stripTrailingHashes，保守：無空白分隔不動）；(4) 標題分級：headingLevel 1–3 對應 fontSize 20/18/16 sp（皆粗體）。**擴充後仍維持零正則、零依賴、保守字串掃描、不拋例外——不得改為正則或第三方套件**。渲染結構由單一 Text 改為 Column（每行一個 Text＋分隔線插 HorizontalDivider），呼叫端簽名不變。
  - **新測試**：MarkdownParserTest 補 4 案例（分隔線／有序清單保留編號／標題收尾 hash＋分級／未支援符號原樣），既有 4 案例未破壞，共 8 案例。
  - **AI 顯示點複查**：ChatBubble（僅 AI 訊息）、AiScreen（分析結果卡＋歷史回看）、AiResultDialog（TextResult＋Insight 摘要／Top3／建議）全數走 MarkdownText，無遺漏；使用者輸入不渲染。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡以 Python 檢查；lint＋單元測試＋雙 APK 由 GitHub Actions 執行。

（修復四個 UI bug：期間選取重疊、時間選擇器偏移、AI Markdown、總覽切換抽動，已完成）**：只動 UI 顯示層，不動資料層、AddTransactionSheet 捲動結構與 CategoryGrid。
  - **Bug 1（明細頁期間選取）**：根因＝DateRangePicker 放在 AlertDialog text{} 內只給 fillMaxWidth()，內部月份 Lazy 列表在無限高度約束下版面擠壓重疊。修復＝`Modifier.fillMaxWidth().heightIn(max = 480.dp)`（TransactionsScreen.kt）。記帳頁 DateFieldButton 檢查過：用的是 DatePickerDialog（Material3 為 DatePicker 設計的容器，內部已處理高度），無同樣問題，未改。
  - **鐵律延伸（請勿誤改）**：任何 Lazy 類 picker（DateRangePicker／DatePicker 等）放進對話框時都必須給有限高度（如 heightIn(max = 480.dp)），否則在無限高度約束下會重疊或閃退——與「單一捲動容器」鐵律同源，編譯與 lint 偵測不到。
  - **Bug 2（時間選擇時鐘偏左上）**：根因＝TimeFieldButton 的 TimePicker 被包在 DatePickerDialog（為 DatePicker 設計的容器）內，版面約束導致時鐘偏左上。修復＝改用 AlertDialog＋`Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center)` 置中（AddTransactionSheet.kt）；確認／取消與 setTime 流程不變，捲動結構與 CategoryGrid 未動。設定頁 NotificationSection 的 TimePicker 本就是 AlertDialog 容器，未改。
  - **Bug 3（AI 回覆 Markdown 未渲染）**：新建 ui/components/MarkdownText.kt——輕量渲染，不新增依賴。解析抽成純 Kotlin 函式 `parseMarkdown(text): List<MdLine>`（MdSegment＝Plain／Bold／Italic／Code 純資料類別；行首「### 」1–3 個 #＝heading、行首「- 」「* 」＝bullet 渲染為「• 」），保守字串掃描、未閉合符號原樣保留、天生不拋例外（**不得改為正則或第三方套件**）。渲染面 MarkdownText composable：**粗體**＝FontWeight.Bold、*斜體*＝Italic、\`行內程式碼\`＝Monospace＋surfaceVariant 底色＋onSurfaceVariant 字色（顏色一律 colorScheme）、heading＝粗體 18.sp。新增 MarkdownParserTest 4 案例（粗體／清單／無符號原樣／未閉合符號）。
  - **AI 文字輸出點清單（本次全數改用 MarkdownText）**：(1) AiAssistantSheet.kt ChatBubble——僅 AI 訊息，使用者訊息維持純 Text；(2) AiScreen.kt AnalysisResultCard（summary／insights／anomalies／tips）與歷史回看 entry.summary；(3) AiResultDialog.kt——TextResult 內文與 Insight 分支的摘要／Top3 分類文字／建議（總覽洞察、明細批次分析、統計、帳戶 AI 入口都經 AiResultDialog，一處改全生效）。使用者輸入（備註、提問框）不渲染。
  - **Bug 4（切回總覽頁抽動）**：三個動畫同時重播的根因與修復——(1) 四頁（總覽／明細／統計／帳戶）listState 由 rememberLazyListState() 改 `rememberSaveable(saver = LazyListState.Saver) { LazyListState() }`，捲動位置跨頁保留；(2) AnimatedAmountText（Common.kt）改以 NaN 哨兵值起始——首次組合（含切回）以當前值 snapTo 起始、只在值真的改變時播 600ms 滾動，不再從 0 重滾，呼叫端簽名不變；(3) AutoHideFabColumn（AutoHideFab.kt）由 animateFloatAsState 改 Animatable——首次進入直接以最終狀態呈現（不播進場動畫），只在顯示狀態變化時播 220ms 隱藏／恢復動畫。
  - **請勿誤改**：(1) 有限高度鐵律見上；(2) MarkdownText 支援範圍僅粗體／斜體／行內程式碼／清單／標題，不支援的符號原樣顯示是刻意行為，勿加 try-catch 包 UI；(3) AutoHideFabColumn 顯示條件仍為 `!canScrollBackward`（勿改 canScrollForward）；(4) AnimatedAmountText 的 NaN 哨兵與 snapTo 起始是防「切回重滾」的核心，勿改回從 0 起始。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡以 Python 檢查；lint＋單元測試（含新 MarkdownParserTest）＋雙 APK 由 GitHub Actions 執行。

（分類管理 UI：新增／隱藏／排序自訂分類，已完成）**：新增分類管理頁（ui/screens/categories/CategoryManagementScreen.kt＋CategoryManagementViewModel.kt，支出／收入兩個 tab，列表＝圖示＋名稱＋色點＋隱藏標記＋上移／下移）；設定頁新增「分類管理」區塊（TextButton「管理分類」→ 新 route category_management，仿 wizard 頁 TopAppBar＋返回）。新增／編輯對話框：名稱（必填、同型別內防重複，新增與改名都檢查）＋圖示選擇（沿用 IconMapper.accountIconKeys＋LazyRow，與帳戶頁同做法）＋色票（沿用 AccountPalette.colorArgbs 的做法）；新增分類 sortOrder＝當前最大＋1（排最後）；排序＝與相鄰分類互換 sortOrder（兩次 updateCategory）。
  - **刪除／隱藏規則（請勿誤改）**：規則集中在 domain/logic/CategoryRules.kt（純邏輯，含 CategoryRulesTest 4 案例單元測試）——「其他」（支出）與「其他收入」（收入）為 fallback 保護分類，**不可隱藏、不可刪除**（保護以種子名稱比對：若使用者對保護分類改名，保護即失效，與記帳分類以名稱對應的行為一致，勿改為以 id 判斷——種子 id 不穩定）；有交易／預算／週期交易關聯的分類一律只能隱藏（isHidden），不得實刪。刪除走 CategoryRepository.deleteCategoryIfUnused（先查三表 COUNT：transactions／budgets／recurring_transactions，無關聯才 deleteById；CategoryDao 新增三個 count 查詢與 deleteById），回傳 false 時 UI 顯示「此分類尚有交易／預算／週期交易關聯，只能隱藏」。**沙箱與 CI 皆無法跑 Room DAO 測試（專案僅 JUnit、無 Robolectric），故關聯判斷邏輯抽成純邏輯 CategoryRules 做單元測試，DAO 查詢本身為單行 COUNT SQL**。
  - **隱藏的反映（請勿誤改）**：AddTransactionViewModel 的分類清單由 observeAll 改為 **observeActive**（記帳 CategoryGrid 不再顯示隱藏分類；AI 建議配對同源，也不會選到隱藏分類。注意：編輯一筆分類已被隱藏的舊交易時，格子裡不會顯示該分類為選中——屬已知行為）；TransactionsViewModel 的 UiState 新增 `filterCategories`（observeActive，明細頁分類篩選 chips 用），**既有 `categories` map 維持 observeAll**（交易列與 AI 篩選名稱對應需要含隱藏分類，否則既有明細會失去分類名——勿合併）；StatsViewModel／OverviewViewModel 維持 observeAll（既有交易的統計與顯示需含隱藏分類）。AddTransactionSheet 捲動結構與 CategoryGrid 本身未動。
  - **DB schema 未動**：僅新增 DAO 查詢；isHidden／sortOrder 為既有欄位。
  - **新字串**：settings_categories、settings_categories_open、categories_title／add／edit／name／name_required／duplicate／hidden／hide／restore／move_up／move_down／delete_confirm／delete_blocked／saved／deleted。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡與 XML 合法性以 Python 檢查；lint＋單元測試＋雙 APK 由 GitHub Actions 執行。

- **Session 21（淺色模式與動態色彩全面檢查＋最小修正，已完成）**：沙箱無法跑實機，以程式碼層級逐頁審查（深色／淺色／動態色彩三組合），只修有對比或可讀問題處；動態色彩（API 31+）經 Theme.kt dynamicDark/LightColorScheme 正確套用，語意色（error／primary／tertiary）皆從 colorScheme 取用，動態模式下自動跟隨。
  - **修正 1：Glass.kt 玻璃卡描邊**（影響所有頁面卡片，含收支小卡、預算卡、帳戶卡、AI 引導卡）——原描邊預設為寫死 `Color.White.copy(alpha = 0.25f)`，淺色模式下幾乎不可見。改為未指定時依深淺色解析：深色＝`onSurface.copy(alpha = 0.25f)`（視覺同原白色描邊、動態色彩下自動跟隨），淺色＝`colorScheme.outlineVariant`（M3 淺色卡片描邊標準色）。`outlineColor` 參數型別改為可空 `Color?`，HmoneyAppRoot 傳入明確值者不受影響。
  - **修正 2：帳戶色票未選取邊框**（AccountsScreen 對話框）——原寫死 `Color.White.copy(alpha = 0.5f)`，淺色對話框底上不可見，色票會看不出可點。改為 `colorScheme.outline`。
  - **修正 3：設定頁預算進度條 80% 警示色**——原寫死 `Color(0xFFFFA726)`（hardcode 違反用色規則）。改為 `colorScheme.tertiary`（M3 警示語意色，深淺色與動態色彩皆自動適配）。門檻 80%/100% 邏輯未動（BudgetEvaluator 勿改警示仍有效）。
  - **修正 4：AI 洞察對話框建議文字對比**（實機回報略顯不足）——原 bodySmall＋`onSurfaceVariant`，改為 bodyMedium＋預設 `onSurface`；時間戳維持 labelSmall＋`onSurfaceVariant`（次要資訊層級）。
  - **檢查過、無問題**：帳戶卡 12% 低透明度疊染（深淺色皆呈淡染色、文字對比不受影響）；CategoryPalette／AccountPalette 色票（Material 400/600 色階用於色塊與圖示而非文字，淺底可辨識，不需雙套切換）；收支小卡（error／primary＋1dp 框，淺色可讀）；預算卡進度條（primary／tertiary／error）；統計空狀態（outline 圖示＋onSurfaceVariant 文字＋Button）；SwipeToRevealRow（動作鈕 secondaryContainer／errorContainer，前景 surface 底色，淺色邊界可辨識）；AutoHideFabColumn（無自訂色）；記帳 Sheet 與各對話框（皆 M3 標準元件，無 hardcode——全域掃描僅上述三處已修）。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡以 Python 檢查；lint＋單元測試＋雙 APK 由 GitHub Actions 執行；深淺色／動態色彩實機驗收待使用者確認。

- **Session 20（AI 本週洞察結構化＋未設定 Key 引導，已完成）**：改 AiRepositoryImpl.kt、AiResultDialog.kt、FeatureAiViewModel.kt、OverviewScreen.kt、CategoryPalette.kt 與字串資源；未新增網路層，AI 呼叫全部沿用既有 AiRepository 與 loading／取消／錯誤分流。
  - **JSON prompt 格式（請勿誤改）**：analyzeWeek 的 system prompt 改為要求三欄位 `{"summary": "一段整體摘要", "topCategories": [{"name", "amount"}]（最多 3 筆、金額由大到小）, "advice": "一條具體建議"}`；解析時 topCategories 相容純字串陣列，存入既有 insights 欄位（「名稱：金額」格式一行一筆）、advice 存入 tips 欄位——DB schema 未動，AI 分析頁歷史列表（逐行顯示）相容。
  - **Fallback 行為（請勿誤改）**：解析失敗或三欄位全空時，parseContent 回傳 null，由既有 execute() fallback 把原始文字整段視為 summary 顯示；一律走既有 try-catch／AiOutcome 錯誤分流，未新增例外路徑。
  - **洞察結果結構化**：AiResultDialog 的 Insight 分支改為「摘要 → Top 3 支出分類（CategoryPalette 色點，未收錄的自訂分類以 colorScheme.primary 顯示）→ 一條建議 → 生成時間（DateUtils 相對時間）」；新增可選參數 onRetry（預設 null），只有總覽頁傳入（重新分析＝再呼 weekInsight，走既有 Loading 狀態），其他入口不受影響。
  - **未設定 Key 引導**：FeatureAiViewModel 新增 `aiConfigured: Flow<Boolean>`（observeConfig map isConfigured，每次收集重新讀取，避免回到畫面時拿到舊值）；OverviewScreen 未設定時以 GlassCard 顯示「設定 AI 之後，可以獲得每月花費分析」＋「前往設定」按鈕（沿用 onGoSettings）。明細／統計／帳戶的 AI 入口未設定時本就經 NotConfigured 錯誤對話框引導（已驗證，未改）。
  - **新字串**：ai_retry「重新分析」、overview_ai_guide「設定 AI 之後，可以獲得每月花費分析」。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡以 Python 檢查；lint＋單元測試＋雙 APK 由 GitHub Actions 執行。

- **Session 19（帳戶頁卡片視覺升級＋顏色與圖示自訂，已完成）**：AccountsScreen 帳戶卡片以帳戶色主題化——卡片內 Row 最左側加 4dp 圓角色條（填 Color(account.colorArgb)），卡片外層以 Box 疊上帳戶色 12% 透明度圓角染色（RoundedCornerShape(24.dp)，不攔截點擊，點擊仍由 GlassCard 處理）；餘額字級 titleMedium → titleLarge，帳戶名 titleMedium → bodyMedium（次要層級），貨幣標籤不變。新增／編輯帳戶對話框色票由寫死在 AccountsScreen 的 6 色改為 ui/theme/AccountPalette.kt 共用常數檔的 10 色（Material 400/600 色階，深淺色皆可讀）；DB schema 未動（colorArgb/iconKey 為既有欄位）。IconMapper.accountIconKeys 新增「smartphone」（手機支付）與「trending_up」（股票）兩個 iconKey，帳戶可挑選圖示由 5 個增至 7 個。明細／總覽頁顯示帳戶圖示與顏色處經 Color(account.colorArgb) 自動跟隨，未另外修改。
  - **驗證方式**：沙箱無 JDK/Android SDK，括號平衡以 Python 檢查；lint＋單元測試＋雙 APK 由 GitHub Actions 執行。

- **Session 18（明細滑動 bug 修復＋滾動自動隱藏 FAB＋版本 1.1.0，已完成）**：新增 ui/components/SwipeToRevealRow.kt 與 ui/components/AutoHideFab.kt；修改 TransactionsScreen、OverviewScreen、StatsScreen、AccountsScreen 與 app/build.gradle.kts（versionName 1.0.0 → 1.1.0，versionCode 維持 GITHUB_RUN_NUMBER 不動）。AddTransactionSheet 捲動結構與 CategoryGrid 未動。
  - **Bug 1 根因與修復**：舊做法 SwipeToDismissBox 未設攔截時，滑過門檻會停在 Dismissed 錨點（＝整列寬），前景整列滑出畫面消失；且前景 Row 內殘留 Session 6 前的常駐編輯／刪除按鈕與背景重疊。修復：移除前景常駐按鈕，捨棄 SwipeToDismissBox，改自製 `SwipeToRevealRow`（ui/components/，可重用）。
  - **SwipeToRevealRow 錨點設計（請勿誤改）**：`AnchoredDraggableState<RevealValue>`（foundation 1.9 新簽名：建構只吃 initialValue，錨點用 `updateAnchors` 於 SideEffect 注入，動畫用 `animateTo`——勿用舊的 positionalThreshold／velocityThreshold 建構參數，該組態已移到 Modifier 且舊欄位為 deprecated lateinit）。兩錨點：Settled = 0f、Revealed = -160dp（兩顆 48dp 動作鈕＋12dp 間距＋左右 16dp 邊距）；水平手勢（Orientation.Horizontal）與垂直 LazyColumn 捲動不衝突。滑到 Revealed 停住不消失；點內容區或列表空白處回彈；動作需明確點按鈕，快慢滑皆不誤觸。前景內容加 `colorScheme.surface` 底色（列原本透明，否則動作鈕會從底下透出）。
  - **單一展開狀態**：TransactionsScreen 持有 `revealedId: Long?`，每列 `revealed = revealedId == tx.id`；滑開第二筆時前一筆經 `animateTo(Settled)` 自動回彈。編輯／刪除仍走既有 editingId／deletingId（含刪除確認對話框），點按鈕先收回再開。
  - **Bug 2 修復：AutoHideFabColumn（ui/components/）**：顯示條件為 `!listState.canScrollBackward`（還能向上捲回＝已離開頂端）。**請勿誤改**：勿改成 canScrollForward——內容比畫面長時，停在頂端 canScrollForward 也為 true，FAB 會在頂端就消失。隱藏動畫為「位移 200dp 滑出＋淡出」（tween 220ms，graphicsLayer），位移出畫面同時確保隱藏時不可誤觸。套用四頁：總覽（AI＋快速記帳 FAB）、明細（AI FAB）、帳戶（Scaffold floatingActionButton 內 AI＋新增）、統計（AI FAB）。
  - **內距**：四頁 LazyColumn 補 `contentPadding = PaddingValues(bottom = 160.dp)`，最後一筆金額滾到底可完整看清；明細列表另加無 ripple 的空白處點擊收回展開列。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），括號平衡已以 Python 檢查；lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。

- **Session 17（明細頁交易項目滑動操作：編輯／刪除，已完成）**：只改 TransactionsScreen.kt；AddTransactionSheet、刪除確認對話框、ViewModel、資料層完全未動。
  - **改了什麼**：LazyColumn 內每筆交易項目（僅交易列，不含日期分組標題）包進 Material3 `SwipeToDismissBox`，左滑露出「編輯」「刪除」兩個圓形按鈕（編輯用 secondaryContainer、刪除用 errorContainer，皆取自 colorScheme）；點按鈕先 `snapTo(Settled)` 收回再沿用既有流程（編輯＝editingId 開 AddTransactionSheet、刪除＝deletingId 開既有確認對話框）。
  - **方向決策（請勿誤改）**：採 **EndToStart（手指向左滑）**——需求第 1 點「左滑露出動作」與第 3 點「右滑不觸發」兩者一致指向左滑；原文括號內的「StartToEnd」與第 3 點矛盾，以行為描述為準。`enableStartToEnd = false`、`enableEndToStart = true`，右滑完全不起作用、未達門檻放開自動回彈。
  - **互動設計**：滑過門檻後項目停住（SwipeToDismissBox 特性：內容滑出、背景動作區取代整列）；動作區空白處可點擊收回（因內容已完全滑出、無法再對內容滑動收回，此為必要逃生口）；動作需明確點按鈕才觸發，快滑慢滑都不會誤觸。
  - **既有行為保留**：列尾原本常駐的編輯／刪除按鈕未移除（需求未要求移除，維持不變更原則）；TransactionRow 本身無點擊行為，分組標題不包滑動元件，無手勢衝突。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），括號平衡已以 Python 檢查；lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。

- **Session 16（記帳頁常用備註快捷選填＋連記模式，已完成）**：只改 AddTransactionSheet.kt、AddTransactionViewModel.kt 與字串資源；捲動結構（外層不捲動＋中段 weight(1f) 可捲動＋鍵盤固定底部）與 CategoryGrid 完全未動，無 DB schema 變更。
  - **常用備註 chips**：位置在備註欄下方、AI 載入列之後、標籤欄之前，位於既有捲動區內。新 private composable `RecentNotesChips`——FlowRow 自動換行、本身不捲動（垂直軸向仍只有中段一個捲動容器），AssistChip 點擊直接帶入備註欄（viewModel.setNote）；無資料時整列不顯示（不會有空列）。
  - **chips 資料來源決策**：擇 ViewModel 記憶體去重——AddTransactionViewModel 新增 `recentNotes: StateFlow<List<String>>`，由既有 `transactionRepository.observeTransactions()` map→去空→distinct→take(5)（DAO 本身 ORDER BY dateEpochMillis DESC，新到舊）。不新增 DAO 查詢、不動 Room schema，也無需補 DAO 測試；chips 更新隨 repository flow 即時反映（延遲一拍可接受）。
  - **連記模式決策**：擇「完成 ✓」鈕旁小 toggle（FilterChip「連記」），而非 Snackbar action——儲存成功後 Sheet 會關閉，Snackbar action 需處理重新開啟的時序；toggle 於 Sheet 開啟期間直接續記較單純可靠。開關開啟時送出成功不關 Sheet：ViewModel.save() 新增可選參數 `onContinue`，成功時改呼叫新方法 `resetForNext()`——保留帳戶、分類與類型，清空金額、手續費、備註、標籤，日期時間重設為現在，並以 Sheet 內 Snackbar 顯示「已儲存，再記一筆」；關閉時沿用原流程（父層「已儲存」＋關閉 Sheet）。既有 23 個單元測試未覆蓋此 ViewModel，save() 以預設參數擴充不影響既有呼叫。
  - **字串資源**：新增 add_recent_notes「常用備註」、add_continue_mode「連記」、add_continue_saved「已儲存，再記一筆」。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），括號平衡與 XML 合法性已以 Python 檢查；lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。

- **Session 15（全 App 過場動畫與操作回饋補強，已完成）**：只碰 UI 回饋層，AddTransactionSheet 捲動結構與資料層完全未動。
  - **分頁切換轠場**：NavHost 進齣轠場由 spring 滑動＋fade 改為純淡入淡出（fadeIn 220ms／fadeOut 180ms tween，前進與返回皆同）——五分頁切換一致、不閃白；containerColor 為 background，fade 過程不露白。附屬頁面（精靈、AI）同套轠場。
  - **Toast 全面改 Snackbar**：(1) 記帳儲存成功——AddTransactionSheet 新增可選參數 snackbarHostState（父層傳入），onSaved 經父層 SnackbarHost 顯示「已儲存」後才關閉 Sheet；Overview／Transactions 兩個入口各加 SnackbarHost（BottomCenter、bottom 88dp 避開 FAB）；Sheet 內部回饋（驗證失敗、AI 建議）仍用 Sheet 自己的 localSnackbarHostState，兩者分離避免遮蔽。(2) 明細頁 AI 篩選套用——Toast 改 Snackbar（字串組合期解析）。(3) 明細頁刪除成功——新增「已刪除這筆記錄」Snackbar。
  - **帳戶頁回饋補齊**：新增／編輯儲存成功、刪除成功各加 Snackbar（失敗的「僅能隱藏」提示原本就有），沿用既有 Scaffold snackbarHost。
  - **設定頁預算儲存回饋**：SettingsScreen 根層包 Box＋SnackbarHost（捲動結構不變，單一 verticalScroll 仍在 Column），月總預算／分類預算儲存後顯示「預算已儲存」。
  - **數字滾動補齊**：AnimatedAmountText 加可選 fontWeight 參數（沿用既有元件）；總覽頁本月收支兩張小卡金額、預算卡「剩餘」與「日均可用」金額改用 AnimatedAmountText（原本只有總餘額有滾動）。
  - **AI loading 檢查**：AiResultDialog（四分頁入口）、AiAssistantSheet、AiScreen 皆有 loading 狀態，無缺漏，未改。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。

- **Session 14（總覽頁「本月預算進度」卡片，已完成）**：對應 SPEC 3.2（總覽）與預算功能串接，僅改 OverviewViewModel.kt、OverviewScreen.kt 與字串資源；設定頁預算設定功能與 BudgetEvaluator 完全未動，無 DB schema 變更。
  - **改了什麼**：本月收支卡下方新增預算卡——LinearProgressIndicator（本月支出 ÷ 月總預算）＋「已花 X / 預算 Y，剩餘 Z」＋「日均可用 W」；未設定預算（總預算為 0）時顯示「尚未設定預算」＋「去設定」按鈕（沿用 OverviewScreen 既有 onGoSettings 導航參數，直接導向設定頁）。
  - **日均可用**：剩餘金額（爆表則以 0 計）÷ 當月剩餘天數；剩餘天數＝lengthOfMonth() − dayOfMonth + 1（今天也算一天可用，避免月末除以零）。日期一律 LocalDate＋ZoneId.systemDefault()，沿用專案既有寫法，無 API 34 限定 API。
  - **狀態色決策**：ratio < 60% 綠（colorScheme.primary）、60–90% 黃（colorScheme.tertiary）、> 90% 或爆表紅（colorScheme.error），皆從主題語意色取用，無 hardcode。爆表時剩餘金額以負數呈現（Formats.money 支援負數）。
  - **重要決策（請勿誤改）**：未重用 BudgetEvaluator.Level——其閾值為 80%/100%（設定頁規格），本次總覽頁規格為 60%/90%，兩處規格不同；為不影響設定頁行為，總覽頁閾值判斷直接寫在 OverviewViewModel/OverviewScreen，BudgetEvaluator 保持原樣。若日後兩處閾值要統一，需先確認設定頁規格再動。
  - **資料管線**：OverviewViewModel 注入 BudgetRepository.observeAll()（combine 第 5 個 flow），取 type == TOTAL 的金額；無新 DB 欄位、無 schema 變更。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。跨月驗證待實機改系統日期。

- **Session 13（統計頁空狀態與圓餅圖視覺優化，已完成）**：對應 SPEC 3.3（統計），僅改 StatsScreen.kt、DonutChart（Charts.kt，僅統計頁使用）、字串資源，不動時間選擇器與帳戶篩選邏輯。
  - **空狀態**：新增 StatsChartEmptyState（StatsScreen.kt private composable）取代原本一行 EmptyState——置中大圖示（PieChart 72dp）＋主標題＋引導文字＋「去記一筆」按鈕；沿用 OverviewScreen 的開啟方式（showAddSheet state + AddTransactionSheet(onDismiss)），未動 AddTransactionSheet 內部結構。
  - **分類固定色盤**：新增 ui/theme/CategoryPalette.kt（單一共用常數檔），以分類名稱對應固定色（餐飲紅、交通靛藍、購物橙、娛樂紫、居家青、醫療粉、其他藍灰），取 Material 400 色階、深淺色模式皆可讀；色值與資料庫種子分類色一致，未收錄的自訂分類 fallback 沿用 DB colorArgb。DonutChart 與圖例色塊統一走 CategoryPalette.colorOf。
  - **圖例列**：色塊＋分類名＋金額＋百分比，可點擊；圓餅分區也可點擊（DonutChart 新增 onSliceClick，pointerInput + detectTapGestures 以點擊角度換算分區，含 12dp 容差）。點擊後以 Snackbar 顯示「分類：金額（占 x%）」，SnackBarHost 置於 Box 底部中央（bottom 88dp 避開 FAB）。字串於組合期先解析（stats_slice_detail 格式化字串），不在點擊事件內取字串——沿用 Session 10 的 lint 決策。
  - **用色原則**：分類色盤為共用常數檔（SPEC 允許），UI 其餘用色仍全部取自 MaterialTheme.colorScheme，無 hardcode 散落。
  - **驗證方式**：沙箱無 JDK/Android SDK（與先前 session 相同），lint＋單元測試＋雙 APK 簽署由 GitHub Actions 執行，Release 頁可下載。

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
