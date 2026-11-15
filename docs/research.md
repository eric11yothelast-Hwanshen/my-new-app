# 幻帳（Hmoney）研究結論 — docs/research.md

> 研究日期：2026-11。本文件為規格第一節「研究階段」的產出，所有版本號經連網查證後固定，之後所有 Gradle 設定必須與本文件一致。

## 一、版本固定表（2026 年最新穩定版）

| 項目 | 固定版本 | 查證來源 |
| --- | --- | --- |
| Android Gradle Plugin (AGP) | **9.5.0**（2026 年 10 月發布，最新穩定） | developer.android.com/build/releases/gradle-plugin |
| Gradle | **9.6.0**（AGP 9.5 最低需求＝預設版本） | 同上相容性表 |
| JDK | **17**（AGP 9.5 預設） | 同上相容性表 |
| SDK Build Tools | 36.0.0（AGP 預設，無需顯式指定） | 同上 |
| Kotlin | AGP 9 內建 Kotlin（built-in Kotlin，不需宣告 org.jetbrains.kotlin.android） | AGP 9.0 release notes |
| Compose 編譯器插件 | org.jetbrains.kotlin.plugin.compose **2.3.21**（與內建 Kotlin 2.3.x 對齊） | Kotlin/AGP9 遷移指南 |
| KSP | **2.3.12**（KSP 2.3.1+ 才支援 AGP 9 內建 Kotlin；2.3.6+ 官方建議） | developer.android.com AGP 9 migration skill、google/ksp releases |
| Compose BOM | **2026.09.00**（對應 material3 1.4.0、compose 1.12.1） | developer.android.com/develop/ui/compose/bom |
| Material3 (compose) | 1.4.0（經 BOM 帶入） | AndroidX versions 表 |
| Hilt | **2.60.1**（Hilt 2.59+ 才支援 AGP 9；2.60.1 修正 2.60 回歸） | github.com/google/dagger/releases |
| androidx.hilt（hilt-navigation-compose 等） | **1.4.0** | AndroidX versions 表 |
| androidx.core:core-ktx | **1.19.1** | AndroidX versions 表 |
| androidx.activity:activity-compose | **1.13.0** | AndroidX versions 表 |
| androidx.lifecycle | **2.11.0** | AndroidX versions 表 |
| androidx.navigation:navigation-compose | **2.10.2** | AndroidX versions 表 |
| androidx.datastore | **1.3.0**（Session 2 起採用） | AndroidX versions 表 |
| androidx.room | **2.8.5**（Session 2 起採用，KSP 處理器） | AndroidX versions 表／社群升級紀錄 |
| androidx.work:work-runtime-ktx | **2.11.1**（Session 5 起採用） | AndroidX WorkManager releases |
| androidx.security:security-crypto | **1.1.0**（EncryptedSharedPreferences；見第七節取捨） | AndroidX Security releases |
| Retrofit | **3.0.0**（Session 6 起採用） | github.com/square/retrofit |
| OkHttp | **5.1.0**（BOM） | square/okhttp releases |
| Haze（玻璃態） | **2.1.0**（Glass 效果自 2.1 起穩定） | chrisbanes.me/posts/haze-2.1.0 |
| compileSdk | **37**（2026 最新 AndroidX 稳定版要求 compileSdk 37） |
| targetSdk | **36**（Android 16） | Google Play 政策（見第二節） |
| minSdk | **26**（依規格） | 規格書 |

### 重要工具鏈注意事項（AGP 9 遷移）

- AGP 9 內建 Kotlin：不得再套用 `org.jetbrains.kotlin.android`；`kapt` 不相容，一律用 KSP（Room / Hilt 皆走 KSP）。
- Hilt Gradle plugin 2.59+ 要求 AGP 9；本專案 AGP 9.5 + Hilt 2.60.1 相容。
- KSP 在 AGP 9 內建 Kotlin 下，Room 實體若同時標記 `@Parcelize` 會觸發 KSP [MissingType]（google/ksp#3053）——本專案 Room 實體一律不加 `@Parcelize`。
- Java source/target 預設升為 11；本專案統一設 17。

## 二、targetSdk 規範（2026）

- Google Play 自 **2026-08-31** 起要求新 App 與更新必須 target **Android 16（API 36）** 以上（Wear/Auto 等另有規定）。
- 本專案固定 `compileSdk = 37`（最新 AndroidX 穩定版要求）、`targetSdk = 36`，符合現行規範。
- API 36 起不可退出 edge-to-edge（`windowOptOutEdgeToEdgeEnforcement` 已停用）——UI 實作必須直接以 edge-to-edge 為前提設計。

## 三、UI 設計趨勢（2026）

