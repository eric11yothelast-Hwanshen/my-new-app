# 幻帳（Hmoney）開發進度清單

> 依 `docs/SPEC.md` 第十一節的 11 個階段追蹤。每個 session 只執行對應階段，完成後勾選。

| # | 階段 | 狀態 | 完成日期 | 備註 |
| --- | --- | --- | --- | --- |
| 1 | 研究階段 → docs/research.md | ✅ 已完成 | 2026-11-11 | 固定 AGP 9.5.0 / Gradle 9.6.0 / Compose BOM 2026.09.00 / targetSdk 36；玻璃態選定 Haze 2.1.0 |
| 2 | 專案骨架（Gradle、manifest、Hilt、導覽、主題系統） | ✅ 已完成 | 2026-11-11 | Wrapper 四件套齊全；五頁底部導覽＋動態色彩主題；權限暫無（後續階段依功能加入） |
| 3 | 資料層（Room 實體與 DAO、Repository） | ✅ 已完成 | 2026-11-11 | Room 2.8.5（KSP）＋WAL 模式；預設帳戶（現金、1號銀行、2號銀行）與内建分類種子；DAO 支援關鍵字搜尋與期間／帳戶／分類／類型篩選 |
| 4 | 核心功能（記帳 CRUD、多帳戶與轉帳） | ✅ 已完成 | 2026-11-11 | 金額以「分」為單位（不可為負）；餘額 = 期初＋收入−支出±轉帳，手續費計為轉出帳戶支出；有交易的帳戶僅能隱藏不得實刪；轉帳驗證兩帳戶不可相同。UI 畫面於 Session 3 接上 |
| 5 | UI 全面實作（含玻璃態與動效） | ✅ 已完成 | 2026-11-11 | Haze 2.1.0 玻璃態（API 33+ 模糊、低版半透明壓色）；流體切換開關；數字滾動、spring 轉場；總覽／明細／帳戶／設定＋記帳 Bottom Sheet（數字鍵盤）；深淺模式與動態色彩設定 |
| 6 | 統計、預算、週期交易 | ⬜ 未完成 | | |
| 7 | 通知與防後台終止（含設定精靈） | ⬜ 未完成 | | |
| 8 | AI 分析串接 | ⬜ 未完成 | | |
| 9 | 單元測試與修 bug | ⬜ 未完成 | | |
| 10 | CI/CD、簽章、首次 Release、README | ⬜ 未完成 | | |
| 11 | 推送至 main，確認 Actions 綠燈與 Release 頁面可下載 APK | ⬜ 未完成 | | |

## Session 紀錄

- **Session 3**（已完成）：執行第 5 項。玻璃態（Haze）底部導覽列與卡片、FluidSwitch 流體開關、數字滾動動畫、spring 轉場、空狀態；總覽頁（本月收支玻璃卡／總餘額／帳戶餘額列／近期明細／快速記帳 FAB）、明細頁（搜尋＋四種篩選＋按日分組小計＋編輯／刪除確認）、帳戶頁（新增／編輯／隱藏／刪除規則提示）、設定頁外觀（深色模式／動態色彩，DataStore）。CI 綠燈。遺留事項：統計頁仍為骨架（Session 4）；設定頁其餘區塊待後續階段。
- **Session 2**（已完成）：執行第 3–4 項＋額外的最小 CI 驗證工作流（.github/workflows/build.yml：JDK 17 + assembleDebug）。建立 Room 資料層（實體／DAO／資料庫／種子資料）、domain 模型、餘額計算邏輯、三個 Repository（介面＋實作）與 Hilt DI 模組。遺留事項：沙箱無 JDK/Android SDK 仍無法本機編譯，可編譯性由 push 後的 Actions 驗證；UI 接線與畫面於 Session 3。
- **Session 1**（已完成）：執行第 1–2 項。提交 docs/SPEC.md、docs/PROGRESS.md、docs/research.md，並完成專案骨架（Gradle 9.6 Wrapper、AGP 9.5、Hilt、Navigation Compose 五頁導覽、Material 3 動態色彩主題、自適應圖示）。遺留事項：沙箱無 JDK/Android SDK，無法本機編譯 APK；建置驗證由 Session 8 的 GitHub Actions 執行。
