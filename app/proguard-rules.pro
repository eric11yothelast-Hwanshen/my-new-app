# R8 混淆規則（Compose、Hilt、Room、Retrofit、OkHttp 的規則已隨套件附帶）

# Tink（EncryptedSharedPreferences 依賴）引用的 errorprone 註解不在 classpath：安全忽略
-dontwarn com.google.errorprone.annotations.**

# javax 註解（部分來源套件僅編譯期引用）
-dontwarn javax.annotation.**
