# TianshangGuard release ProGuard rules (audit H-06).
# Library modules ship their own consumer rules; these cover app-specific
# reflection / native-bound / serialization boundaries.

# ── ONNX Runtime (native inference) ──────────────────────────────
-keep class com.microsoft.onnxruntime.** { *; }
-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }
-dontwarn com.microsoft.onnxruntime.**
-dontwarn ai.onnxruntime.**

# ── SQLCipher (encrypted database) ──────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
# SQLCipher reads the native lib name reflectively
-keep class net.sqlcipher.database.SQLiteDatabase { *; }

# ── BouncyCastle (Ed25519 rule signing) ─────────────────────────
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider
-dontwarn org.bouncycastle.**

# ── Room (entities + DAOs) ──────────────────────────────────────
-keep class com.tianshang.guard.data.local.database.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomOpenHelper
-dontwarn androidx.room.**

# ── Gson / Retrofit network models ──────────────────────────────
-keep class com.tianshang.guard.data.remote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Koin dependency injection ───────────────────────────────────
-keep class com.tianshang.guard.di.** { *; }
-keepnames class com.tianshang.guard.** { *; }

# ── ViewModels ──────────────────────────────────────────────────
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class com.tianshang.guard.ui.** { *; }

# ── Serialization support ───────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable { *; }
-keepclassmembers class * implements android.os.Parcelable { *; }

# ── CameraX / ZXing ship their own consumer rules ──────────────
-dontwarn androidx.camera.**
-dontwarn com.google.zxing.**

# ── Compose ─────────────────────────────────────────────────────
-dontwarn androidx.compose.**