- 2026 主流＝**Material 3 Expressive**（spring 物理動畫、大圓角、動態色彩）＋玻璃態（Liquid Glass / glassmorphism）視覺；Material 官方已 Compose-first（m3.material.io/blog/material-is-compose-first）。
- Material3 1.4.0（Compose BOM 2026.09.00）已是現行 stable，含 Expressive 動效 API。

## 四、玻璃態實作方案評估

| 方案 | 評估 |
| --- | --- |
| **chrisbanes/haze 2.1**（採用） | Compose 原生、跨平台、模組化（haze / haze-blur / haze-glass / 材質包）；Glass 效果 2.1 起穩定；提供 Material3 現成 Style（HazeMaterials / glass-material3）；有 fallback 渲染（不支援時以不透明底色隱藏細節），符合「低版本不崩潰」要求 |
| Mortd3kay/liquid-glass-android | AGSL shader，限 Android 13+（API 33+），低版本需自行降級；維護單一、生態較小 |
| skydoves/Cloudy | 僅自身內容模糊（blur own content），不支援「背景內容模糊」的 glassmorphism 主需求 |

**結論**：採用 **Haze 2.1.0**（`haze` + `haze-blur` + `haze-blur-materials`，必要時 `haze-glass-material3`）。
**降級策略**：Haze 官方 fallback 渲染會在無法模糊時改用半透明壓色（淺色白底／深色黑底）；再於程式以 `Build.VERSION.SDK_INT >= 33` 分支，API 33+ 全效果、以下僅半透明壓色＋高光描邊，不呼叫進階 API，保證 API 26+ 不崩潰。

## 五、防後台終止（dontkillmyapp.com 研究摘要）

- **Samsung**：關閉「Put unused apps to sleep／Deep sleep」、將 App 從休眠清單移除；設定頁 `Settings > Battery > Background usage limits`；勿依賴「最近任務上鎖」。
- **Xiaomi（MIUI/HyperOS）**：需開啟 App「Autostart」、電池省電策略設「No restrictions」（無限制）、開啟通知；常見 Intent：`com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` 等。
- **OPPO/OnePlus（ColorOS）**：電池最佳化允許背景執行、鎖定最近任務、開啟自啟動。
- **vivo（Funtouch/OriginOS）**：背景高耗電白名單、自啟動、鎖定最近任務。
- **Huawei/Honor（EMUI/MagicOS）**：受保護的後台應用（Protected apps）開啟、啟動管理允許自啟動與關聯啟動。
- **Google/Pixel**：移除電池最佳化（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` / `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）即可。
- 所有廠牌 Intent 以 try-catch 包裹、失敗 fallback 至 dontkillmyapp.com 對應廠牌說明頁（Session 5 實作完整清單）。

## 六、通知與排程 API 現況

- **POST_NOTIFICATIONS**：API 33+ 為 runtime 權限，須動態請求（`ActivityCompat.requestPermissions` / Compose `rememberLauncherForActivityResult`）；被拒絕需提供引導畫面（`ACTION_APP_NOTIFICATION_SETTINGS`）。
- **SCHEDULE_EXACT_ALARM**：API 31+ 宣告；targetSdk 33+ 預設「拒絕」，需以 `AlarmManager.canScheduleExactAlarms()` 檢查並引導 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`。**USE_EXACT_ALARM** 僅限鬧鐘/日曆類 App，記帳提醒不適用，不宣告。
- 精確鬧鐘被系統收回時的 fallback：改用 WorkManager 彈性排程（`OneTimeWorkRequest` + `setInitialDelay`／`PeriodicWorkRequest`）。
- **RECEIVE_BOOT_COMPLETED**：重開機後重新註冊 WorkManager（有自身持久化）與精確鬧鐘（BroadcastReceiver 重排）。
- PendingIntent 一律 `FLAG_IMMUTABLE`（規格禁止舊寫法）。

## 七、其他技術取捨

- **資料庫加密（SQLCipher）**：評估結論——SQLCipher 需綁定 Room + native lib，增加 APK 體積與升級風險，且本 App 資料為個人記帳紀錄，已有系統級 app 私有空間保護；暫不啟用，保留為未來選配。若使用者要求，可於後續 session 加入。
- **EncryptedSharedPreferences**：androidx.security:security-crypto 1.1.0 已標記 API 為 deprecated（官方建議改用平台 Keystore），但功能完整可用且規格指定；Session 6 採用時以最小封裝隔離，未來可無痛替換。
- **AI JSON 解析**：使用平台內建 `org.json`，避免 kotlinx-serialization 插件版本須與 AGP 內建 Kotlin 綁定的風險。
- **沙箱限制**：開發沙箱無 JDK／Android SDK 且無下載網路，無法在本機編譯 APK；建置驗證由 Session 8 的 GitHub Actions 完成。Gradle Wrapper 四件套已從 Gradle 官方倉庫（tag v9.6.0）取得並提交。
