# 幻帳（Hmoney）開發進度清單

> 依 `docs/SPEC.md` 第十一節的 11 個階段追蹤。每個 session 只執行對應階段，完成後勾選。

| # | 階段 | 狀態 | 完成日期 | 備註 |
| --- | --- | --- | --- | --- |
| 1 | 研究階段 → docs/research.md | ✅ 已完成 | 2026-11-11 | 固定 AGP 9.5.0 / Gradle 9.6.0 / Compose BOM 2026.09.00 / targetSdk 36；玻璃態選定 Haze 2.1.0 |
| 2 | 專案骨架（Gradle、manifest、Hilt、導覽、主題系統） | ✅ 已完成 | 2026-11-11 | Wrapper 四件套齊全；五頁底部導覽＋動態色彩主題；權限暫無（後續階段依功能加入） |
| 3 | 資料層（Room 實體與 DAO、Repository） | ⬜ 未完成 | | |
| 4 | 核心功能（記帳 CRUD、多帳戶與轉帳） | ⬜ 未完成 | | |
| 5 | UI 全面實作（含玻璃態與動效） | ⬜ 未完成 | | |
| 6 | 統計、預算、週期交易 | ⬜ 未完成 | | |
| 7 | 通知與防後台終止（含設定精靈） | ⬜ 未完成 | | |
| 8 | AI 分析串接 | ⬜ 未完成 | | |
| 9 | 單元測試與修 bug | ⬜ 未完成 | | |
| 10 | CI/CD、簽章、首次 Release、README | ⬜ 未完成 | | |
| 11 | 推送至 main，確認 Actions 綠燈與 Release 頁面可下載 APK | ⬜ 未完成 | | |

## Session 紀錄

- **Session 1**（已完成）：執行第 1–2 項。提交 docs/SPEC.md、docs/PROGRESS.md、docs/research.md，並完成專案骨架（Gradle 9.6 Wrapper、AGP 9.5、Hilt、Navigation Compose 五頁導覽、Material 3 動態色彩主題、自適應圖示）。遺留事項：沙箱無 JDK/Android SDK，無法本機編譯 APK；建置驗證由 Session 8 的 GitHub Actions 執行。
