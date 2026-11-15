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

# ML Kit bundled 條碼掃描器（S42）：套件未附 consumer keep 規則，R8 混淆後 release 掃碼失效——keep 規則勿刪
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-dontwarn com.google.mlkit.**
