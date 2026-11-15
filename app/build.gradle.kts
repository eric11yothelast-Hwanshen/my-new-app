// App 模組建置設定（版本號與 docs/research.md 固定表一致）
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Room schema 匯出位置（供未來遷移比對）
room {
    schemaDirectory("$projectDir/schemas")
}

// ===== Release 簽章（兩層讀取）=====
// 順序：CI Secrets（環境變數）→ 都沒有則 fallback debug 簽章；金鑰檔案不入庫
// 任何情況下建置不得直接失敗（依 SPEC 第八節）
val keystoreFile: File? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { File(it) }
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val keyAlias: String? = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val keyPassword: String? = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasReleaseSigning = keystoreFile?.exists() == true &&
    !keystorePassword.isNullOrBlank() &&
    !keyAlias.isNullOrBlank() &&
    !keyPassword.isNullOrBlank()


android {
    namespace = "com.hmoney.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hmoney.app"
        minSdk = 26
        targetSdk = 36
        // CI 以 GITHUB_RUN_NUMBER 覆寫遞增，確保可覆蓋安裝升級
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.3.0"
    }

    // 以不衝突的區域變數承接（避免 lambda 內同名屬性自我賦值）
    val ksFile = keystoreFile
    val ksStorePassword = keystorePassword
    val ksAlias = keyAlias
    val ksKeyPassword = keyPassword

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = ksFile
                storePassword = ksStorePassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 簽章金鑰長期一致；無金鑰時 fallback debug 簽章，建置不中斷
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX 基礎
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose（BOM 統一版本）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // 導航
    implementation(libs.androidx.navigation.compose)

    // Hilt 依賴注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room 資料庫（KSP 處理器）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore（外觀等設定）
    implementation(libs.androidx.datastore.preferences)

    // WorkManager 週期排程（備份、預算、週報、週期交易）
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Haze 玻璃態視觉（API 33+ 完整效果，低版本自動降級）
    implementation(libs.haze.core)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)

    // AI API 串接（OpenAI 相容）：Retrofit + OkHttp；金鑰以 EncryptedSharedPreferences 保存
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)

    // 條碼掃描（S40）：ML Kit bundled 版（離線可用，不上架 Play 故不選 unbundled）
    implementation(libs.mlkit.barcode.scanning)
    // CameraX 三件（S40 僅鋪依賴，preview 留 S41）
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // 開發期工具
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 單元測試
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json 參考實作（JVM 測試用；App 執行期用平台內建，零新執行期依賴）
    testImplementation(libs.org.json)
}
