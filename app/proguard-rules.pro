# ── ONNX Runtime JNI ──
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { native <methods>; }

# ── SQLCipher ──
-keep class net.sqlcipher.** { *; }
-keepclassmembers class net.sqlcipher.** { native <methods>; }

# ── Room ──
-keep class com.tianshang.guard.data.local.database.** { *; }

# ── Koin ──
-keep class org.koin.** { *; }

# ── Retrofit / OkHttp ──
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# ── Remove debug logs in release ──
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ── Compose ──
-dontwarn androidx.compose.**

# ── Keep Koin module definitions ──
-keep class com.tianshang.guard.di.** { *; }
