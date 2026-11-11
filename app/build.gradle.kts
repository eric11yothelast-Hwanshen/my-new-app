// App 模組建置設定（版本號與 docs/research.md 固定表一致）
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

android {
    namespace = "com.hmoney.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hmoney.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    // AI API 串接（OpenAI 相容）：Retrofit + OkHttp；金鑰以 EncryptedSharedPreferences 保存
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)

    // Haze 玻璃態視觉（API 33+ 完整效果，低版本自動降級）
    implementation(libs.haze.core)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)

    // 開發期工具
    debugImplementation(libs.androidx.compose.ui.tooling)
}
