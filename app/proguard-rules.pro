# Proguard rules for Bilingual Dictionary App
-keepattributes SourceFile,LineNumberTable,EnclosingMethod,InnerClasses,*Annotation*

# ViewBinding
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
    public * getRoot();
}
-keep class com.bilingual.dictionary.databinding.** { *; }

# Keep Application & Activities & Custom Views
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.app.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View
-keep class com.bilingual.dictionary.ocr.** { *; }

# Material 3 & AndroidX
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep class androidx.** { *; }
-dontwarn androidx.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Google ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Data models
-keep class com.bilingual.dictionary.data.model.** { *; }
-keepclassmembers class com.bilingual.dictionary.data.model.** { *; }

# SQLite & Repositories
-keep class com.bilingual.dictionary.data.db.** { *; }
-keep class com.bilingual.dictionary.core.** { *; }
-keep class com.bilingual.dictionary.data.repository.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
