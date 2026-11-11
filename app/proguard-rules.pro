# R8 混淆規則（Compose、Hilt、Room、Retrofit、OkHttp 的規則已隨套件附帶）

# Tink（EncryptedSharedPreferences 依賴）引用的 errorprone 註解不在 classpath：安全忽略
-dontwarn com.google.errorprone.annotations.**

# javax 註解（部分來源套件僅編譯期引用）
-dontwarn javax.annotation.**

# AI 介面（Retrofit 動態代理）：保留介面方法與註解，避免 release 混淆後閃退
-keep interface com.hmoney.app.data.ai.AiApi { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
