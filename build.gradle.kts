// 根建置檔：只宣告插件版本，不套用（AGP 9 內建 Kotlin，無 kotlin-android 插件）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
